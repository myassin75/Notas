package haddad.three.notas.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.EntryPointAccessors
import haddad.three.notas.data.AppMediaStorage
import haddad.three.notas.data.DefaultNotePreferencesState
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAction
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteOptions
import haddad.three.notas.data.NoteReminder
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.RecurrenceRule
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.getActiveReminders
import haddad.three.notas.data.pinned
import haddad.three.notas.di.SettingsDependenciesEntryPoint
import haddad.three.notas.ui.common.HeroFraming
import haddad.three.notas.ui.nav.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import haddad.three.notas.data.Visibility as NoteVisibility

enum class EditorSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    UNSAVED_CHANGES,
}

/**
 * Shared owner of every persisted field, setter, and note-lifecycle action common to both the
 * Edit Note and Edit List screens. The two screens differ only in their body payload (free-text
 * body vs. a checklist) and the create/update/diff calls that depend on it, so those are left as
 * abstract hooks ([persistNewDraftForAttachment], [saveIfNeeded]) while everything else lives here.
 *
 * Each field is exposed as its own [StateFlow] so leaf composables can collect just the slice they
 * render and skip recompositions when unrelated fields change.
 *
 * Save/load is serialized through a single [EditorPersistenceSession] so ON_STOP, dispose, and
 * explicit calls cannot race against each other (or against the async note load in the subclass
 * `init`).
 */
abstract class BaseEditorViewModel(
    protected val repository: NoteRepository,
    protected val appMediaStorage: AppMediaStorage?,
    savedStateHandle: SavedStateHandle,
    private val defaultNotePrefs: DefaultNotePrefs? = null,
) : ViewModel() {
    protected val noteId: Long? =
        savedStateHandle
            .get<Long>(Routes.ARG_ID)
            ?.takeIf { it > 0L }

    /** Only the Edit Note screen passes a prefill; lists leave this blank. */
    protected val prefillBody: String = savedStateHandle[Routes.ARG_PREFILL] ?: ""

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    /**
     * Snapshot of the underlying note's [haddad.three.notas.data.NoteEntity.completedAt] as a
     * boolean. Driven by the live DB observer started in the subclass `init`, so external
     * completion via swipe / notification action / repository.markCompleted is reflected here too.
     */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    /**
     * Snapshot of [haddad.three.notas.data.NoteEntity.pinnedAt] as a boolean. Unlike [starred],
     * pinning is NOT part of the editor draft: it writes through immediately via
     * [NoteRepository.setPinned] and comes back through the live observer, the same way
     * [completed] and [archived] do. Pinning is placement, not content, so it should never
     * mark the editor dirty or need a save to take effect.
     */
    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _reminderAt = MutableStateFlow<Long?>(null)
    val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()

    private val _endAt = MutableStateFlow<Long?>(null)
    val endAt: StateFlow<Long?> = _endAt.asStateFlow()

    private val _isAllDay = MutableStateFlow(false)
    val isAllDay: StateFlow<Boolean> = _isAllDay.asStateFlow()

    private val _shoulderId = MutableStateFlow<Long?>(null)
    val shoulderId: StateFlow<Long?> = _shoulderId.asStateFlow()

    private val _shoulder = MutableStateFlow<ShoulderEntity?>(null)
    val shoulder: StateFlow<ShoulderEntity?> = _shoulder.asStateFlow()

    private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
    val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

    private val _reminders = MutableStateFlow<List<NoteReminder>>(emptyList())
    val reminders: StateFlow<List<NoteReminder>> = _reminders.asStateFlow()

    private val _importance = MutableStateFlow(Importance.DEFAULT)
    val importance: StateFlow<Importance> = _importance.asStateFlow()

    private val _visibility = MutableStateFlow(NoteVisibility.DEFAULT)
    val visibility: StateFlow<NoteVisibility> = _visibility.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _pictureUri = MutableStateFlow<String?>(null)
    val pictureUri: StateFlow<String?> = _pictureUri.asStateFlow()

    /** Bumped whenever the hero bytes change, including in-place edits that keep the same URI string. */
    private val _pictureRevision = MutableStateFlow(0L)
    val pictureRevision: StateFlow<Long> = _pictureRevision.asStateFlow()

    private val _pictureHeroFraming = MutableStateFlow<String?>(null)
    val pictureHeroFraming: StateFlow<String?> = _pictureHeroFraming.asStateFlow()

    private val _colorIndex = MutableStateFlow(0)
    val colorIndex: StateFlow<Int> = _colorIndex.asStateFlow()

    private val _iconKey = MutableStateFlow<String?>(null)
    val iconKey: StateFlow<String?> = _iconKey.asStateFlow()

    private val _actions = MutableStateFlow<List<NoteAction>>(emptyList())
    val actions: StateFlow<List<NoteAction>> = _actions.asStateFlow()

    private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

    private val _createdAt = MutableStateFlow<Long?>(null)
    val createdAt: StateFlow<Long?> = _createdAt.asStateFlow()

    private val _updatedAt = MutableStateFlow<Long?>(null)
    val updatedAt: StateFlow<Long?> = _updatedAt.asStateFlow()

    /** True after the existing row or the new draft's defaults have finished loading. */
    private val _loaded = MutableStateFlow(noteId == null && defaultNotePrefs == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _missingNote = MutableStateFlow(false)
    val missingNote: StateFlow<Boolean> = _missingNote.asStateFlow()

    /**
     * Mirrors the underlying note's archived / trashed shelf. Used by the edit screen to flip into
     * read-only mode and swap the bottom-bar action set. New notes/lists always start active.
     */
    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()

    private val _trashed = MutableStateFlow(false)
    val trashed: StateFlow<Boolean> = _trashed.asStateFlow()

    /**
     * Seeds a brand-new draft from Settings > Defaults. Does not mark dirty: blank notes with only
     * defaults still discard on back; typing or an explicit edit then persists them with the
     * seeded values. The default reminder time and recurrence are deliberately not applied here -
     * they only seed the reminder picker, so a new note never gets a reminder it wasn't given.
     */
    private fun applyNewNoteDefaults(defaults: DefaultNotePreferencesState) {
        if (!visibilityEditedLocally) _visibility.value = defaults.defaultVisibility
        if (!importanceEditedLocally) _importance.value = defaults.defaultImportance
    }

    protected fun updateTimestamps(
        createdAt: Long?,
        updatedAt: Long?,
    ) {
        _createdAt.value = createdAt
        _updatedAt.value = updatedAt
    }

    /**
     * True once this session is backed by a database row ([loadedId] non-null). New drafts start
     * false so the action bar omits archive/trash until the first save (or attachment create)
     * assigns an id, without tying that UI to the navigation argument (which stays null).
     */
    private val _hasPersistedRow = MutableStateFlow(noteId != null)
    val hasPersistedRow: StateFlow<Boolean> = _hasPersistedRow.asStateFlow()

    private val _currentNoteId = MutableStateFlow(noteId)
    val currentNoteId: StateFlow<Long?> = _currentNoteId.asStateFlow()

    protected val persistence =
        EditorPersistenceSession(initialDirty = noteId == null && prefillBody.isNotBlank())
    val hasUnsavedChanges: StateFlow<Boolean> = persistence.hasUnsavedChanges

    private val _saveStatus = MutableStateFlow(EditorSaveStatus.IDLE)
    val saveStatus: StateFlow<EditorSaveStatus> = _saveStatus.asStateFlow()

    protected fun updateSaveStatus(status: EditorSaveStatus) {
        _saveStatus.value = status
    }

    private var autoSaveJob: Job? = null

    abstract fun hasMeaningfulContent(): Boolean

    fun triggerAutoSave(
        untitledName: String = "Untitled",
        delayMs: Long = 650L,
    ) {
        if (!hasMeaningfulContent()) {
            _saveStatus.value = EditorSaveStatus.IDLE
            return
        }
        _saveStatus.value = EditorSaveStatus.UNSAVED_CHANGES
        autoSaveJob?.cancel()
        autoSaveJob =
            viewModelScope.launch {
                delay(delayMs)
                if (persistence.isDirty && hasMeaningfulContent()) {
                    _saveStatus.value = EditorSaveStatus.SAVING
                    try {
                        saveIfNeeded(untitledName)
                        _saveStatus.value = EditorSaveStatus.SAVED
                    } catch (e: Exception) {
                        _saveStatus.value = EditorSaveStatus.UNSAVED_CHANGES
                    }
                }
            }
    }

    protected var loadedId: Long? = noteId
    protected var originalNote: haddad.three.notas.data.NoteEntity? = null
    private var remindersEditedLocally = false
    private var visibilityEditedLocally = false
    private var importanceEditedLocally = false

    init {
        val initialDateMillis: Long? = savedStateHandle.get<Long>(Routes.ARG_INITIAL_DATE)?.takeIf { it > 0L }
        if (noteId == null && initialDateMillis != null) {
            _reminderAt.value = initialDateMillis
            _reminders.value = listOf(NoteReminder(reminderAt = initialDateMillis, recurrence = null))
        }

        if (noteId == null && defaultNotePrefs != null) {
            // Acquire before launching so a save or attachment cannot persist unseeded defaults.
            check(persistence.tryLock()) { "persistence lock must be unlocked at construction" }
            viewModelScope.launch {
                try {
                    applyNewNoteDefaults(defaultNotePrefs.snapshot())
                } finally {
                    _loaded.value = true
                    persistence.unlock()
                }
            }
        }
    }

    protected fun markDirty() {
        persistence.markDirty()
        _saveStatus.value = EditorSaveStatus.UNSAVED_CHANGES
    }

    protected fun syncHasPersistedRow() {
        _hasPersistedRow.value = loadedId != null
        _currentNoteId.value = loadedId
    }

    protected fun finishInitialLoad(noteFound: Boolean) {
        _missingNote.value = _missingNote.value || !noteFound
        _loaded.value = true
    }

    protected fun acceptPersistedSnapshot(note: haddad.three.notas.data.NoteEntity?) {
        originalNote = note
        remindersEditedLocally = note != null && _reminders.value != note.getActiveReminders()
    }

    /**
     * Populates every field this base owns from a freshly loaded row. Subclasses call this from
     * their `init` load and then set their own body/items payload.
     */
    protected fun applyLoadedCommon(existing: NoteWithItems) {
        val n = existing.note
        _title.value = n.title
        val activeRems = n.getActiveReminders()
        _reminders.value = activeRems
        val soonest = activeRems.minByOrNull { it.reminderAt }
        _reminderAt.value = soonest?.reminderAt
        _endAt.value = n.endAt ?: soonest?.endAt
        _isAllDay.value = n.isAllDay
        _recurrence.value = soonest?.recurrence?.sanitized()
        _importance.value = n.importance
        _visibility.value = n.visibility
        _shoulderId.value = n.shoulderId
        _shoulder.value = existing.shoulder
        _locked.value = n.locked
        _pictureUri.value = n.pictureUri
        _pictureHeroFraming.value = n.pictureHeroFraming
        _colorIndex.value = n.colorIndex
        _iconKey.value = n.iconKey
        _actions.value = n.actions
        _attachments.value = existing.attachments
        _archived.value = n.archived
        _trashed.value = n.trashed
        _completed.value = n.completedAt != null
        _pinned.value = n.pinned
        _createdAt.value = n.createdAt
        _updatedAt.value = n.updatedAt
        acceptPersistedSnapshot(n)
    }

    /**
     * Live-mirror only the fields that can be written from OUTSIDE this VM while the editor is open:
     * reminderAt + recurrence (snooze action, recurrence advance after a fire, mark-as-done from
     * notification), plus trashed/completed/timestamps. The other fields are owned by this VM's
     * user-input flow and would clobber unsaved drafts if mirrored. Room's Flow only emits on actual
     * row changes, so this is a single small distinct subscription - negligible battery impact.
     */
    protected fun startExternalFieldMirror(id: Long) {
        viewModelScope.launch {
            repository.observe(id).collect { row ->
                if (row == null) {
                    _missingNote.value = true
                    return@collect
                }
                val n = row.note
                val activeRems = n.getActiveReminders()
                // Room also emits after pinning, attachments, and our own suspended saves.
                // Keep the draft until its reminder changes have actually been persisted.
                if (!remindersEditedLocally) {
                    _reminders.value = activeRems
                    val soonest = activeRems.minByOrNull { it.reminderAt }
                    _reminderAt.value = soonest?.reminderAt
                    _recurrence.value = soonest?.recurrence?.sanitized()
                }
                if (_archived.value != n.archived) _archived.value = n.archived
                if (_trashed.value != n.trashed) _trashed.value = n.trashed
                val isCompleted = n.completedAt != null
                if (_completed.value != isCompleted) _completed.value = isCompleted
                if (_pinned.value != n.pinned) _pinned.value = n.pinned
                if (_createdAt.value != n.createdAt) _createdAt.value = n.createdAt
                if (_updatedAt.value != n.updatedAt) _updatedAt.value = n.updatedAt
                originalNote =
                    originalNote?.copy(
                        archived = n.archived,
                        trashed = n.trashed,
                        completedAt = n.completedAt,
                        pinnedAt = n.pinnedAt,
                        reminderAt = n.reminderAt,
                        endAt = n.endAt,
                        isAllDay = n.isAllDay,
                        recurrence = n.recurrence,
                        reminders = n.reminders,
                        createdAt = n.createdAt,
                        updatedAt = n.updatedAt,
                    )
            }
        }
    }

    fun setTitle(value: String) {
        if (_title.value == value) return
        _title.value = value
        markDirty()
        triggerAutoSave()
    }

    fun setReminder(
        at: Long?,
        rule: RecurrenceRule?,
    ) {
        val normalized = rule?.sanitized()
        if (_reminderAt.value == at && _recurrence.value == normalized) return
        _reminderAt.value = at
        _recurrence.value = normalized
        if (at != null) {
            _reminders.value = listOf(NoteReminder(at, normalized))
        } else {
            _reminders.value = emptyList()
        }
        remindersEditedLocally = true
        markDirty()
    }

    fun setReminders(remindersList: List<NoteReminder>) {
        if (_reminders.value == remindersList) return
        _reminders.value = remindersList
        val soonest = remindersList.minByOrNull { it.reminderAt }
        _reminderAt.value = soonest?.reminderAt
        _endAt.value = soonest?.endAt
        _recurrence.value = soonest?.recurrence?.sanitized()
        remindersEditedLocally = true
        markDirty()
    }

    fun setImportance(value: Importance) {
        importanceEditedLocally = true
        if (_importance.value == value) return
        _importance.value = value
        markDirty()
    }

    fun setVisibility(value: NoteVisibility) {
        visibilityEditedLocally = true
        if (_visibility.value == value) return
        _visibility.value = value
        markDirty()
        refreshActiveNotificationVisibility(value)
    }

    private fun refreshActiveNotificationVisibility(value: NoteVisibility) {
        val id = loadedId ?: return
        viewModelScope.launch {
            repository.refreshNotificationVisibilityPreview(id, value)
        }
    }

    fun toggleLock() {
        _locked.value = !_locked.value
        markDirty()
    }

    fun setPictureUri(value: String?) {
        if (_pictureUri.value != value) {
            _pictureUri.value = value
        }
        if (value == null) {
            _pictureHeroFraming.value = null
        }
        _pictureRevision.value = _pictureRevision.value + 1L
        markDirty()
    }

    fun setHeroWithFraming(
        pictureUri: String,
        framing: HeroFraming,
    ) {
        _pictureUri.value = pictureUri
        _pictureHeroFraming.value = framing.toJsonString()
        _pictureRevision.value = _pictureRevision.value + 1L
        markDirty()
    }

    fun setColorIndex(value: Int) {
        val sanitized = value.coerceIn(0, 9)
        if (_colorIndex.value == sanitized) return
        _colorIndex.value = sanitized
        markDirty()
    }

    fun setIconKey(value: String?) {
        if (_iconKey.value == value) return
        _iconKey.value = value
        markDirty()
    }

    fun setActions(value: List<NoteAction>) {
        if (_actions.value == value) return
        _actions.value = value
        markDirty()
    }

    fun addAttachment(
        uri: Uri,
        name: String,
        mime: String?,
    ) {
        viewModelScope.launch {
            persistence.withLock {
                val id = loadedId ?: persistNewDraftForAttachment()
                val copyResult =
                    appMediaStorage?.copyAttachmentToPrivateStorage(
                        noteId = id,
                        sourceUri = uri,
                        displayName = name,
                        mimeType = mime,
                    )
                val attachmentUri = copyResult?.uriString ?: uri.toString()
                val attachmentDisplayName = copyResult?.displayName ?: name
                val attachmentMimeType = copyResult?.mimeType ?: mime
                repository.addAttachment(id, attachmentUri, attachmentDisplayName, attachmentMimeType)
                _attachments.value = repository.get(id)?.attachments ?: emptyList()
                markDirty()
            }
        }
    }

    fun removeAttachment(attachmentId: Long) {
        viewModelScope.launch {
            persistence.withLock {
                repository.removeAttachment(attachmentId)
                val id = loadedId
                _attachments.value =
                    if (id != null) {
                        repository.get(id)?.attachments ?: emptyList()
                    } else {
                        _attachments.value.filterNot { it.id == attachmentId }
                    }
                markDirty()
            }
        }
    }

    fun setShoulderId(value: Long?) {
        if (_shoulderId.value == value) return
        _shoulderId.value = value
        markDirty()
    }

    fun setShoulder(value: ShoulderEntity?) {
        _shoulder.value = value
        setShoulderId(value?.id)
    }

    val allShoulders: StateFlow<List<ShoulderEntity>> =
        repository.shoulderRepository?.getAllShoulders()?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        ) ?: MutableStateFlow(emptyList())

    fun createShoulder(
        name: String,
        colorHex: String,
    ) {
        viewModelScope.launch {
            val id = repository.shoulderRepository?.createShoulder(name, colorHex)
            setShoulderId(id)
        }
    }

    fun updateShoulder(shoulder: ShoulderEntity) {
        viewModelScope.launch {
            repository.shoulderRepository?.updateShoulder(shoulder)
            if (_shoulderId.value == shoulder.id) {
                _shoulder.value = shoulder
            }
        }
    }

    fun deleteShoulder(shoulder: ShoulderEntity) {
        viewModelScope.launch {
            repository.shoulderRepository?.deleteShoulder(shoulder)
            if (_shoulderId.value == shoulder.id) {
                setShoulderId(null)
                _shoulder.value = null
            }
        }
    }

    protected fun currentOptions() =
        NoteOptions(
            reminderAt = _reminderAt.value,
            endAt = _endAt.value ?: _reminders.value.firstOrNull()?.endAt,
            isAllDay = _isAllDay.value,
            importance = _importance.value,
            visibility = _visibility.value,
            pictureUri = _pictureUri.value,
            pictureHeroFraming = _pictureHeroFraming.value,
            locked = _locked.value,
            iconKey = _iconKey.value,
            actions = _actions.value,
            recurrence = _recurrence.value,
            reminders = _reminders.value,
            shoulderId = _shoulderId.value,
        )

    /**
     * Creates the backing row for a still-unsaved draft when an attachment is added before the
     * first explicit save. Note vs. list differ in the create call (and the note variant restores
     * the starred flag), so each subclass supplies its own. Implementations must set [loadedId]
     * and call [syncHasPersistedRow].
     */
    protected abstract suspend fun persistNewDraftForAttachment(): Long

    /**
     * Persist the in-memory state. Safe to call from ON_STOP, onDispose, or any other lifecycle
     * hook; concurrent calls serialize through [persistence] so we never insert the same draft
     * twice. Returns an undo action that reverts this save, or null when nothing was written.
     */
    abstract suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)?

    protected fun syncTimestamps() {
        val id = loadedId ?: return
        viewModelScope.launch {
            val cur = repository.get(id)?.note
            if (cur != null) {
                _createdAt.value = cur.createdAt
                _updatedAt.value = cur.updatedAt
            }
        }
    }

    suspend fun trashCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.moveToTrash(id)
            persistence.clearDirty()
            _trashed.value = true
            _archived.value = false
        }
        syncTimestamps()
    }

    /**
     * Flip the note's done state from inside the editor's bottom bar. Routes through
     * [NoteRepository.markCompleted] / [NoteRepository.markIncomplete] so recurrence is honored -
     * completing a recurring note rolls it forward instead of moving it to Done. The live observer
     * started in `init` picks up the new value and updates [completed] automatically.
     */
    suspend fun toggleCompleted() {
        val id = loadedId ?: return
        if (_completed.value) {
            repository.markIncomplete(id)
        } else {
            repository.markCompleted(id)
        }
        syncTimestamps()
    }

    /**
     * Pin / unpin from the editor's bottom bar. Writes through immediately (no dirty flag, no
     * save required) and the live observer started in `init` reflects the new value back into
     * [pinned]. No-op for a draft that has no backing row yet, matching [toggleCompleted].
     */
    suspend fun togglePinned() {
        val id = loadedId ?: return
        repository.setPinned(id, !_pinned.value)
    }

    /** Flip the note to the archive shelf after saving any in-flight edits. */
    suspend fun archiveCurrent(untitledName: String) {
        // Persist any in-flight edits first so the archive snapshot matches what the user sees.
        saveIfNeeded(untitledName)
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.archiveNote(id)
            _archived.value = true
            _trashed.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    suspend fun unarchiveCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.unarchiveNote(id)
            _archived.value = false
            _trashed.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    suspend fun restoreFromTrashCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.restoreFromTrash(id)
            _trashed.value = false
            _archived.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    /** Permanent delete of the current note. Called from the trashed-state action bar after confirm. */
    suspend fun deleteForeverCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.deleteForever(id)
            persistence.clearDirty()
        }
    }

    suspend fun fireNotification(
        context: android.content.Context,
        untitledName: String,
    ) {
        saveIfNeeded(untitledName)
        val id = loadedId ?: return
        val noteWithItems = repository.get(id) ?: return
        val reminderPrefs =
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    SettingsDependenciesEntryPoint::class.java,
                ).reminderPrefs()
        val keepUntilDone = reminderPrefs.snapshot().keepReminderNotificationsUntilDone
        haddad.three.notas.reminders.ReminderReceiver.showNotification(
            context = context,
            note = noteWithItems.note,
            items = noteWithItems.items,
            keepUntilDone = keepUntilDone,
        )
        android.widget.Toast
            .makeText(
                context,
                context.getString(haddad.three.notas.R.string.notification_created),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
    }
}

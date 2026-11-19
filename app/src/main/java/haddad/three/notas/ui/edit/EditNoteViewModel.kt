package haddad.three.notas.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haddad.three.notas.data.AppMediaStorage
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.NoteOptions
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.getActiveReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the free-text body for the Edit Note screen on top of every persisted field shared with
 * lists in [BaseEditorViewModel]. Only the body payload and the create/update/diff calls that
 * depend on it live here; setters, tag handling, and the note-lifecycle actions are inherited.
 */
@HiltViewModel
class EditNoteViewModel
    @Inject
    constructor(
        repository: NoteRepository,
        appMediaStorage: AppMediaStorage? = null,
        savedStateHandle: SavedStateHandle,
        defaultNotePrefs: DefaultNotePrefs? = null,
    ) : BaseEditorViewModel(repository, appMediaStorage, savedStateHandle, defaultNotePrefs) {
        private val _body = MutableStateFlow(if (noteId == null) prefillBody else "")
        val body: StateFlow<String> = _body.asStateFlow()

        init {
            if (noteId != null) {
                check(persistence.tryLock()) { "persistence lock must be unlocked at construction" }
                viewModelScope.launch {
                    var noteFound = false
                    try {
                        val existing = repository.get(noteId)
                        if (existing != null) {
                            noteFound = true
                            applyLoadedCommon(existing)
                            _body.value = existing.note.body
                        }
                    } finally {
                        // Leave loading when the load finishes: missing row, success, or thrown from get().
                        finishInitialLoad(noteFound)
                        persistence.unlock()
                    }
                }
                startExternalFieldMirror(noteId)
            }
        }

        fun setBody(value: String) {
            if (_body.value == value) return
            _body.value = value
            markDirty()
            triggerAutoSave()
        }

        override fun hasMeaningfulContent(): Boolean {
            val t = title.value.trim()
            val b = _body.value.trim()
            val opts = currentOptions()
            return t.isNotEmpty() || b.isNotEmpty() || attachments.value.isNotEmpty() ||
                opts.pictureUri != null || opts.reminderAt != null || opts.actions.isNotEmpty() ||
                opts.iconKey != null || opts.shoulderId != null || opts.endAt != null || opts.isAllDay
        }

        override suspend fun persistNewDraftForAttachment(): Long {
            val newId =
                repository.createNote(
                    title = title.value,
                    body = _body.value,
                    colorIndex = colorIndex.value,
                    options = currentOptions(),
                )
            loadedId = newId
            syncHasPersistedRow()
            startExternalFieldMirror(newId)
            return newId
        }

        private fun hasNetChanges(): Boolean {
            val id = loadedId
            val t = title.value
            val b = _body.value
            val opts = currentOptions()
            if (id == null) {
                return t.isNotBlank() || b.isNotBlank() || attachments.value.isNotEmpty() || opts.pictureUri != null || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null || opts.shoulderId != null || opts.endAt != null || opts.isAllDay
            } else {
                val old = originalNote ?: return true
                if (t != old.title) return true
                if (b != old.body) return true
                if (opts.reminderAt != old.reminderAt) return true
                if (opts.endAt != old.endAt) return true
                if (opts.isAllDay != old.isAllDay) return true
                if (opts.importance != old.importance) return true
                if (opts.visibility != old.visibility) return true
                if (opts.pictureUri != old.pictureUri) return true
                if (opts.pictureHeroFraming != old.pictureHeroFraming) return true
                if (opts.locked != old.locked) return true
                if (colorIndex.value != old.colorIndex) return true
                if (opts.iconKey != old.iconKey) return true
                if (opts.actions != old.actions) return true
                if (opts.recurrence != old.recurrence) return true
                if (opts.reminders != old.getActiveReminders()) return true
                if (opts.shoulderId != old.shoulderId) return true
                return false
            }
        }

        override suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)? {
            return persistence.withLock {
                if (missingNote.value) {
                    persistence.clearDirty()
                    updateSaveStatus(EditorSaveStatus.IDLE)
                    return@withLock null
                }
                val id = loadedId
                if (id == null && !hasMeaningfulContent()) {
                    persistence.clearDirty()
                    updateSaveStatus(EditorSaveStatus.IDLE)
                    return@withLock null
                }
                if (!hasNetChanges()) {
                    acceptPersistedSnapshot(originalNote)
                    persistence.clearDirty()
                    updateSaveStatus(EditorSaveStatus.SAVED)
                    return@withLock null
                }
                updateSaveStatus(EditorSaveStatus.SAVING)
                val titleValue = title.value
                val bodyValue = _body.value
                val finalTitle = titleValue.ifBlank { untitledName }
                if (id == null) {
                    if (!persistence.isDirty) {
                        updateSaveStatus(EditorSaveStatus.IDLE)
                        return@withLock null
                    }
                    val epochAtWrite = persistence.currentEpoch()
                    val newId = repository.createNote(finalTitle, bodyValue, colorIndex.value, currentOptions())
                    loadedId = newId
                    syncHasPersistedRow()
                    startExternalFieldMirror(newId)
                    if (titleValue.isBlank()) setTitle(finalTitle)
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val savedNote = repository.get(newId)?.note
                    acceptPersistedSnapshot(savedNote)
                    if (savedNote != null) {
                        updateTimestamps(savedNote.createdAt, savedNote.updatedAt)
                    }
                    updateSaveStatus(EditorSaveStatus.SAVED)

                    return@withLock {
                        repository.moveToTrash(newId)
                    }
                } else {
                    if (!persistence.isDirty) {
                        updateSaveStatus(EditorSaveStatus.SAVED)
                        return@withLock null
                    }
                    val old = originalNote
                    val epochAtWrite = persistence.currentEpoch()
                    repository.updateNote(id, finalTitle, bodyValue, colorIndex.value, currentOptions())
                    if (titleValue.isBlank()) setTitle(finalTitle)
                    val cur = repository.get(id)?.note
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val savedNote = repository.get(id)?.note
                    acceptPersistedSnapshot(savedNote)
                    if (savedNote != null) {
                        updateTimestamps(savedNote.createdAt, savedNote.updatedAt)
                    }
                    updateSaveStatus(EditorSaveStatus.SAVED)

                    if (old != null) {
                        return@withLock {
                            repository.updateNote(
                                id = id,
                                title = old.title,
                                body = old.body,
                                colorIndex = old.colorIndex,
                                options =
                                    NoteOptions(
                                        reminderAt = old.reminderAt,
                                        endAt = old.endAt,
                                        isAllDay = old.isAllDay,
                                        importance = old.importance,
                                        visibility = old.visibility,
                                        pictureUri = old.pictureUri,
                                        pictureHeroFraming = old.pictureHeroFraming,
                                        locked = old.locked,
                                        iconKey = old.iconKey,
                                        actions = old.actions,
                                        recurrence = old.recurrence,
                                        reminders = old.getActiveReminders(),
                                        shoulderId = old.shoulderId,
                                    ),
                            )
                        }
                    } else {
                        return@withLock null
                    }
                }
            }
        }
    }

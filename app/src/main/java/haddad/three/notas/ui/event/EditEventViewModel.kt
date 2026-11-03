package haddad.three.notas.ui.event

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAction
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteOptions
import haddad.three.notas.data.NoteReminder
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.RecurrenceRule
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.Visibility
import haddad.three.notas.data.getActiveReminders
import haddad.three.notas.ui.nav.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class PendingAttachment(
    val id: Long,
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
)

data class ParsedEventData(
    val isAllDay: Boolean,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val notes: String,
)

@HiltViewModel
class EditEventViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val noteRepository: NoteRepository,
        private val shoulderRepository: ShoulderRepository? = null,
    ) : ViewModel() {
        val eventId: Long? = savedStateHandle.get<Long>(Routes.ARG_ID)?.takeIf { it > 0L }
        private val prefillDateMillis: Long? = savedStateHandle.get<Long>(Routes.ARG_DATE_MILLIS)?.takeIf { it > 0L }

        val isEditMode: Boolean = eventId != null

        private val _title = MutableStateFlow("")
        val title: StateFlow<String> = _title.asStateFlow()

        private val _notes = MutableStateFlow("")
        val notes: StateFlow<String> = _notes.asStateFlow()

        private val _selectedDate = MutableStateFlow(LocalDate.now())
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        private val _endDate = MutableStateFlow<LocalDate?>(null)
        val endDate: StateFlow<LocalDate?> = _endDate.asStateFlow()

        private var existingHeroFraming: String? = null
        private var existingLocked: Boolean = false

        private val _startTime = MutableStateFlow(LocalTime.of(9, 0))
        val startTime: StateFlow<LocalTime> = _startTime.asStateFlow()

        private val _endTime = MutableStateFlow(LocalTime.of(9, 30))
        val endTime: StateFlow<LocalTime> = _endTime.asStateFlow()

        private val _isAllDay = MutableStateFlow(false)
        val isAllDay: StateFlow<Boolean> = _isAllDay.asStateFlow()

        private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
        val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

        private val _selectedShoulderId = MutableStateFlow<Long?>(null)
        val selectedShoulderId: StateFlow<Long?> = _selectedShoulderId.asStateFlow()

        private val _reminders = MutableStateFlow<List<NoteReminder>>(emptyList())
        val reminders: StateFlow<List<NoteReminder>> = _reminders.asStateFlow()

        private val _reminderAtStart = MutableStateFlow(true)
        val reminderAtStart: StateFlow<Boolean> = _reminderAtStart.asStateFlow()

        private val _actions = MutableStateFlow<List<NoteAction>>(emptyList())
        val actions: StateFlow<List<NoteAction>> = _actions.asStateFlow()

        private val _visibility = MutableStateFlow(Visibility.DEFAULT)
        val visibility: StateFlow<Visibility> = _visibility.asStateFlow()

        private val _importance = MutableStateFlow(Importance.DEFAULT)
        val importance: StateFlow<Importance> = _importance.asStateFlow()

        private val _pictureUri = MutableStateFlow<String?>(null)
        val pictureUri: StateFlow<String?> = _pictureUri.asStateFlow()

        private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
        val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

        private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
        val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

        private val _isSaving = MutableStateFlow(false)
        val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

        private val _saveError = MutableStateFlow<String?>(null)
        val saveError: StateFlow<String?> = _saveError.asStateFlow()

        private val _isLoaded = MutableStateFlow(false)
        val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

        private val _colorIndex = MutableStateFlow(8)
        val colorIndex: StateFlow<Int> = _colorIndex.asStateFlow()

        private val _iconKey = MutableStateFlow<String?>("calendar_month")
        val iconKey: StateFlow<String?> = _iconKey.asStateFlow()

        private var originalColorIndex = 0

        val availableShoulders: StateFlow<List<ShoulderEntity>> =
            (shoulderRepository?.getAllShoulders() ?: flowOf(emptyList()))
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        val selectedShoulder: StateFlow<ShoulderEntity?> =
            combine(
                availableShoulders,
                _selectedShoulderId,
            ) { shoulders, id ->
                shoulders.firstOrNull { it.id == id }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

        init {
            if (eventId != null) {
                viewModelScope.launch {
                    val noteWithItems = noteRepository.get(eventId)
                    if (noteWithItems != null) {
                        val note = noteWithItems.note
                        originalColorIndex = note.colorIndex
                        _colorIndex.value = if (note.colorIndex > 0) note.colorIndex else 8
                        _iconKey.value = note.iconKey ?: "calendar_month"
                        _title.value = note.title
                        if (note.endAt != null || note.isAllDay) {
                            _isAllDay.value = note.isAllDay
                            val startTime =
                                if (note.reminderAt != null && note.reminderAt > 0L) {
                                    Instant.ofEpochMilli(note.reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                                } else {
                                    LocalTime.of(9, 0)
                                }
                            val endTime =
                                if (note.endAt != null && note.endAt > 0L) {
                                    Instant.ofEpochMilli(note.endAt).atZone(ZoneId.systemDefault()).toLocalTime()
                                } else {
                                    startTime.plusMinutes(30)
                                }
                            _startTime.value = startTime
                            _endTime.value = endTime
                            _notes.value = note.body
                        } else {
                            val parsed =
                                parseEventBodyAndTimes(
                                    body = note.body,
                                    reminderAt = note.reminderAt,
                                )
                            _isAllDay.value = parsed.isAllDay
                            _startTime.value = parsed.startTime
                            _endTime.value = parsed.endTime
                            _notes.value = parsed.notes
                        }

                        if (note.endAt != null && note.endAt > 0L) {
                            _endDate.value = Instant.ofEpochMilli(note.endAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        } else {
                            _endDate.value = null
                        }
                        existingHeroFraming = note.pictureHeroFraming
                        existingLocked = note.locked

                        val epoch = note.reminderAt ?: note.endAt ?: (prefillDateMillis?.takeIf { it > 0L } ?: System.currentTimeMillis())
                        _selectedDate.value =
                            Instant
                                .ofEpochMilli(epoch)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        val activeReminders = note.getActiveReminders()
                        if (activeReminders.isNotEmpty()) {
                            _reminders.value = activeReminders
                            _reminderAtStart.value = true
                        } else if (note.reminderAt != null) {
                            _reminders.value = listOf(NoteReminder(note.reminderAt, note.recurrence, endAt = note.endAt))
                            _reminderAtStart.value = true
                        } else {
                            _reminders.value = emptyList()
                            _reminderAtStart.value = false
                        }

                        _selectedShoulderId.value = note.shoulderId
                        _recurrence.value = note.recurrence ?: note.getActiveReminders().firstOrNull()?.recurrence
                        _actions.value = note.actions
                        _visibility.value = note.visibility
                        _importance.value = note.importance
                        _pictureUri.value = note.pictureUri
                        _attachments.value = noteWithItems.attachments
                    }
                    _isLoaded.value = true
                }
            } else {
                if (prefillDateMillis != null && prefillDateMillis > 0L) {
                    _selectedDate.value =
                        Instant
                            .ofEpochMilli(prefillDateMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                }
                _isLoaded.value = true
            }
        }

        fun setTitle(newTitle: String) {
            _title.value = newTitle
            if (_saveError.value != null) _saveError.value = null
        }

        fun setNotes(newNotes: String) {
            _notes.value = newNotes
        }

        fun setSelectedDate(newDate: LocalDate) {
            _selectedDate.value = newDate
        }

        fun setEndDate(newDate: LocalDate?) {
            _endDate.value = newDate
        }

        fun setStartTime(newTime: LocalTime) {
            _startTime.value = newTime
            if (_saveError.value != null) _saveError.value = null
        }

        fun setEndTime(newTime: LocalTime) {
            _endTime.value = newTime
            if (_saveError.value != null) _saveError.value = null
        }

        fun setAllDay(allDay: Boolean) {
            _isAllDay.value = allDay
            if (_saveError.value != null) _saveError.value = null
        }

        fun setRecurrence(newRecurrence: RecurrenceRule?) {
            _recurrence.value = newRecurrence
        }

        fun setSelectedShoulderId(id: Long?) {
            _selectedShoulderId.value = id
        }

        fun setReminders(newReminders: List<NoteReminder>) {
            _reminders.value = newReminders
            _reminderAtStart.value = newReminders.isNotEmpty()
        }

        fun setReminderAtStart(enabled: Boolean) {
            _reminderAtStart.value = enabled
            if (!enabled) {
                _reminders.value = emptyList()
            } else {
                val date = _selectedDate.value
                val time = _startTime.value
                val epoch =
                    date
                        .atTime(time)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                _reminders.value = listOf(NoteReminder(reminderAt = epoch, recurrence = _recurrence.value))
            }
        }

        fun setActions(newActions: List<NoteAction>) {
            _actions.value = newActions
        }

        fun setVisibility(newVisibility: Visibility) {
            _visibility.value = newVisibility
        }

        fun setImportance(newImportance: Importance) {
            _importance.value = newImportance
        }

        fun setPictureUri(uri: String?) {
            _pictureUri.value = uri
        }

        fun setColorIndex(index: Int) {
            _colorIndex.value = index.coerceIn(0, 9)
        }

        fun setIconKey(key: String?) {
            _iconKey.value = key
        }

        fun createShoulder(
            name: String,
            colorHex: String,
        ) {
            viewModelScope.launch {
                val id = shoulderRepository?.createShoulder(name = name, colorHex = colorHex)
                if (id != null) {
                    _selectedShoulderId.value = id
                }
            }
        }

        fun updateShoulder(shoulder: ShoulderEntity) {
            viewModelScope.launch {
                shoulderRepository?.updateShoulder(shoulder)
            }
        }

        fun deleteShoulder(shoulder: ShoulderEntity) {
            viewModelScope.launch {
                if (_selectedShoulderId.value == shoulder.id) {
                    _selectedShoulderId.value = null
                }
                shoulderRepository?.deleteShoulder(shoulder)
            }
        }

        fun addAttachment(
            uri: Uri,
            displayName: String,
            mimeType: String?,
        ) {
            viewModelScope.launch {
                val uriStr = uri.toString()
                if (eventId != null) {
                    noteRepository.addAttachment(eventId, uriStr, displayName, mimeType)
                    val updated = noteRepository.get(eventId)?.attachments ?: emptyList()
                    _attachments.value = updated
                } else {
                    val tempId = System.currentTimeMillis()
                    _pendingAttachments.update { list ->
                        list + PendingAttachment(tempId, uriStr, displayName, mimeType)
                    }
                }
            }
        }

        fun removeAttachment(id: Long) {
            viewModelScope.launch {
                if (eventId != null) {
                    noteRepository.removeAttachment(id)
                    val updated = noteRepository.get(eventId)?.attachments ?: emptyList()
                    _attachments.value = updated
                } else {
                    _pendingAttachments.update { list -> list.filterNot { it.id == id } }
                }
            }
        }

        suspend fun saveEvent(): Result<Long> {
            val trimmedTitle = _title.value.trim()
            if (trimmedTitle.isBlank()) {
                val error = "Please enter an event title"
                _saveError.value = error
                return Result.failure(IllegalArgumentException(error))
            }

            if (!_isAllDay.value && _startTime.value == _endTime.value) {
                val error = "Start time and end time cannot be the same"
                _saveError.value = error
                return Result.failure(IllegalArgumentException(error))
            }

            _isSaving.value = true
            _saveError.value = null

            val date = _selectedDate.value
            val isAllDay = _isAllDay.value
            val (reminderEpochMillis, endEpochMillis) =
                if (isAllDay) {
                    val startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val targetEndDate = _endDate.value ?: date
                    val resolvedEndDate = if (targetEndDate.isBefore(date)) date else targetEndDate
                    val endMillis =
                        resolvedEndDate
                            .atTime(23, 59, 59)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    startMillis to endMillis
                } else {
                    val start = _startTime.value
                    val end = _endTime.value
                    val startMillis =
                        date
                            .atTime(start)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    val targetEndDate = _endDate.value ?: if (end < start) date.plusDays(1) else date
                    val resolvedEndDate = if (targetEndDate.isBefore(date)) date else targetEndDate
                    val endMillis =
                        resolvedEndDate
                            .atTime(end)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    startMillis to endMillis
                }

            val bodyText = _notes.value.trim()

            val activeReminders =
                if (_reminders.value.isNotEmpty()) {
                    _reminders.value.map { r ->
                        if (r.endAt != null) {
                            r.copy(endAt = endEpochMillis, recurrence = _recurrence.value)
                        } else {
                            r
                        }
                    }
                } else if (_reminderAtStart.value) {
                    listOf(
                        NoteReminder(
                            reminderAt = reminderEpochMillis,
                            recurrence = _recurrence.value,
                            endAt = endEpochMillis,
                        ),
                    )
                } else {
                    emptyList()
                }

            val options =
                NoteOptions(
                    reminderAt =
                        if (activeReminders.isNotEmpty()) {
                            activeReminders.minByOrNull { it.reminderAt }?.reminderAt ?: reminderEpochMillis
                        } else {
                            reminderEpochMillis
                        },
                    endAt = endEpochMillis,
                    isAllDay = isAllDay,
                    shoulderId = _selectedShoulderId.value,
                    recurrence = _recurrence.value,
                    reminders = activeReminders,
                    importance = _importance.value,
                    visibility = _visibility.value,
                    pictureUri = _pictureUri.value,
                    pictureHeroFraming = existingHeroFraming,
                    locked = existingLocked,
                    actions = _actions.value,
                    iconKey = _iconKey.value,
                )

            return try {
                val savedId =
                    if (eventId == null) {
                        val newId =
                            noteRepository.createNote(
                                title = trimmedTitle,
                                body = bodyText,
                                colorIndex = _colorIndex.value,
                                options = options,
                            )
                        for (att in _pendingAttachments.value) {
                            noteRepository.addAttachment(newId, att.uriString, att.displayName, att.mimeType)
                        }
                        newId
                    } else {
                        noteRepository.updateNote(
                            id = eventId,
                            title = trimmedTitle,
                            body = bodyText,
                            colorIndex = _colorIndex.value,
                            options = options,
                        )
                        eventId
                    }
                _isSaving.value = false
                Result.success(savedId)
            } catch (e: Exception) {
                _isSaving.value = false
                _saveError.value = e.message ?: "Failed to save event"
                Result.failure(e)
            }
        }

        companion object {
            fun parseEventBodyAndTimes(
                body: String,
                reminderAt: Long?,
            ): ParsedEventData {
                val defaultStart =
                    if (reminderAt != null && reminderAt > 0L) {
                        Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    } else {
                        LocalTime.of(9, 0)
                    }
                val defaultEnd = defaultStart.plusMinutes(30)

                val trimmedBody = body.trim()
                if (trimmedBody.contains("[All Day]", ignoreCase = true)) {
                    val notes = trimmedBody.replace("[All Day]", "").trim()
                    return ParsedEventData(
                        isAllDay = true,
                        startTime = defaultStart,
                        endTime = defaultEnd,
                        notes = notes,
                    )
                }

                val lines = trimmedBody.lines()
                val firstLine = lines.firstOrNull()?.trim().orEmpty()
                val rangeRegex =
                    Regex(
                        """^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""",
                        RegexOption.IGNORE_CASE,
                    )
                val match = rangeRegex.find(firstLine)
                if (match != null) {
                    val (h1Str, m1Str, ampm1, h2Str, m2Str, ampm2) = match.destructured
                    var h1 = h1Str.toIntOrNull() ?: 9
                    val m1 = m1Str.toIntOrNull() ?: 0
                    var h2 = h2Str.toIntOrNull() ?: 10
                    val m2 = m2Str.toIntOrNull() ?: 0

                    val p2 = ampm2.lowercase(Locale.getDefault())
                    val p1 = if (ampm1.isNotBlank()) ampm1.lowercase(Locale.getDefault()) else p2

                    if (p1 == "pm" && h1 < 12) h1 += 12
                    if (p1 == "am" && h1 == 12) h1 = 0
                    if (p2 == "pm" && h2 < 12) h2 += 12
                    if (p2 == "am" && h2 == 12) h2 = 0

                    val start = LocalTime.of(h1.coerceIn(0, 23), m1.coerceIn(0, 59))
                    val end = LocalTime.of(h2.coerceIn(0, 23), m2.coerceIn(0, 59))
                    val notes = lines.drop(1).joinToString("\n").trim()
                    return ParsedEventData(
                        isAllDay = false,
                        startTime = start,
                        endTime = end,
                        notes = notes,
                    )
                }

                return ParsedEventData(
                    isAllDay = false,
                    startTime = defaultStart,
                    endTime = defaultEnd,
                    notes = trimmedBody,
                )
            }

            fun cleanLegacyTimeFromBody(body: String): String {
                val trimmed = body.trim()
                if (trimmed.contains("[All Day]", ignoreCase = true)) {
                    return trimmed.replace("[All Day]", "").trim()
                }
                val lines = trimmed.lines()
                val firstLine = lines.firstOrNull()?.trim().orEmpty()
                val rangeRegex =
                    Regex(
                        """^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""",
                        RegexOption.IGNORE_CASE,
                    )
                if (rangeRegex.matches(firstLine)) {
                    return lines.drop(1).joinToString("\n").trim()
                }
                return trimmed
            }
        }
    }

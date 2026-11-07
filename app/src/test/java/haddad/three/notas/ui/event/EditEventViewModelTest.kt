package haddad.three.notas.ui.event

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteReminder
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.Visibility
import haddad.three.notas.ui.nav.Routes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class EditEventViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val insertedNotes = mutableListOf<NoteEntity>()
    private val updatedNotes = mutableListOf<NoteEntity>()
    private val insertedAttachments = mutableListOf<NoteAttachmentEntity>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        insertedNotes.clear()
        updatedNotes.clear()
        insertedAttachments.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(): NoteRepository {
        val fakeNoteDao =
            object : haddad.three.notas.data.BaseFakeNoteDao() {
                override suspend fun get(id: Long): NoteWithItems? {
                    val found = insertedNotes.firstOrNull { it.id == id } ?: return null
                    val atts = insertedAttachments.filter { it.noteId == id }
                    return NoteWithItems(found, emptyList(), attachments = atts)
                }

                override suspend fun getByUuid(uuid: String): NoteWithItems? {
                    val found = insertedNotes.firstOrNull { it.uuid == uuid } ?: return null
                    val atts = insertedAttachments.filter { it.noteId == found.id }
                    return NoteWithItems(found, emptyList(), attachments = atts)
                }

                override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? =
                    insertedNotes.firstOrNull { it.uuid == uuid }

                override suspend fun insert(note: NoteEntity): Long {
                    val id = if (note.id > 0) note.id else (insertedNotes.size + 1).toLong()
                    val assigned = note.copy(id = id)
                    insertedNotes.add(assigned)
                    return id
                }

                override suspend fun update(note: NoteEntity) {
                    updatedNotes.add(note)
                    val idx = insertedNotes.indexOfFirst { it.id == note.id }
                    if (idx >= 0) {
                        insertedNotes[idx] = note
                    }
                }
            }

        val fakeChecklistDao = object : haddad.three.notas.data.BaseFakeChecklistItemDao() {}

        val fakeAttachmentDao =
            object : haddad.three.notas.data.BaseFakeAttachmentDao() {
                override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> =
                    insertedAttachments.filter { it.noteId == noteId }

                override suspend fun insert(attachment: NoteAttachmentEntity): Long {
                    val id = (insertedAttachments.size + 1).toLong()
                    val entity = attachment.copy(id = id)
                    insertedAttachments.add(entity)
                    return id
                }

                override suspend fun getById(id: Long): NoteAttachmentEntity? =
                    insertedAttachments.firstOrNull { it.id == id }

                override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? =
                    insertedAttachments.firstOrNull { it.uuid == uuid }

                override suspend fun deleteById(id: Long) {
                    insertedAttachments.removeAll { it.id == id }
                }

                override suspend fun deleteForNote(noteId: Long) {
                    insertedAttachments.removeAll { it.noteId == noteId }
                }
            }

        return NoteRepository(
            noteDao = fakeNoteDao,
            itemDao = fakeChecklistDao,
            attachmentDao = fakeAttachmentDao,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
    }

    private fun createShoulderRepository(): ShoulderRepository {
        val shoulders = mutableListOf<ShoulderEntity>()
        val shouldersFlow = MutableStateFlow<List<ShoulderEntity>>(emptyList())
        val fakeShoulderDao =
            object : haddad.three.notas.data.BaseFakeShoulderDao() {
                override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = shouldersFlow

                override suspend fun allShoulders(): List<ShoulderEntity> = shoulders
                override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.filter { it.ownerId == ownerId }

                override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = shouldersFlow

                override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders.firstOrNull { it.id == id }

                override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders.firstOrNull { it.uuid == uuid }

                override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.firstOrNull { it.normalizedName == normalizedName }

                override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()

                override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
                    val id = (shoulders.size + 1).toLong()
                    val assigned = shoulder.copy(id = id)
                    shoulders.add(assigned)
                    shouldersFlow.value = shoulders.toList()
                    return id
                }

                override suspend fun updateShoulder(shoulder: ShoulderEntity) {
                    val idx = shoulders.indexOfFirst { it.id == shoulder.id }
                    if (idx >= 0) {
                        shoulders[idx] = shoulder
                        shouldersFlow.value = shoulders.toList()
                    }
                }

                override suspend fun deleteShoulder(shoulder: ShoulderEntity) {
                    shoulders.removeAll { it.id == shoulder.id }
                    shouldersFlow.value = shoulders.toList()
                }

                override suspend fun setShoulderForNote(
                    noteId: Long,
                    shoulderId: Long?,
                ) = Unit
            }
        return ShoulderRepository(fakeShoulderDao)
    }

    @Test
    fun testCreateTimedEvent_success() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val date = LocalDate.of(2026, 10, 15)
            val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val handle = SavedStateHandle(mapOf(Routes.ARG_DATE_MILLIS to dateMillis))

            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                    shoulderRepository = createShoulderRepository(),
                )
            advanceUntilIdle()

            assertFalse(viewModel.isEditMode)
            assertEquals(date, viewModel.selectedDate.value)

            viewModel.setTitle("Team Standup")
            viewModel.setNotes("Discuss Q4 goals")
            viewModel.setStartTime(LocalTime.of(10, 0))
            viewModel.setEndTime(LocalTime.of(10, 30))

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            assertEquals(1, insertedNotes.size)
            val note = insertedNotes.first()
            assertEquals("Team Standup", note.title)
            assertEquals("Discuss Q4 goals", note.body)
            assertEquals(NoteKind.NOTE, note.kind)
            assertNotNull(note.reminderAt)
            assertNotNull(note.endAt)
            assertFalse(note.isAllDay)
        }

    @Test
    fun testCreateAllDayEvent_encodesAllDay() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val handle = SavedStateHandle()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("Annual Conference")
            viewModel.setAllDay(true)
            viewModel.setNotes("Bring badges")

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val note = insertedNotes.first()
            assertEquals("Annual Conference", note.title)
            assertEquals("Bring badges", note.body)
            assertTrue(note.isAllDay)
        }

    @Test
    fun testValidation_blankTitleFails() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("   ")
            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertNotNull(viewModel.saveError.value)
            assertEquals(0, insertedNotes.size)
        }

    @Test
    fun testValidation_sameStartAndEndTimeFails() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("Quick Sync")
            viewModel.setStartTime(LocalTime.of(14, 0))
            viewModel.setEndTime(LocalTime.of(14, 0))
            viewModel.setAllDay(false)

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertTrue(viewModel.saveError.value?.contains("same") == true)
            assertEquals(0, insertedNotes.size)
        }

    @Test
    fun testEditMode_loadsExistingEventData() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val testDate = LocalDate.of(2026, 11, 20)
            val startTime = LocalTime.of(15, 0)
            val reminderAt =
                testDate
                    .atTime(startTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val existingNote =
                NoteEntity(
                    id = 42L,
                    kind = NoteKind.NOTE,
                    title = "Design Review",
                    body = "3:00 PM – 4:00 PM\nReview mobile mockups",
                    colorIndex = 0,
                    trashed = false,
                    reminderAt = reminderAt,
                    shoulderId = 7L,
                    importance = Importance.HIGH,
                    visibility = Visibility.DEFAULT,
                    createdAt = reminderAt,
                    updatedAt = reminderAt,
                )
            insertedNotes.add(existingNote)

            val handle = SavedStateHandle(mapOf(Routes.ARG_ID to 42L))
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            assertTrue(viewModel.isEditMode)
            assertEquals("Design Review", viewModel.title.value)
            assertEquals("Review mobile mockups", viewModel.notes.value)
            assertEquals(testDate, viewModel.selectedDate.value)
            assertEquals(LocalTime.of(15, 0), viewModel.startTime.value)
            assertEquals(LocalTime.of(16, 0), viewModel.endTime.value)
            assertFalse(viewModel.isAllDay.value)
            assertEquals(7L, viewModel.selectedShoulderId.value)
            assertEquals(Importance.HIGH, viewModel.importance.value)
        }

    @Test
    fun testEditMode_updateEventSavesChanges() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val existingNote =
                NoteEntity(
                    id = 99L,
                    kind = NoteKind.NOTE,
                    title = "Original Title",
                    body = "9:00 AM – 10:00 AM\nOld notes",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                )
            insertedNotes.add(existingNote)

            val handle = SavedStateHandle(mapOf(Routes.ARG_ID to 99L))
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("Updated Title")
            viewModel.setNotes("New notes content")
            viewModel.setImportance(Importance.CRITICAL)

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            assertEquals(99L, result.getOrNull())
            val updated = insertedNotes.first { it.id == 99L }
            assertEquals("Updated Title", updated.title)
            assertEquals("New notes content", updated.body)
            assertEquals(Importance.CRITICAL, updated.importance)
        }

    @Test
    fun testIndependentEndAtAndBodyIntegrity() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val handle = SavedStateHandle()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            val userDescription = "Project sync with team:\n- Review roadmap\n- Discuss blockers"
            viewModel.setTitle("Sprint Review")
            viewModel.setNotes(userDescription)
            viewModel.setStartTime(LocalTime.of(14, 0))
            viewModel.setEndTime(LocalTime.of(15, 30))

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val note = insertedNotes.first()

            val initialEndAt = note.endAt!!

            // Verify body is strictly the user description, without any time text appended
            assertEquals(userDescription, note.body)
            assertNotNull(note.reminderAt)
            assertNotNull(note.endAt)
            assertTrue(initialEndAt > note.reminderAt!!)

            // Reload via new ViewModel in edit mode
            val editHandle = SavedStateHandle(mapOf(Routes.ARG_ID to note.id))
            val editViewModel =
                EditEventViewModel(
                    savedStateHandle = editHandle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            // Verify restored values
            assertEquals("Sprint Review", editViewModel.title.value)
            assertEquals(userDescription, editViewModel.notes.value)
            assertEquals(LocalTime.of(14, 0), editViewModel.startTime.value)
            assertEquals(LocalTime.of(15, 30), editViewModel.endTime.value)
            assertFalse(editViewModel.isAllDay.value)

            // Update only end time
            editViewModel.setEndTime(LocalTime.of(16, 0))
            val editResult = editViewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(editResult.isSuccess)
            val updatedNote = insertedNotes.first { it.id == note.id }
            assertEquals(userDescription, updatedNote.body)
            assertEquals("Sprint Review", updatedNote.title)
            assertTrue(updatedNote.endAt!! > initialEndAt)
        }

    @Test
    fun testParseEventBodyAndTimes_allDay() {
        val parsed = EditEventViewModel.parseEventBodyAndTimes("[All Day]\nMeeting with Alex", null)
        assertTrue(parsed.isAllDay)
        assertEquals("Meeting with Alex", parsed.notes)
    }

    @Test
    fun testParseEventBodyAndTimes_timeRange() {
        val parsed =
            EditEventViewModel.parseEventBodyAndTimes(
                "9:00 AM – 10:30 AM\nDetailed agenda item",
                null,
            )
        assertFalse(parsed.isAllDay)
        assertEquals(LocalTime.of(9, 0), parsed.startTime)
        assertEquals(LocalTime.of(10, 30), parsed.endTime)
        assertEquals("Detailed agenda item", parsed.notes)
    }

    @Test
    fun testShoulderSelectionAndCrud() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val shoulderRepo = createShoulderRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                    shoulderRepository = shoulderRepo,
                )
            advanceUntilIdle()

            viewModel.createShoulder("Work", "#4285F4")
            advanceUntilIdle()

            assertNotNull(viewModel.selectedShoulderId.value)

            viewModel.setSelectedShoulderId(null)
            assertEquals(null, viewModel.selectedShoulderId.value)
        }

    @Test
    fun testSetReminders_savesCustomReminders() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("Dentist Appointment")
            val customReminderMillis = 1791890000000L
            viewModel.setReminders(listOf(NoteReminder(reminderAt = customReminderMillis)))

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val note = insertedNotes.first()
            assertEquals(1, note.reminders.size)
            assertEquals(customReminderMillis, note.reminders.first().reminderAt)
        }

    @Test
    fun testLoadExistingEvent_populatesReminders() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val customReminderMillis = 1791895000000L
            val existingNote =
                NoteEntity(
                    id = 42L,
                    kind = NoteKind.NOTE,
                    title = "Conference Call",
                    body = "10:00 AM – 11:00 AM",
                    colorIndex = 1,
                    trashed = false,
                    createdAt = 1791880000000L,
                    updatedAt = 1791880000000L,
                    reminderAt = 1791890000000L,
                    reminders =
                        listOf(
                            NoteReminder(reminderAt = 1791890000000L),
                            NoteReminder(reminderAt = customReminderMillis),
                        ),
                )
            insertedNotes.add(existingNote)

            val handle = SavedStateHandle(mapOf(Routes.ARG_ID to 42L))
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            assertTrue(viewModel.isEditMode)
            assertEquals(2, viewModel.reminders.value.size)
            assertEquals(customReminderMillis, viewModel.reminders.value[1].reminderAt)
        }

    @Test
    fun testCreateEvent_defaultColorAndIcon() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                )
            advanceUntilIdle()

            assertEquals(8, viewModel.colorIndex.value)
            assertEquals("calendar_month", viewModel.iconKey.value)

            viewModel.setTitle("New Event")
            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val note = insertedNotes.first()
            assertEquals(8, note.colorIndex)
            assertEquals("calendar_month", note.iconKey)
        }

    @Test
    fun testCreateEvent_customColorAndIcon() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = SavedStateHandle(),
                    noteRepository = repo,
                )
            advanceUntilIdle()

            viewModel.setTitle("Birthday Party")
            viewModel.setColorIndex(3)
            viewModel.setIconKey("symbol:cake")

            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val note = insertedNotes.first()
            assertEquals(3, note.colorIndex)
            assertEquals("symbol:cake", note.iconKey)
        }

    @Test
    fun testEditEvent_loadsExistingColorAndIcon() =
        runTest(testDispatcher) {
            val repo = createRepository()
            val existingNote =
                NoteEntity(
                    id = 55L,
                    kind = NoteKind.NOTE,
                    title = "Gym Session",
                    body = "07:00 AM – 08:00 AM",
                    colorIndex = 5,
                    iconKey = "fitness_center",
                    trashed = false,
                    createdAt = 1791880000000L,
                    updatedAt = 1791880000000L,
                    reminderAt = 1791890000000L,
                )
            insertedNotes.add(existingNote)

            val handle = SavedStateHandle(mapOf(Routes.ARG_ID to 55L))
            val viewModel =
                EditEventViewModel(
                    savedStateHandle = handle,
                    noteRepository = repo,
                )
            advanceUntilIdle()

            assertEquals(5, viewModel.colorIndex.value)
            assertEquals("fitness_center", viewModel.iconKey.value)

            viewModel.setColorIndex(6)
            viewModel.setIconKey("pool")
            val result = viewModel.saveEvent()
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val updated = updatedNotes.first()
            assertEquals(6, updated.colorIndex)
            assertEquals("pool", updated.iconKey)
        }
}

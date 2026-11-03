package haddad.three.notas.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteReminder

class ReminderDismissReceiverTest {
    @Test
    fun dismissedReminderRepostsLatestDueReminder() {
        val now = 2_000L
        val note =
            note(
                reminderAt = 3_000L,
                reminders =
                    listOf(
                        NoteReminder(reminderAt = 3_000L),
                        NoteReminder(reminderAt = 1_000L),
                    ),
            )

        assertEquals(1, latestDueReminderIndex(note, now))
    }

    @Test
    fun dismissedReminderDoesNotRepostWhenNoReminderIsDue() {
        val note =
            note(
                reminderAt = 3_000L,
                reminders = listOf(NoteReminder(reminderAt = 3_000L)),
            )

        assertNull(latestDueReminderIndex(note, now = 2_000L))
    }

    @Test
    fun dismissedReminderPrefersNewerDueReminderOverOlderDueReminder() {
        val note =
            note(
                reminderAt = 1_000L,
                reminders =
                    listOf(
                        NoteReminder(reminderAt = 1_000L),
                        NoteReminder(reminderAt = 1_500L),
                    ),
            )

        assertEquals(1, latestDueReminderIndex(note, now = 2_000L))
    }

    private fun note(
        reminderAt: Long?,
        reminders: List<NoteReminder>,
    ): NoteEntity =
        NoteEntity(
            id = 1L,
            kind = NoteKind.NOTE,
            title = "Note",
            body = "",
            colorIndex = 0,
            trashed = false,
            createdAt = 0L,
            updatedAt = 0L,
            reminderAt = reminderAt,
            reminders = reminders,
        )
}

package haddad.three.notas.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.di.ApplicationScope
import javax.inject.Inject

@AndroidEntryPoint
class ReminderDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val keepUntilDone =
                    reminderPrefs
                        .snapshot()
                        .keepReminderNotificationsUntilDone
                if (!keepUntilDone) return@launch

                val noteWithItems = noteRepository.get(noteId) ?: return@launch
                val note = noteWithItems.note
                val reminderIndex = latestDueReminderIndex(note, System.currentTimeMillis()) ?: return@launch
                ReminderReceiver.showNotification(
                    context = context,
                    note = note,
                    items = noteWithItems.items,
                    reminderIndex = reminderIndex,
                    keepUntilDone = true,
                    onlyAlertOnce = true,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISSED = "haddad.three.notas.reminders.REMINDER_DISMISSED"
        const val EXTRA_NOTE_ID = "note_id"
    }
}

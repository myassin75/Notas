package haddad.three.notas.backup

import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import org.json.JSONObject

object SettingsBackup {
    const val KEY_THEME = "theme_prefs"
    const val KEY_NOTES_UI = "notes_ui_prefs"
    const val KEY_VIEW_OPTIONS = "view_options_prefs"
    const val KEY_LOCK = "lock_prefs"
    const val KEY_INTERACTION = "interaction_prefs"
    const val KEY_QUICK_CAPTURE = "quick_capture_prefs"
    const val KEY_REMINDER = "reminder_prefs"
    const val KEY_DEFAULT_NOTE = "default_note_prefs"
    const val KEY_UPDATE = "update_prefs"

    suspend fun exportJson(
        themePrefs: ThemePrefs,
        notesUiPrefs: NotesUiPrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        defaultNotePrefs: DefaultNotePrefs,
        updatePrefs: UpdatePrefs,
    ): JSONObject =
        JSONObject().apply {
            put(KEY_THEME, themePrefs.exportForBackup())
            put(KEY_NOTES_UI, notesUiPrefs.exportForBackup())
            put(KEY_VIEW_OPTIONS, viewOptionsPrefs.exportForBackup())
            put(KEY_LOCK, lockPrefs.exportForBackup())
            put(KEY_INTERACTION, interactionPrefs.exportForBackup())
            put(KEY_QUICK_CAPTURE, quickCapturePrefs.exportForBackup())
            put(KEY_REMINDER, reminderPrefs.exportForBackup())
            put(KEY_DEFAULT_NOTE, defaultNotePrefs.exportForBackup())
            put(KEY_UPDATE, updatePrefs.exportForBackup())
        }

    suspend fun importJson(
        root: JSONObject?,
        themePrefs: ThemePrefs,
        notesUiPrefs: NotesUiPrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        defaultNotePrefs: DefaultNotePrefs,
        updatePrefs: UpdatePrefs,
    ) {
        if (root == null) return
        val themeJson = root.optJSONObject(KEY_THEME)
        themePrefs.importFromBackup(themeJson)
        notesUiPrefs.importFromBackup(root.optJSONObject(KEY_NOTES_UI), themeJson)
        viewOptionsPrefs.importFromBackup(root.optJSONObject(KEY_VIEW_OPTIONS))
        lockPrefs.importFromBackup(root.optJSONObject(KEY_LOCK))
        interactionPrefs.importFromBackup(root.optJSONObject(KEY_INTERACTION))
        quickCapturePrefs.importFromBackup(root.optJSONObject(KEY_QUICK_CAPTURE))
        reminderPrefs.importFromBackup(root.optJSONObject(KEY_REMINDER))
        defaultNotePrefs.importFromBackup(root.optJSONObject(KEY_DEFAULT_NOTE))
        updatePrefs.importFromBackup(root.optJSONObject(KEY_UPDATE))
    }
}

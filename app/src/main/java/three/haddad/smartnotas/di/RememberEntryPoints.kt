package three.haddad.smartnotas.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import three.haddad.smartnotas.data.BackupIo
import three.haddad.smartnotas.data.BackupPrefs
import three.haddad.smartnotas.data.DefaultNotePrefs
import three.haddad.smartnotas.data.DevModePrefs
import three.haddad.smartnotas.data.InteractionPrefs
import three.haddad.smartnotas.data.LockPrefs
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NotesUiPrefs
import three.haddad.smartnotas.data.OnboardingPrefs
import three.haddad.smartnotas.data.QuickCapturePrefs
import three.haddad.smartnotas.data.ReminderPrefs
import three.haddad.smartnotas.data.ThemePrefs
import three.haddad.smartnotas.data.UpdatePrefs
import three.haddad.smartnotas.data.ViewOptionsPrefs
import three.haddad.smartnotas.reminders.ReminderScheduler
import three.haddad.smartnotas.update.AppReviewLauncher
import three.haddad.smartnotas.update.PlayInAppUpdateProgressController
import three.haddad.smartnotas.update.PlayInAppUpdateStarter
import three.haddad.smartnotas.update.PlayUpdateSessionHandle
import three.haddad.smartnotas.update.RememberUpdateState
import three.haddad.smartnotas.update.UpdateAvailableNotifier
import three.haddad.smartnotas.update.UpdateCheckWorkScheduler
import three.haddad.smartnotas.widget.NotesWidgetUpdater

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsDependenciesEntryPoint {
    fun devModePrefs(): DevModePrefs

    fun lockPrefs(): LockPrefs

    fun interactionPrefs(): InteractionPrefs

    fun quickCapturePrefs(): QuickCapturePrefs

    fun reminderPrefs(): ReminderPrefs

    fun defaultNotePrefs(): DefaultNotePrefs

    fun backupPrefs(): BackupPrefs

    fun backupIo(): BackupIo

    fun cloudBackupManager(): three.haddad.smartnotas.data.backup.CloudBackupManager

    fun authRepository(): three.haddad.smartnotas.data.auth.AuthRepository

    fun themePrefs(): ThemePrefs

    fun notesUiPrefs(): NotesUiPrefs

    fun viewOptionsPrefs(): ViewOptionsPrefs

    fun noteRepository(): NoteRepository

    fun updatePrefs(): UpdatePrefs

    fun playInAppUpdateStarter(): PlayInAppUpdateStarter

    fun playInAppUpdateProgressController(): PlayInAppUpdateProgressController

    fun playUpdateSessionHandle(): PlayUpdateSessionHandle

    fun rememberUpdateState(): RememberUpdateState

    fun updateAvailableNotifier(): UpdateAvailableNotifier

    fun updateCheckWorkScheduler(): UpdateCheckWorkScheduler

    fun appReviewLauncher(): AppReviewLauncher
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotesWidgetEntryPoint {
    fun noteRepository(): NoteRepository

    fun notesWidgetUpdater(): NotesWidgetUpdater

    fun viewOptionsPrefs(): ViewOptionsPrefs
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevOptionsDependenciesEntryPoint {
    fun devModePrefs(): DevModePrefs

    fun onboardingPrefs(): OnboardingPrefs

    fun updatePrefs(): UpdatePrefs

    fun noteRepository(): NoteRepository

    fun notesWidgetUpdater(): NotesWidgetUpdater

    fun reminderScheduler(): ReminderScheduler

    fun reminderPrefs(): ReminderPrefs

    fun defaultNotePrefs(): DefaultNotePrefs

    fun themePrefs(): ThemePrefs

    fun notesUiPrefs(): NotesUiPrefs

    fun viewOptionsPrefs(): ViewOptionsPrefs

    fun interactionPrefs(): InteractionPrefs

    fun quickCapturePrefs(): QuickCapturePrefs

    fun lockPrefs(): LockPrefs

    fun backupPrefs(): BackupPrefs

    fun rememberUpdateState(): RememberUpdateState
}

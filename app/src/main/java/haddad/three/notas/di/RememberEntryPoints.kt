package haddad.three.notas.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.DevModePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.OnboardingPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.reminders.ReminderScheduler
import haddad.three.notas.update.AppReviewLauncher
import haddad.three.notas.update.PlayInAppUpdateProgressController
import haddad.three.notas.update.PlayInAppUpdateStarter
import haddad.three.notas.update.PlayUpdateSessionHandle
import haddad.three.notas.update.RememberUpdateState
import haddad.three.notas.update.UpdateAvailableNotifier
import haddad.three.notas.update.UpdateCheckWorkScheduler
import haddad.three.notas.widget.NotesWidgetUpdater

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsDependenciesEntryPoint {
    fun devModePrefs(): DevModePrefs

    fun lockPrefs(): LockPrefs

    fun interactionPrefs(): InteractionPrefs

    fun quickCapturePrefs(): QuickCapturePrefs

    fun reminderPrefs(): ReminderPrefs

    fun defaultNotePrefs(): DefaultNotePrefs

    fun cloudBackupManager(): haddad.three.notas.data.backup.CloudBackupManager

    fun googleDriveBackupProvider(): haddad.three.notas.data.drive.backup.GoogleDriveBackupProvider

    fun googleDriveAuthDelegate(): haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate

    fun driveAuthPreferences(): haddad.three.notas.data.drive.auth.DriveAuthPreferences

    fun authRepository(): haddad.three.notas.data.auth.AuthRepository

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

    fun themePrefs(): haddad.three.notas.data.ThemePrefs
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

    fun rememberUpdateState(): RememberUpdateState
}

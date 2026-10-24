package three.haddad.smartnotas.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import three.haddad.smartnotas.backup.BackupExportCoordinator
import three.haddad.smartnotas.backup.NoteBackupDirtyTracker
import three.haddad.smartnotas.data.AppMediaStorage
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
import three.haddad.smartnotas.data.RememberDatabase
import three.haddad.smartnotas.data.ReminderPrefs
import three.haddad.smartnotas.data.SearchPreferences
import three.haddad.smartnotas.data.ShoulderRepository
import three.haddad.smartnotas.data.ThemePrefs
import three.haddad.smartnotas.data.UpdatePrefs
import three.haddad.smartnotas.data.ViewOptionsPrefs
import three.haddad.smartnotas.googletasks.GoogleTasksApi
import three.haddad.smartnotas.googletasks.GoogleTasksImportPrefs
import three.haddad.smartnotas.googletasks.GoogleTasksImporter
import three.haddad.smartnotas.googletasks.GoogleTasksRepository
import three.haddad.smartnotas.reminders.ReminderScheduler
import three.haddad.smartnotas.ui.lock.AppLockSession
import three.haddad.smartnotas.update.UpdateCheckWorkScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RememberModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): RememberDatabase = RememberDatabase.build(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun provideDevModePrefs(
        @ApplicationContext context: Context,
    ): DevModePrefs = DevModePrefs(context)

    @Provides
    @Singleton
    fun provideReminderPrefs(
        @ApplicationContext context: Context,
    ): ReminderPrefs = ReminderPrefs(context)

    @Provides
    @Singleton
    fun provideDefaultNotePrefs(
        @ApplicationContext context: Context,
    ): DefaultNotePrefs = DefaultNotePrefs(context)

    @Provides
    @Singleton
    fun provideReminderScheduler(
        @ApplicationContext context: Context,
        reminderPrefs: ReminderPrefs,
    ): ReminderScheduler = ReminderScheduler(context, reminderPrefs)

    @Provides
    @Singleton
    fun provideThemePrefs(
        @ApplicationContext context: Context,
    ): ThemePrefs = ThemePrefs(context)

    @Provides
    @Singleton
    fun provideNotesUiPrefs(
        @ApplicationContext context: Context,
    ): NotesUiPrefs = NotesUiPrefs(context)

    @Provides
    @Singleton
    fun provideViewOptionsPrefs(
        @ApplicationContext context: Context,
    ): ViewOptionsPrefs = ViewOptionsPrefs(context)

    @Provides
    @Singleton
    fun provideSearchPreferences(
        @ApplicationContext context: Context,
    ): SearchPreferences = SearchPreferences(context)

    @Provides
    @Singleton
    fun provideShoulderRepository(
        database: RememberDatabase,
        tombstoneRecorder: three.haddad.smartnotas.data.sync.SyncTombstoneRecorder,
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
    ): ShoulderRepository =
        ShoulderRepository(
            shoulderDao = database.shoulderDao(),
            tombstoneRecorder = tombstoneRecorder,
            authRepository = authRepository,
        )

    @Provides
    @Singleton
    fun provideNoteRepository(
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        shoulderRepository: ShoulderRepository,
        appMediaStorage: AppMediaStorage,
        tombstoneRecorder: three.haddad.smartnotas.data.sync.SyncTombstoneRecorder,
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): NoteRepository =
        NoteRepository(
            noteDao = database.noteDao(),
            database = database,
            itemDao = database.checklistItemDao(),
            attachmentDao = database.attachmentDao(),
            scheduler = reminderScheduler,
            shoulderRepository = shoulderRepository,
            appMediaStorage = appMediaStorage,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher,
            tombstoneRecorder = tombstoneRecorder,
            authRepository = authRepository,
        )

    @Provides
    @Singleton
    fun provideLockPrefs(
        @ApplicationContext context: Context,
    ): LockPrefs = LockPrefs(context)

    @Provides
    @Singleton
    fun provideOnboardingPrefs(
        @ApplicationContext context: Context,
    ): OnboardingPrefs = OnboardingPrefs(context)

    @Provides
    @Singleton
    fun provideInteractionPrefs(
        @ApplicationContext context: Context,
    ): InteractionPrefs = InteractionPrefs(context)

    @Provides
    @Singleton
    fun provideBackupPrefs(
        @ApplicationContext context: Context,
    ): BackupPrefs = BackupPrefs(context)

    @Provides
    @Singleton
    fun provideQuickCapturePrefs(
        @ApplicationContext context: Context,
    ): QuickCapturePrefs = QuickCapturePrefs(context)

    @Provides
    @Singleton
    fun provideUpdatePrefs(
        @ApplicationContext context: Context,
    ): UpdatePrefs = UpdatePrefs(context)

    @Provides
    @Singleton
    fun provideBackupIo(
        @ApplicationContext context: Context,
        noteRepository: NoteRepository,
        themePrefs: ThemePrefs,
        notesUiPrefs: NotesUiPrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        defaultNotePrefs: DefaultNotePrefs,
        updatePrefs: UpdatePrefs,
        updateCheckWorkScheduler: UpdateCheckWorkScheduler,
    ): BackupIo =
        BackupIo(
            context = context,
            repository = noteRepository,
            themePrefs = themePrefs,
            notesUiPrefs = notesUiPrefs,
            viewOptionsPrefs = viewOptionsPrefs,
            lockPrefs = lockPrefs,
            interactionPrefs = interactionPrefs,
            backupPrefs = backupPrefs,
            quickCapturePrefs = quickCapturePrefs,
            reminderPrefs = reminderPrefs,
            defaultNotePrefs = defaultNotePrefs,
            updatePrefs = updatePrefs,
            updateCheckWorkScheduler = updateCheckWorkScheduler,
        )

    @Provides
    @Singleton
    fun provideGoogleTasksImportPrefs(
        @ApplicationContext context: Context,
    ): GoogleTasksImportPrefs = GoogleTasksImportPrefs(context)

    @Provides
    @Singleton
    fun provideGoogleTasksApi(): GoogleTasksApi = GoogleTasksApi()

    @Provides
    @Singleton
    fun provideGoogleTasksRepository(
        @ApplicationContext context: Context,
        api: GoogleTasksApi,
    ): GoogleTasksRepository =
        GoogleTasksRepository(
            context = context,
            api = api,
        )

    @Provides
    @Singleton
    fun provideGoogleTasksImporter(noteRepository: NoteRepository): GoogleTasksImporter = GoogleTasksImporter(repository = noteRepository)

    @Provides
    @Singleton
    fun provideNoteBackupDirtyTracker(
        @ApplicationContext context: Context,
    ): NoteBackupDirtyTracker = NoteBackupDirtyTracker(context)

    @Provides
    @Singleton
    fun provideBackupExportCoordinator(
        backupPrefs: BackupPrefs,
        backupIo: BackupIo,
        noteRepository: NoteRepository,
        @ApplicationScope applicationScope: CoroutineScope,
        noteBackupDirtyTracker: NoteBackupDirtyTracker,
    ): BackupExportCoordinator =
        BackupExportCoordinator(
            backupPrefs = backupPrefs,
            backupIo = backupIo,
            noteRepository = noteRepository,
            applicationScope = applicationScope,
            noteBackupDirtyTracker = noteBackupDirtyTracker,
        )

    @Provides
    @Singleton
    fun providePendingLaunchFlow(): MutableStateFlow<LaunchAction?> = MutableStateFlow(null)

    @Provides
    @Singleton
    @Suppress("ktlint:standard:function-expression-body")
    fun provideAppLockSession(): AppLockSession {
        return AppLockSession()
    }

    @Provides
    @Singleton
    fun provideAppStartupWarmup(
        @ApplicationScope applicationScope: CoroutineScope,
        noteRepository: NoteRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AppStartupWarmup =
        AppStartupWarmup(
            applicationScope = applicationScope,
            noteRepository = noteRepository,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        backendApiClient: three.haddad.smartnotas.data.auth.BackendApiClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): three.haddad.smartnotas.data.auth.AuthRepository =
        three.haddad.smartnotas.data.auth.FirebaseAuthRepository(
            context = context,
            backendApiClient = backendApiClient,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideSyncStateStore(
        @ApplicationContext context: Context,
    ): three.haddad.smartnotas.data.sync.SyncStateStore =
        three.haddad.smartnotas.data.sync.SyncStateStore(context)

    @Provides
    @Singleton
    fun provideSyncTombstoneRecorder(
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
        syncStateStore: three.haddad.smartnotas.data.sync.SyncStateStore,
    ): three.haddad.smartnotas.data.sync.SyncTombstoneRecorder =
        three.haddad.smartnotas.data.sync.DefaultSyncTombstoneRecorder(
            authRepository = authRepository,
            syncStateStore = syncStateStore,
        )

    @Provides
    @Singleton
    fun provideSyncApiClient(): three.haddad.smartnotas.data.sync.SyncApiClient =
        three.haddad.smartnotas.data.sync.SyncApiClient()

    @Provides
    @Singleton
    fun provideSyncEngine(
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
        syncStateStore: three.haddad.smartnotas.data.sync.SyncStateStore,
        syncApiClient: three.haddad.smartnotas.data.sync.SyncApiClient,
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): three.haddad.smartnotas.data.sync.SyncEngine =
        three.haddad.smartnotas.data.sync.SyncEngine(
            authRepository = authRepository,
            syncStateStore = syncStateStore,
            syncApiClient = syncApiClient,
            database = database,
            noteDao = database.noteDao(),
            itemDao = database.checklistItemDao(),
            attachmentDao = database.attachmentDao(),
            shoulderDao = database.shoulderDao(),
            scheduler = reminderScheduler,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideBackupApiClient(): three.haddad.smartnotas.data.backup.BackupApiClient =
        three.haddad.smartnotas.data.backup.BackupApiClient()

    @Provides
    @Singleton
    fun provideCloudBackupManager(
        @ApplicationContext context: Context,
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
        backupApiClient: three.haddad.smartnotas.data.backup.BackupApiClient,
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        noteRepository: NoteRepository,
        themePrefs: ThemePrefs,
        notesUiPrefs: NotesUiPrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        defaultNotePrefs: DefaultNotePrefs,
        updatePrefs: UpdatePrefs,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): three.haddad.smartnotas.data.backup.CloudBackupManager =
        three.haddad.smartnotas.data.backup.CloudBackupManager(
            context = context,
            authRepository = authRepository,
            backupApiClient = backupApiClient,
            noteDao = database.noteDao(),
            shoulderDao = database.shoulderDao(),
            itemDao = database.checklistItemDao(),
            attachmentDao = database.attachmentDao(),
            noteRepository = noteRepository,
            themePrefs = themePrefs,
            notesUiPrefs = notesUiPrefs,
            viewOptionsPrefs = viewOptionsPrefs,
            lockPrefs = lockPrefs,
            interactionPrefs = interactionPrefs,
            backupPrefs = backupPrefs,
            quickCapturePrefs = quickCapturePrefs,
            reminderPrefs = reminderPrefs,
            defaultNotePrefs = defaultNotePrefs,
            updatePrefs = updatePrefs,
            database = database,
            scheduler = reminderScheduler,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideAttachmentStorageManager(
        authRepository: three.haddad.smartnotas.data.auth.AuthRepository,
        backupApiClient: three.haddad.smartnotas.data.backup.BackupApiClient,
        database: RememberDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): three.haddad.smartnotas.data.backup.AttachmentStorageManager =
        three.haddad.smartnotas.data.backup.AttachmentStorageManager(
            authRepository = authRepository,
            backupApiClient = backupApiClient,
            attachmentDao = database.attachmentDao(),
            ioDispatcher = ioDispatcher,
        )
}

class AppStartupWarmup(
    private val applicationScope: CoroutineScope,
    private val noteRepository: NoteRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun start() {
        // Warm the first Home query while the system splash / initial composition is still
        // happening, so the first fullscreen Home frame is less likely to render before Room
        // has produced the active notes list.
        applicationScope.launch(ioDispatcher) {
            noteRepository.observeActive().first()
        }
    }
}

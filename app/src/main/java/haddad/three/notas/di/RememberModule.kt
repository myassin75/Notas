package haddad.three.notas.di

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
import kotlinx.coroutines.runBlocking
import haddad.three.notas.data.AppMediaStorage
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.DevModePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.OnboardingPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.SearchPreferences
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.sync.change.DefaultLocalChangeRecorder
import haddad.three.notas.data.sync.change.LocalChangeRecorder
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.conflict.FieldConflictResolver
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncHlcStateDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.snapshot.AttachmentPromotionRecoveryManager
import haddad.three.notas.data.sync.snapshot.SyncAttachmentPromotionDao
import haddad.three.notas.data.sync.snapshot.SyncSnapshotDao
import haddad.three.notas.data.sync.snapshot.SyncSnapshotManager
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.HlcStorage
import haddad.three.notas.data.sync.hlc.RoomHlcStorage
import haddad.three.notas.data.sync.ingestion.RemoteJournalIngestionEngine
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalRepository
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.journal.SyncSequenceDao
import haddad.three.notas.googletasks.GoogleTasksApi
import haddad.three.notas.googletasks.GoogleTasksImportPrefs
import haddad.three.notas.googletasks.GoogleTasksImporter
import haddad.three.notas.googletasks.GoogleTasksRepository
import haddad.three.notas.reminders.ReminderScheduler
import haddad.three.notas.ui.lock.AppLockSession
import haddad.three.notas.update.UpdateCheckWorkScheduler
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
        tombstoneRecorder: haddad.three.notas.data.sync.SyncTombstoneRecorder,
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        changeRecorder: LocalChangeRecorder,
    ): ShoulderRepository =
        ShoulderRepository(
            shoulderDao = database.shoulderDao(),
            tombstoneRecorder = tombstoneRecorder,
            authRepository = authRepository,
            database = database,
            changeRecorder = changeRecorder,
        )

    @Provides
    @Singleton
    fun provideNoteRepository(
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        shoulderRepository: ShoulderRepository,
        appMediaStorage: AppMediaStorage,
        tombstoneRecorder: haddad.three.notas.data.sync.SyncTombstoneRecorder,
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        changeRecorder: LocalChangeRecorder,
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
            changeRecorder = changeRecorder,
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
        backendApiClient: haddad.three.notas.data.auth.BackendApiClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): haddad.three.notas.data.auth.AuthRepository =
        haddad.three.notas.data.auth.FirebaseAuthRepository(
            context = context,
            backendApiClient = backendApiClient,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideSyncStateStore(
        @ApplicationContext context: Context,
    ): haddad.three.notas.data.sync.SyncStateStore =
        haddad.three.notas.data.sync.SyncStateStore(context)

    @Provides
    @Singleton
    fun provideSyncTombstoneRecorder(
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
    ): haddad.three.notas.data.sync.SyncTombstoneRecorder =
        haddad.three.notas.data.sync.DefaultSyncTombstoneRecorder(
            authRepository = authRepository,
            syncStateStore = syncStateStore,
        )

    @Provides
    @Singleton
    fun providePendingChangeDao(database: RememberDatabase): PendingChangeDao =
        database.pendingChangeDao()

    @Provides
    @Singleton
    fun provideSyncSequenceDao(database: RememberDatabase): SyncSequenceDao =
        database.syncSequenceDao()

    @Provides
    @Singleton
    fun provideSyncJournalDao(database: RememberDatabase): SyncJournalDao =
        database.syncJournalDao()

    @Provides
    @Singleton
    fun provideSyncInboxDao(database: RememberDatabase): haddad.three.notas.data.sync.transport.SyncInboxDao =
        database.syncInboxDao()

    @Provides
    @Singleton
    fun provideSyncWatermarkDao(database: RememberDatabase): haddad.three.notas.data.sync.transport.SyncWatermarkDao =
        database.syncWatermarkDao()

    @Provides
    @Singleton
    fun provideSyncAppliedOperationDao(database: RememberDatabase): SyncAppliedOperationDao =
        database.syncAppliedOperationDao()

    @Provides
    @Singleton
    fun provideSyncConflictMetadataDao(database: RememberDatabase): SyncConflictMetadataDao =
        database.syncConflictMetadataDao()

    @Provides
    @Singleton
    fun provideSyncTombstoneDao(database: RememberDatabase): SyncTombstoneDao =
        database.syncTombstoneDao()

    @Provides
    @Singleton
    fun provideSyncConflictRecordDao(database: RememberDatabase): SyncConflictRecordDao =
        database.syncConflictRecordDao()

    @Provides
    @Singleton
    fun provideSyncHlcStateDao(database: RememberDatabase): SyncHlcStateDao =
        database.syncHlcStateDao()

    @Provides
    @Singleton
    fun provideSyncSnapshotDao(database: RememberDatabase): SyncSnapshotDao =
        database.syncSnapshotDao()

    @Provides
    @Singleton
    fun provideSyncAttachmentPromotionDao(database: RememberDatabase): SyncAttachmentPromotionDao =
        database.syncAttachmentPromotionDao()

    @Provides
    @Singleton
    fun provideHlcStorage(hlcStateDao: SyncHlcStateDao): HlcStorage =
        RoomHlcStorage(hlcStateDao)

    @Provides
    @Singleton
    fun provideHlcClock(
        hlcStorage: HlcStorage,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
    ): HlcClock =
        HlcClock(
            nodeId = runBlocking { syncStateStore.getOrCreateDeviceId() },
            storage = hlcStorage,
        )

    @Provides
    @Singleton
    fun provideFieldConflictResolver(
        database: RememberDatabase,
    ): FieldConflictResolver =
        FieldConflictResolver(
            noteDao = database.noteDao(),
            checklistItemDao = database.checklistItemDao(),
            shoulderDao = database.shoulderDao(),
            attachmentDao = database.attachmentDao(),
            conflictMetadataDao = database.syncConflictMetadataDao(),
            tombstoneDao = database.syncTombstoneDao(),
            conflictRecordDao = database.syncConflictRecordDao(),
        )

    @Provides
    @Singleton
    fun provideRemoteJournalIngestionEngine(
        database: RememberDatabase,
        fieldConflictResolver: FieldConflictResolver,
        hlcClock: HlcClock,
        mekManager: MekManager,
    ): RemoteJournalIngestionEngine =
        RemoteJournalIngestionEngine(
            database = database,
            syncInboxDao = database.syncInboxDao(),
            syncWatermarkDao = database.syncWatermarkDao(),
            appliedOperationDao = database.syncAppliedOperationDao(),
            fieldConflictResolver = fieldConflictResolver,
            hlcClock = hlcClock,
            mekManager = mekManager,
        )

    @Provides
    @Singleton
    fun provideLocalChangeRecorder(
        pendingChangeDao: PendingChangeDao,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
    ): LocalChangeRecorder =
        DefaultLocalChangeRecorder(
            pendingChangeDao = pendingChangeDao,
            deviceIdProvider = { syncStateStore.getOrCreateDeviceId() },
        )

    @Provides
    @Singleton
    fun provideSyncJournalWriter(
        @ApplicationContext context: Context,
        database: RememberDatabase,
        pendingChangeDao: PendingChangeDao,
        sequenceDao: SyncSequenceDao,
        journalDao: SyncJournalDao,
        mekManager: MekManager,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
    ): SyncJournalWriter =
        SyncJournalWriter(
            context = context,
            database = database,
            pendingChangeDao = pendingChangeDao,
            sequenceDao = sequenceDao,
            journalDao = journalDao,
            mekManager = mekManager,
            deviceIdProvider = { syncStateStore.getOrCreateDeviceId() },
        )

    @Provides
    @Singleton
    fun provideSyncJournalRepository(
        @ApplicationContext context: Context,
        database: RememberDatabase,
        journalDao: SyncJournalDao,
        sequenceDao: SyncSequenceDao,
        pendingChangeDao: PendingChangeDao,
        mekManager: MekManager,
    ): SyncJournalRepository =
        SyncJournalRepository(
            context = context,
            database = database,
            journalDao = journalDao,
            sequenceDao = sequenceDao,
            pendingChangeDao = pendingChangeDao,
            mekManager = mekManager,
        )

    @Provides
    @Singleton
    fun provideAttachmentPromotionRecoveryManager(
        @ApplicationContext context: Context,
        promotionDao: SyncAttachmentPromotionDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AttachmentPromotionRecoveryManager = AttachmentPromotionRecoveryManager(
        context = context,
        promotionDao = promotionDao,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideSyncSnapshotManager(
        @ApplicationContext context: Context,
        database: RememberDatabase,
        snapshotDao: SyncSnapshotDao,
        promotionDao: SyncAttachmentPromotionDao,
        promotionRecoveryManager: AttachmentPromotionRecoveryManager,
        pendingChangeDao: PendingChangeDao,
        journalDao: SyncJournalDao,
        watermarkDao: haddad.three.notas.data.sync.transport.SyncWatermarkDao,
        appliedOperationDao: SyncAppliedOperationDao,
        conflictMetadataDao: SyncConflictMetadataDao,
        tombstoneDao: SyncTombstoneDao,
        conflictRecordDao: SyncConflictRecordDao,
        hlcClock: HlcClock,
        mekManager: MekManager,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SyncSnapshotManager = SyncSnapshotManager(
        context = context,
        database = database,
        snapshotDao = snapshotDao,
        promotionDao = promotionDao,
        promotionRecoveryManager = promotionRecoveryManager,
        pendingChangeDao = pendingChangeDao,
        journalDao = journalDao,
        watermarkDao = watermarkDao,
        appliedOperationDao = appliedOperationDao,
        conflictMetadataDao = conflictMetadataDao,
        tombstoneDao = tombstoneDao,
        conflictRecordDao = conflictRecordDao,
        noteDao = database.noteDao(),
        itemDao = database.checklistItemDao(),
        shoulderDao = database.shoulderDao(),
        attachmentDao = database.attachmentDao(),
        hlcClock = hlcClock,
        mekManager = mekManager,
        deviceIdProvider = { syncStateStore.getOrCreateDeviceId() },
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideSyncApiClient(): haddad.three.notas.data.sync.SyncApiClient =
        haddad.three.notas.data.sync.SyncApiClient()

    @Provides
    @Singleton
    fun provideSyncEngine(
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        syncStateStore: haddad.three.notas.data.sync.SyncStateStore,
        syncApiClient: haddad.three.notas.data.sync.SyncApiClient,
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): haddad.three.notas.data.sync.SyncEngine =
        haddad.three.notas.data.sync.SyncEngine(
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
    fun provideBackupApiClient(): haddad.three.notas.data.backup.BackupApiClient =
        haddad.three.notas.data.backup.BackupApiClient()

    @Provides
    @Singleton
    fun provideCloudBackupManager(
        @ApplicationContext context: Context,
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        backupApiClient: haddad.three.notas.data.backup.BackupApiClient,
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        noteRepository: NoteRepository,
        themePrefs: ThemePrefs,
        notesUiPrefs: NotesUiPrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        defaultNotePrefs: DefaultNotePrefs,
        updatePrefs: UpdatePrefs,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): haddad.three.notas.data.backup.CloudBackupManager =
        haddad.three.notas.data.backup.CloudBackupManager(
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
        authRepository: haddad.three.notas.data.auth.AuthRepository,
        backupApiClient: haddad.three.notas.data.backup.BackupApiClient,
        database: RememberDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): haddad.three.notas.data.backup.AttachmentStorageManager =
        haddad.three.notas.data.backup.AttachmentStorageManager(
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

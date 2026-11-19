package haddad.three.notas.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DefaultGoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.snapshot.SnapshotTransportService
import haddad.three.notas.data.sync.snapshot.SyncSnapshotDao
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import haddad.three.notas.data.sync.transport.JournalDiscoveryService
import haddad.three.notas.data.sync.transport.JournalDownloader
import haddad.three.notas.data.sync.transport.JournalUploader
import haddad.three.notas.data.sync.transport.SyncInboxDao
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GoogleDriveModule {
    @Provides
    @Singleton
    fun provideGoogleDriveAuthDelegate(): GoogleDriveAuthDelegate = DefaultGoogleDriveAuthDelegate

    @Provides
    @Singleton
    fun provideGoogleDriveApiClient(): GoogleDriveApiClient = GoogleDriveApiClient()

    @Provides
    @Singleton
    fun provideDriveAuthPreferences(
        @ApplicationContext context: Context,
    ): DriveAuthPreferences = DriveAuthPreferences(context)

    @Provides
    @Singleton
    fun provideNoteDao(database: RememberDatabase): NoteDao = database.noteDao()

    @Provides
    @Singleton
    fun provideShoulderDao(database: RememberDatabase): ShoulderDao = database.shoulderDao()

    @Provides
    @Singleton
    fun provideChecklistItemDao(database: RememberDatabase): ChecklistItemDao = database.checklistItemDao()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Provides
    @Singleton
    fun provideGoogleDriveSyncTransport(
        @ApplicationContext context: Context,
        driveApiClient: GoogleDriveApiClient,
        driveAuthDelegate: GoogleDriveAuthDelegate,
        drivePrefs: DriveAuthPreferences,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): GoogleDriveSyncTransport =
        GoogleDriveSyncTransport(
            context = context,
            driveApiClient = driveApiClient,
            driveAuthDelegate = driveAuthDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideJournalDiscoveryService(
        transport: GoogleDriveSyncTransport,
        watermarkDao: SyncWatermarkDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): JournalDiscoveryService =
        JournalDiscoveryService(
            transport = transport,
            watermarkDao = watermarkDao,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideJournalDownloader(
        @ApplicationContext context: Context,
        transport: GoogleDriveSyncTransport,
        inboxDao: SyncInboxDao,
        watermarkDao: SyncWatermarkDao,
        mekManager: MekManager,
        database: RememberDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): JournalDownloader =
        JournalDownloader(
            context = context,
            transport = transport,
            inboxDao = inboxDao,
            watermarkDao = watermarkDao,
            mekManager = mekManager,
            database = database,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideJournalUploader(
        journalDao: SyncJournalDao,
        pendingChangeDao: PendingChangeDao,
        transport: GoogleDriveSyncTransport,
        database: RememberDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): JournalUploader =
        JournalUploader(
            journalDao = journalDao,
            pendingChangeDao = pendingChangeDao,
            transport = transport,
            database = database,
            ioDispatcher = ioDispatcher,
        )

    @Provides
    @Singleton
    fun provideSnapshotTransportService(
        @ApplicationContext context: Context,
        transport: GoogleDriveSyncTransport,
        driveApiClient: GoogleDriveApiClient,
        database: RememberDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SnapshotTransportService =
        SnapshotTransportService(
            context = context,
            transport = transport,
            driveApiClient = driveApiClient,
            snapshotDao = database.syncSnapshotDao(),
            ioDispatcher = ioDispatcher,
        )
}

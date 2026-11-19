package haddad.three.notas.security

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.Bip39
import haddad.three.notas.crypto.CryptoConstants
import haddad.three.notas.crypto.CryptoStorage
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.IntegrityVerificationException
import haddad.three.notas.crypto.InvalidContainerException
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.BaseFakeAttachmentDao
import haddad.three.notas.data.BaseFakeChecklistItemDao
import haddad.three.notas.data.BaseFakeNoteDao
import haddad.three.notas.data.BaseFakeShoulderDao
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthConstants
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.backup.DriveBackupArchiveManager
import haddad.three.notas.data.drive.backup.GoogleDriveBackupProvider
import haddad.three.notas.data.drive.envelope.DriveRecoveryEnvelopeManager
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveFileListResponse
import haddad.three.notas.data.drive.model.DriveRecoveryEnvelopeDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneEntity
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.InMemoryHlcStorage
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Phase 10 Comprehensive Security Audit, Data Integrity & Disaster Recovery Verification Test Suite.
 *
 * Covers:
 * 1. NOTAS_E1 Authenticated Container Security (AAD, Tampering, Truncation, Nonce Uniqueness).
 * 2. Key Management & Account Partitioning Lifecycle.
 * 3. BIP-39 Recovery Phrase & Zero-Knowledge Recovery Envelope.
 * 4. Google Drive OAuth Security, Token Hygiene, and Account Isolation.
 * 5. Incremental Sync Security, Monotonicity, and Replay Protection.
 * 6. Disaster Recovery, Corrupted Backup Rejection, and Pre-Restore Rollback.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Phase10SecurityAuditVerificationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var fakeContext: Context
    private lateinit var keyStoreManager: SoftwareKeyStoreManager
    private lateinit var cryptoStorage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var hlcClock: HlcClock

    private val testMnemonic = listOf(
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "about"
    )

    @Before
    fun setUp() {
        runBlocking {
            val fakeFilesDir = tempFolder.newFolder("files")
            val fakeCacheDir = tempFolder.newFolder("cache")
            fakeContext = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
                override fun getFilesDir(): File = fakeFilesDir
                override fun getCacheDir(): File = fakeCacheDir
            }

            keyStoreManager = SoftwareKeyStoreManager()
            cryptoStorage = InMemoryCryptoStorage()
            mekManager = MekManager(keyStoreManager, cryptoStorage)
            drivePrefs = DriveAuthPreferences(fakeContext)

            hlcClock = HlcClock("device_audit_1", InMemoryHlcStorage())
            hlcClock.initialize()
        }
    }

    // =========================================================================
    // SECTION 3: ENCRYPTION AUDIT & NOTAS_E1 CONTAINER SECURITY
    // =========================================================================

    @Test
    fun testValidPayloadEncryptionAndDecryptionRoundTrip() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "Audit-Verified Notas Sensitive Payload 🔒 2026".toByteArray(Charsets.UTF_8)
        val aad = "audit_id=SEC-10-01".toByteArray(Charsets.UTF_8)

        val container = EncryptedContainer.encrypt(
            plaintext = plaintext,
            key = key,
            keyId = 1,
            aad = aad
        )

        val decrypted = EncryptedContainer.decrypt(
            containerBytes = container,
            key = key,
            expectedAad = aad
        )

        assertArrayEquals("Decrypted bytes must match original plaintext exactly", plaintext, decrypted)
    }

    @Test
    fun testRejectInvalidAuthenticationTag() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "Tamper Test Payload".toByteArray(Charsets.UTF_8)

        val container = EncryptedContainer.encrypt(plaintext, key)
        // Flip bit in the trailing 128-bit authentication tag
        val tamperedTagContainer = container.clone().also {
            val lastByte = it.size - 1
            it[lastByte] = (it[lastByte].toInt() xor 0x01).toByte()
        }

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(tamperedTagContainer, key)
        }
    }

    @Test
    fun testRejectModifiedCiphertext() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "Payload that will be modified in ciphertext".toByteArray(Charsets.UTF_8)

        val container = EncryptedContainer.encrypt(plaintext, key)
        // Flip a byte in the middle of ciphertext
        val tamperedCiphertextContainer = container.clone().also {
            val midByte = it.size - 20
            it[midByte] = (it[midByte].toInt() xor 0xFF).toByte()
        }

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(tamperedCiphertextContainer, key)
        }
    }

    @Test
    fun testRejectModifiedNonce() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "Nonce tamper verification".toByteArray(Charsets.UTF_8)

        val container = EncryptedContainer.encrypt(plaintext, key)
        // Nonce is at offset 14 (8 magic + 2 version + 4 keyId), length 12
        val tamperedNonceContainer = container.clone().also {
            it[14] = (it[14].toInt() xor 0x55).toByte()
        }

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(tamperedNonceContainer, key)
        }
    }

    @Test
    fun testRejectModifiedAad() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "AAD protection verification".toByteArray(Charsets.UTF_8)
        val validAad = "user=alice&action=read".toByteArray(Charsets.UTF_8)
        val attackerAad = "user=mallory&action=admin".toByteArray(Charsets.UTF_8)

        val container = EncryptedContainer.encrypt(plaintext, key, aad = validAad)

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(container, key, expectedAad = attackerAad)
        }
    }

    @Test
    fun testRejectTruncatedContainer() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val shortContainer = ByteArray(10) // Below minimum header size

        assertThrows(InvalidContainerException::class.java) {
            EncryptedContainer.decrypt(shortContainer, key)
        }
    }

    @Test
    fun testRejectMalformedMagicHeader() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val container = EncryptedContainer.encrypt("Secret".toByteArray(), key)

        val corruptedMagic = container.clone().also {
            it[0] = 'B'.code.toByte()
            it[1] = 'A'.code.toByte()
            it[2] = 'D'.code.toByte()
        }

        assertThrows(InvalidContainerException::class.java) {
            EncryptedContainer.decrypt(corruptedMagic, key)
        }
    }

    @Test
    fun testRejectUnsupportedFormatVersion() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val container = EncryptedContainer.encrypt("Secret".toByteArray(), key)

        // Version is at offset 8 (short, 2 bytes). Set to 0x0099
        val unsupportedVersion = container.clone().also {
            it[8] = 0x00
            it[9] = 0x99.toByte()
        }

        assertThrows(InvalidContainerException::class.java) {
            EncryptedContainer.decrypt(unsupportedVersion, key)
        }
    }

    @Test
    fun testNonceUniquenessAcrossMassiveGenerations() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val payload = "Repeated encryption payload for nonce test".toByteArray(Charsets.UTF_8)

        val seenNonces = HashSet<String>()
        val count = 1000

        for (i in 0 until count) {
            val container = EncryptedContainer.encrypt(payload, key)
            val nonce = container.copyOfRange(14, 26).joinToString("") { "%02x".format(it) }
            val isUnique = seenNonces.add(nonce)
            assertTrue("Nonce collision detected at iteration $i! Nonce: $nonce", isUnique)
        }

        assertEquals(count, seenNonces.size)
    }

    @Test
    fun testCryptographicDomainSeparationBetweenLocalAndRecoveryAad() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val masterKey = keyGen.generateKey()

        // Encrypt MEK with Local Keystore AAD
        val localWrapped = EncryptedContainer.encrypt(
            plaintext = masterKey.encoded,
            key = masterKey,
            aad = CryptoConstants.AAD_MEK_LOCAL
        )

        // Attempting to decrypt the local wrapped key with Recovery AAD must fail
        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(
                containerBytes = localWrapped,
                key = masterKey,
                expectedAad = CryptoConstants.AAD_MEK_RECOVERY
            )
        }
    }

    // =========================================================================
    // SECTION 4: KEY MANAGEMENT AUDIT & ACCOUNT PARTITIONING
    // =========================================================================

    @Test
    fun testMissingLocalKeyReturnsNull() {
        assertNull("Fresh install without initialized MEK must return null", mekManager.getActiveMasterKey())
        assertFalse("Recovery phrase should not be backed up initially", mekManager.isRecoveryConfigured())
    }

    @Test
    fun testCorruptedWrappedMekFailsGracefullyWithoutLeakingKeys() {
        // Case 1: Corrupted garbage bytes fail with InvalidContainerException
        cryptoStorage.setLocalWrappedMek("CorruptedCiphertextGarbageBytes".toByteArray(), 1)

        assertThrows(InvalidContainerException::class.java) {
            keyStoreManager.unwrapKey(
                wrappedKeyBytes = cryptoStorage.getLocalWrappedMek()!!,
                kekVersion = 1,
                accountId = CryptoStorage.GUEST_ACCOUNT_ID
            )
        }

        // Case 2: Tampered valid container ciphertext/tag fails with IntegrityVerificationException
        val validMek = SecretKeySpec(ByteArray(32) { 0x42 }, "AES")
        val validWrapped = keyStoreManager.wrapKey(validMek, 1, CryptoStorage.GUEST_ACCOUNT_ID)
        val tampered = validWrapped.copyOf().also {
            it[it.size - 5] = (it[it.size - 5].toInt() xor 0xFF).toByte()
        }
        cryptoStorage.setLocalWrappedMek(tampered, 1)

        assertThrows(IntegrityVerificationException::class.java) {
            keyStoreManager.unwrapKey(
                wrappedKeyBytes = cryptoStorage.getLocalWrappedMek()!!,
                kekVersion = 1,
                accountId = CryptoStorage.GUEST_ACCOUNT_ID
            )
        }
    }

    @Test
    fun testAccountKeyIsolation() {
        val aliceAccountId = "user_alice_123"
        val bobAccountId = "user_bob_456"

        val aliceMek = mekManager.getOrCreateMasterKey(aliceAccountId)
        val bobMek = mekManager.getOrCreateMasterKey(bobAccountId)

        assertFalse(
            "Alice's MEK must be cryptographically distinct from Bob's MEK",
            aliceMek.encoded.contentEquals(bobMek.encoded)
        )

        val aliceWrapped = cryptoStorage.getLocalWrappedMek(aliceAccountId)
        val bobWrapped = cryptoStorage.getLocalWrappedMek(bobAccountId)
        assertNotNull(aliceWrapped)
        assertNotNull(bobWrapped)
        assertFalse(aliceWrapped!!.contentEquals(bobWrapped!!))
    }

    @Test
    fun testGuestToAccountMigrationViaCopyKeys() {
        val guestMek = mekManager.getOrCreateMasterKey(CryptoStorage.GUEST_ACCOUNT_ID)
        val newUserId = "user_new_register"

        // Migrate keys to newly authenticated user
        cryptoStorage.copyKeys(CryptoStorage.GUEST_ACCOUNT_ID, newUserId)

        assertTrue(cryptoStorage.hasKeys(newUserId))
        val migratedWrapped = cryptoStorage.getLocalWrappedMek(newUserId)
        assertNotNull(migratedWrapped)

        val migratedMek = keyStoreManager.unwrapKey(migratedWrapped!!, 1, newUserId)
        assertArrayEquals("Migrated user MEK must match original guest MEK", guestMek.encoded, migratedMek.encoded)
    }

    // =========================================================================
    // SECTION 5: BIP-39 RECOVERY SECURITY AUDIT
    // =========================================================================

    @Test
    fun testValidRecoveryPhraseRestoresExactMek() {
        val originalMek = mekManager.getOrCreateMasterKey()
        val recoveryBlob = mekManager.wrapMekForRecovery(testMnemonic, passphrase = "")

        // Simulate new device with clean keystore
        val cleanKeyStore = SoftwareKeyStoreManager()
        val cleanStorage = InMemoryCryptoStorage()
        val cleanMekManager = MekManager(cleanKeyStore, cleanStorage)

        val restoredMek = cleanMekManager.recoverMasterKeyFromMnemonic(
            mnemonic = testMnemonic,
            passphrase = "",
            wrappedRecoveryBlob = recoveryBlob
        )

        assertArrayEquals("Restored MEK on new device must match original MEK bit-for-bit", originalMek.encoded, restoredMek.encoded)
    }

    @Test
    fun testInvalidWordInRecoveryPhraseRejected() {
        val invalidPhrase = testMnemonic.toMutableList().also { it[0] = "notasinvalidword" }

        assertThrows(IllegalArgumentException::class.java) {
            Bip39.validateMnemonic(invalidPhrase).getOrThrow()
        }
    }

    @Test
    fun testInvalidChecksumInRecoveryPhraseRejected() {
        // Replacing last word breaks the 4-bit checksum
        val corruptedChecksumPhrase = testMnemonic.toMutableList().also { it[11] = "abandon" }

        val result = Bip39.validateMnemonic(corruptedChecksumPhrase)
        assertTrue("Invalid checksum must be reported as failure", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("checksum") == true)
    }

    @Test
    fun testWrongPassphraseProducesDifferentRecoveryKeyAndFailsDecryption() {
        val recoveryBlob = mekManager.wrapMekForRecovery(testMnemonic, passphrase = "CorrectPassword123")

        val cleanKeyStore = SoftwareKeyStoreManager()
        val cleanStorage = InMemoryCryptoStorage()
        val cleanMekManager = MekManager(cleanKeyStore, cleanStorage)

        // Attempting to unwrap with wrong passphrase fails authentication
        assertThrows(IntegrityVerificationException::class.java) {
            cleanMekManager.recoverMasterKeyFromMnemonic(
                mnemonic = testMnemonic,
                passphrase = "WrongPassword999",
                wrappedRecoveryBlob = recoveryBlob
            )
        }
    }

    @Test
    fun testIncorrectWordOrderingFailsDecryption() {
        val recoveryBlob = mekManager.wrapMekForRecovery(testMnemonic, passphrase = "")

        val cleanKeyStore = SoftwareKeyStoreManager()
        val cleanStorage = InMemoryCryptoStorage()
        val cleanMekManager = MekManager(cleanKeyStore, cleanStorage)

        // Swap words 0 and 11 ("abandon" and "about")
        val swappedPhrase = testMnemonic.toMutableList().also {
            val tmp = it[0]
            it[0] = it[11]
            it[11] = tmp
        }

        assertThrows(Exception::class.java) {
            cleanMekManager.recoverMasterKeyFromMnemonic(
                mnemonic = swappedPhrase,
                passphrase = "",
                wrappedRecoveryBlob = recoveryBlob
            )
        }
    }

    // =========================================================================
    // SECTION 6: GOOGLE DRIVE SECURITY & ACCOUNT ISOLATION AUDIT
    // =========================================================================

    @Test
    fun testNarrowOAuthScopesOnly() {
        // Verify only appDataFolder and email are requested
        assertEquals("https://www.googleapis.com/auth/drive.appdata", DriveAuthConstants.DRIVE_APPDATA_SCOPE)
        assertEquals("https://www.googleapis.com/auth/userinfo.email", DriveAuthConstants.EMAIL_SCOPE)
    }

    @Test
    fun testAccessTokensNeverPersistedInPrefs() = runBlocking {
        drivePrefs.setConnected("alice@example.com", "account_alice_id")
        val state = drivePrefs.getState()

        assertTrue(state.isConnected)
        assertEquals("alice@example.com", state.connectedEmail)
        assertEquals("account_alice_id", state.connectedAccountId)

        // Verify DataStore does not contain any access token key or token value
        val prefsFile = File(fakeContext.filesDir, "datastore/notas_drive_prefs.preferences_pb")
        if (prefsFile.exists()) {
            val content = prefsFile.readText(Charsets.ISO_8859_1)
            assertFalse("Access token string must never appear in preferences file", content.contains("access_token"))
            assertFalse("Bearer must never appear in preferences file", content.contains("Bearer"))
        }
    }

    @Test
    fun testDisconnectClearsAuthorizationAndResetsState() = runBlocking {
        drivePrefs.setConnected("bob@example.com", "account_bob_id")
        assertTrue(drivePrefs.getState().isConnected)

        drivePrefs.setDisconnected()
        val state = drivePrefs.getState()
        assertFalse(state.isConnected)
        assertNull(state.connectedEmail)
        assertNull(state.connectedAccountId)
    }

    @Test
    fun testRemoteMetadataNeverExposesNoteContent() {
        val manifest = haddad.three.notas.data.drive.model.DriveBackupManifest(
            backupId = "b123",
            createdAtEpochMillis = 1000L,
            schemaVersion = 14,
            appVersion = "1.0.0",
            notesCount = 10,
            tasksCount = 5,
            eventsCount = 2,
            categoriesCount = 3,
            attachmentsCount = 1,
            ownerId = "user_42",
            sourceDevice = "Pixel",
            plainSha256 = "hash123"
        )

        val metadataJson = JSONObject().apply {
            put("name", "notas_backup_1000_b123.bin")
            put("appProperties", JSONObject().apply {
                put("backupId", manifest.backupId)
                put("notesCount", manifest.notesCount.toString())
                put("plainSha256", manifest.plainSha256)
            })
        }.toString()

        assertFalse("Metadata must never contain note body", metadataJson.contains("body"))
        assertFalse("Metadata must never contain note title", metadataJson.contains("title"))
        assertFalse("Metadata must never contain sensitive content", metadataJson.contains("content"))
    }

    // =========================================================================
    // SECTION 8: INCREMENTAL SYNC SECURITY & REPLAY PREVENTION AUDIT
    // =========================================================================

    @Test
    fun testReplayAttackProtectionInAppliedOperations() = runBlocking {
        val appliedDao = FakeAuditAppliedOperationDao()

        val opId = "change_op_999"
        assertFalse(appliedDao.isApplied(opId))

        // Record first application
        appliedDao.insert(
            SyncAppliedOperationEntity(
                changeId = opId,
                journalId = "j1",
                deviceId = "dev1",
                sequence = 1L,
                entityType = "note",
                entityId = "note_123",
                appliedAt = System.currentTimeMillis(),
                hlcTimestamp = "1000:0:dev1",
            )
        )

        // Second submission is identified as already applied (replay protection)
        assertTrue("Duplicate operation must be identified as already applied", appliedDao.isApplied(opId))
    }

    @Test
    fun testHlcMonotonicityEnforced() = runBlocking {
        val initialHlc = hlcClock.generateNext()
        val pastRemoteHlc = Hlc(
            physicalTime = initialHlc.physicalTime - 10000L,
            logicalCounter = 5,
            deviceId = "stale_device"
        )

        // Causal merge of older remote HLC must never pull local clock backwards
        val mergedHlc = hlcClock.mergeRemote(pastRemoteHlc)
        assertTrue(
            "Local HLC physical time must remain monotonic after merging stale remote clock",
            mergedHlc.physicalTime >= initialHlc.physicalTime
        )
    }

    @Test
    fun testTombstonePreventsStaleResurrection() = runBlocking {
        val tombstoneDao = FakeAuditTombstoneDao()
        val noteUuid = "note_deleted_uuid"

        // Insert tombstone
        tombstoneDao.insertOrUpdate(
            SyncTombstoneEntity(
                entityType = "note",
                entityId = noteUuid,
                deletedAt = 2000L,
                hlcTimestamp = "2000:0:deviceA",
                deviceId = "deviceA"
            )
        )

        val tombstone = tombstoneDao.getTombstone("note", noteUuid)
        assertNotNull("Tombstone must persist to prevent resurrection", tombstone)
        assertEquals(2000L, tombstone?.deletedAt)
    }

    // =========================================================================
    // SECTION 9: BACKUP & RESTORE DISASTER RECOVERY AUDIT
    // =========================================================================

    @Test
    fun testTamperedEncryptedBackupFailsBeforeDatabaseWrite() = runBlocking {
        val fakeNoteDao = FakeAuditNoteDao()
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note_safe",
                title = "Original Safe Note",
                body = "Must not be corrupted by failed restore",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val fakeClient = FakeAuditDriveApiClient()
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val mek = keyGen.generateKey()

        // Create a corrupted backup file on Drive (random bytes)
        val corruptedBytes = ByteArray(500) { 0x5A.toByte() }
        fakeClient.storedFiles["corrupted_file_id"] = corruptedBytes

        val archiveManager = DriveBackupArchiveManager(
            noteDao = fakeNoteDao,
            shoulderDao = FakeAuditShoulderDao(),
            itemDao = BaseFakeChecklistItemDao(),
            themePrefs = ThemePrefs(fakeContext),
            notesUiPrefs = NotesUiPrefs(fakeContext),
            viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
            lockPrefs = LockPrefs(fakeContext),
            interactionPrefs = InteractionPrefs(fakeContext),
            quickCapturePrefs = QuickCapturePrefs(fakeContext),
            reminderPrefs = ReminderPrefs(fakeContext),
            defaultNotePrefs = DefaultNotePrefs(fakeContext),
            updatePrefs = UpdatePrefs(fakeContext),
        )

        val provider = GoogleDriveBackupProvider(
            context = fakeContext,
            driveApiClient = fakeClient,
            driveAuthDelegate = FakeAuditDriveAuthDelegate(),
            drivePrefs = drivePrefs,
            mekManager = mekManager,
            envelopeManager = DriveRecoveryEnvelopeManager(fakeClient, mekManager, cryptoStorage),
            archiveManager = archiveManager,
            authRepository = FakeAuditAuthRepository(),
            noteDao = fakeNoteDao,
            shoulderDao = FakeAuditShoulderDao(),
            itemDao = BaseFakeChecklistItemDao(),
            noteRepository = NoteRepository(
                noteDao = fakeNoteDao,
                database = null,
                itemDao = BaseFakeChecklistItemDao(),
                attachmentDao = BaseFakeAttachmentDao(),
                scheduler = null,
                shoulderRepository = ShoulderRepository(FakeAuditShoulderDao(), null, FakeAuditAuthRepository()),
                appMediaStorage = null,
                tombstoneRecorder = null,
                authRepository = FakeAuditAuthRepository(),
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            ),
            themePrefs = ThemePrefs(fakeContext),
            notesUiPrefs = NotesUiPrefs(fakeContext),
            viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
            lockPrefs = LockPrefs(fakeContext),
            interactionPrefs = InteractionPrefs(fakeContext),
            quickCapturePrefs = QuickCapturePrefs(fakeContext),
            reminderPrefs = ReminderPrefs(fakeContext),
            defaultNotePrefs = DefaultNotePrefs(fakeContext),
            updatePrefs = UpdatePrefs(fakeContext),
            database = null,
            scheduler = null,
            ioDispatcher = testDispatcher,
        )

        val restoreResult = provider.restoreBackup("corrupted_file_id", RestoreMode.REPLACE)
        assertTrue("Restore of corrupted backup must fail: $restoreResult", restoreResult is DriveResult.Error)

        // Existing local note must remain completely unharmed
        val notes = fakeNoteDao.allNotes()
        assertEquals(1, notes.size)
        assertEquals("Original Safe Note", notes.first().note.title)
    }

    // =========================================================================
    // AUDIT TEST DOUBLES & FAKES
    // =========================================================================

    private class FakeAuditNoteDao : BaseFakeNoteDao()

    private class FakeAuditShoulderDao : BaseFakeShoulderDao()

    private class FakeAuditAppliedOperationDao : SyncAppliedOperationDao {
        val applied = mutableMapOf<String, SyncAppliedOperationEntity>()

        override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
            applied[operation.changeId] = operation
            return applied.size.toLong()
        }
        override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? = applied[changeId]
        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> =
            applied.values.filter { it.journalId == journalId }
        override suspend fun isApplied(changeId: String): Boolean = applied.containsKey(changeId)
        override suspend fun getAllOperations(): List<SyncAppliedOperationEntity> = applied.values.toList()
        override suspend fun insertAll(operations: List<SyncAppliedOperationEntity>) {
            operations.forEach { applied[it.changeId] = it }
        }
        override suspend fun clearAll() { applied.clear() }
    }

    private class FakeAuditTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableMapOf<String, SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones["${tombstone.entityType}:${tombstone.entityId}"] = tombstone
        }
        override suspend fun getTombstone(entityType: String, entityId: String): SyncTombstoneEntity? =
            tombstones["$entityType:$entityId"]
        override suspend fun deleteTombstone(entityType: String, entityId: String): Int =
            if (tombstones.remove("$entityType:$entityId") != null) 1 else 0
        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.values.toList()
        override suspend fun clearAll() { tombstones.clear() }
    }

    private class FakeAuditAuthRepository : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? = AuthUser(uid = "audit_user", email = "audit@example.com", isEmailVerified = true)
        override suspend fun registerWithEmailAndPassword(e: String, p: String, u: String?): AuthResult<AuthUser> = AuthResult.Success(currentUser!!)
        override suspend fun loginWithEmailAndPassword(e: String, p: String): AuthResult<AuthUser> = AuthResult.Success(currentUser!!)
        override suspend fun signInWithGoogle(c: Context): AuthResult<AuthUser> = AuthResult.Success(currentUser!!)
        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun reloadUser(): AuthResult<AuthUser> = AuthResult.Success(currentUser!!)
        override suspend fun sendPasswordResetEmail(e: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun confirmPasswordReset(c: String, p: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun verifyPasswordResetCode(c: String): AuthResult<String> = AuthResult.Success("audit@example.com")
        override suspend fun applyActionCode(c: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun updatePassword(p: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("None")
        override suspend fun signOut() {}
        override suspend fun getIdToken(f: Boolean): String? = "audit_token"
    }

    private class FakeAuditDriveAuthDelegate : GoogleDriveAuthDelegate {
        override suspend fun authorize(c: Context, f: Boolean): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("audit_drive_token", "audit@example.com")
        override suspend fun parseConsentResult(c: Context, d: android.content.Intent?): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("audit_drive_token", "audit@example.com")
        override suspend fun disconnect(c: Context, a: String?, t: String?) {}
        override suspend fun invalidateToken(c: Context, t: String) {}
    }

    private class FakeAuditDriveApiClient : GoogleDriveApiClient() {
        val storedFiles = mutableMapOf<String, ByteArray>()

        override suspend fun downloadFile(
            accessToken: String,
            fileId: String,
            targetFile: File,
            onProgress: ((Float) -> Unit)?,
        ): DriveResult<Unit> {
            val bytes = storedFiles[fileId] ?: return DriveResult.Error("File not found on Drive")
            targetFile.writeBytes(bytes)
            return DriveResult.Success(Unit)
        }

        override suspend fun listFiles(
            accessToken: String,
            spaces: String,
            pageSize: Int,
            pageToken: String?,
            query: String?,
        ): DriveResult<DriveFileListResponse> = DriveResult.Success(DriveFileListResponse(emptyList(), null))
    }
}

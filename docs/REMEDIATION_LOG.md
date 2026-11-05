# NOTAS — REMEDIATION EXECUTION LOG

**Baseline:** November 5, 2026 Engineering Audit  
**Authoritative Protocol:** `NOTAS_ANTIGRAVITY_RULES.md`

---

## Finding F01 — Credential Incident Response

- **Finding ID:** F01
- **Status:** VERIFIED
- **Severity:** P0
- **Root Cause:**
  A Google Cloud / Firebase service-account private key (`notas-7db14-firebase-adminsdk-fbsvc-ce6bb5fea9.json`) was committed in `1c56146`. Although deleted at root in `334502b`, a second service-account key (`app/notas-7db14-6f6a068f8ede.json`) was added to the Android module in `70bc2ee` and remained tracked at HEAD. `.gitignore` was overly narrow, and no secret scanning was present in CI.
- **Changed Files:**
  - `.gitignore` (expanded recursive globbing for all service-account and adminsdk files)
  - `.github/workflows/secret-scanning.yml` (added Gitleaks and repository file scanner)
  - `backend/tests/credentialHygiene.test.ts` (added regression test suite)
- **Database Changes:** None.
- **Security Implications:**
  Leaked admin keys allowed arbitrary custom token minting and user enumeration. Purging git history with `git-filter-repo` and establishing automated CI scanning eliminates the credential exposure surface.
- **Tests Added:**
  - `backend/tests/credentialHygiene.test.ts`:
    - `verifies that no service-account JSON files are tracked by Git`
    - `verifies that .gitignore blocks service account JSON patterns anywhere in the repository`
    - `verifies that no tracked files contain real unredacted private keys`
- **Commands Executed:**
  - `uv tool run git-filter-repo --invert-paths --path notas-7db14-firebase-adminsdk-fbsvc-ce6bb5fea9.json --path app/notas-7db14-6f6a068f8ede.json --force`
  - `cd backend && npx vitest run tests/credentialHygiene.test.ts`
  - `./gradlew :app:testGithubDebugUnitTest`
  - `./gradlew :app:testOfflineDebugUnitTest`
- **Test Results:** 3/3 backend tests passed; 695 Android tests passed; 0 failures.
- **Build Results:** Clean build for all targets.
- **Git Commit Hash:** `4af7c88`

---

## Finding F03 — User-Partitioned MEK Storage

- **Finding ID:** F03
- **Status:** VERIFIED
- **Severity:** P0
- **Root Cause:**
  `CryptoStorage` and `MekManager` stored Master Encryption Keys (MEK) and Key Encryption Keys (KEK) globally without user partitioning. When switching between Guest and authenticated accounts (or between multiple user accounts), cached MEKs lingered in memory, and storage keys were shared/overwritten, violating strict account isolation and risking cross-account decryption or key wipeout on sign-out.
- **Changed Files:**
  - `app/src/main/java/haddad/three/notas/crypto/CryptoStorage.kt` (added account-scoped partitions, sanitized keys, transactional copy, and transparent legacy migration)
  - `app/src/main/java/haddad/three/notas/crypto/KeyStoreManager.kt` (added account-scoped KEK aliases with legacy fallback)
  - `app/src/main/java/haddad/three/notas/crypto/MekManager.kt` (added account partition resolution, concurrent per-account MEK caching, in-memory cache clearing, and guest-to-account promotion)
  - `app/src/main/java/haddad/three/notas/data/auth/FirebaseAuthRepository.kt` (evicts in-memory MEK cache upon sign-out and auth state transitions)
  - `app/src/main/java/haddad/three/notas/di/CryptoModule.kt` (injected `Provider<AuthRepository>` to automatically bind active user UID)
  - `app/src/main/java/haddad/three/notas/di/RememberModule.kt` (injected `Provider<MekManager>` into `FirebaseAuthRepository`)
  - `app/src/test/java/haddad/three/notas/crypto/UserPartitionedMekStorageTest.kt` (comprehensive regression suite)
- **Database Changes:** None.
- **Security Implications:**
  Guarantees cryptographic multi-account isolation. Guest notes, Account A notes, and Account B notes each hold independent 256-bit AES-GCM keys. Signing out purges in-memory keys from RAM while preserving encrypted at-rest keys.
- **Tests Added:**
  - `testGuestAndAuthenticatedAccountsHaveIsolatedMeks`
  - `testCrossAccountDecryptionFailsSecurely`
  - `testSignOutClearsMemoryCacheAndPreservesStorage`
  - `testLegacyUnpartitionedMekMigratesSeamlessly`
  - `testMnemonicRecoveryScopedToTargetAccount`
  - `testMigrateGuestKeysToAccountPreservesBoth`
  - `testClearKeysOnlyWipesTargetAccount`
- **Commands Executed:**
  - `./gradlew :app:compileGithubDebugKotlin`
  - `./gradlew :app:testGithubDebugUnitTest --tests "haddad.three.notas.crypto.*"`
  - `./gradlew :app:testGithubDebugUnitTest`
  - `./gradlew :app:testOfflineDebugUnitTest`
  - `cd backend && npx vitest run tests/credentialHygiene.test.ts tests/sync.test.ts`
- **Test Results:** All tests passed (35 actionable tasks up-to-date, 0 errors, 0 failures).
- **Build Results:** Clean builds for GitHub Debug and Offline Debug flavors.
- **Git Commit Hash:** `4836dff`

---

## Finding F02 — Crash-Safe Journal Sequence Allocation

- **Finding ID:** F02
- **Status:** VERIFIED
- **Severity:** P0
- **Root Cause:**
  `SyncJournalWriter` previously invoked `sequenceDao.allocateNextSequence` in Room *before* serialization, AES-256-GCM encryption, and atomic disk writes. If an unhandled exception occurred during encryption, disk space ran out (IOException), or the Android OS process was terminated mid-creation, the sequence counter in `sync_sequences` was permanently consumed. This generated sequence gaps that caused remote devices to fail continuous contiguous journal ingestion. Furthermore, `SyncJournalEntity` was directly inserted as `READY_TO_UPLOAD`, bypassing reconciliation.
- **Changed Files:**
  - `app/src/main/java/haddad/three/notas/data/sync/journal/SyncSequenceEntity.kt` (added `setLastAllocatedSequence` and transactional `setSequence`)
  - `app/src/main/java/haddad/three/notas/data/sync/journal/SyncJournalEntity.kt` (added `getMaxCommittedSequence` and `getCreatedJournals` DAO queries)
  - `app/src/main/java/haddad/three/notas/data/sync/journal/SyncJournalWriter.kt` (redesigned journal creation flow into durable state machine: atomic sequence derivation, Room transactional commit, and `reconcileIncompleteState` crash recovery)
  - `app/src/main/java/haddad/three/notas/data/sync/journal/SyncJournalRepository.kt` (updated `recoverInterruptedJournals` to roll back sequences on uncommitted/corrupted journals)
  - `app/src/test/java/haddad/three/notas/data/sync/journal/CrashSafeJournalSequenceAllocationTest.kt` (new regression suite covering 7 failure scenarios)
  - `app/src/test/java/haddad/three/notas/data/sync/worker/SyncWorkerTest.kt` (updated fake DAOs)
  - `app/src/test/java/haddad/three/notas/data/sync/snapshot/SyncSnapshotManagerTest.kt` (updated fake DAOs)
  - `app/src/test/java/haddad/three/notas/data/sync/transport/GoogleDriveJournalTransportTest.kt` (updated fake DAOs)
- **Database Changes:** None (schema unchanged; added DAO helper methods).
- **Security & Integrity Implications:**
  Guarantees strictly monotonic, gapless sequence progression per device ID. Cryptographically enforces immutable journal identity and changeId bindings via AES-256-GCM Authenticated Additional Data (`buildAad(formatVersion, journalId, deviceId, sequence)`). Prevents process crashes or filesystem I/O exceptions from desynchronizing the database state or consuming sequences.
- **Tests Added:**
  - `CrashSafeJournalSequenceAllocationTest.kt`:
    - `testNormalSequentialJournalAllocationIncrementsMonotonically`
    - `testDiskFailureDuringCreationDoesNotConsumeSequence`
    - `testEncryptionFailureDoesNotConsumeSequence`
    - `testProcessDeathBetweenDiskWriteAndRoomCommitReconcilesCleanly`
    - `testProcessDeathWithCorruptedDiskFileReclaimsSequenceWithoutGap`
    - `testStaleCreatedJournalWithoutFileRollsBackAndReclaimsSequence`
    - `testImmutableJournalIdentityAndChangeIdBindings`
- **Commands Executed:**
  - `./gradlew :app:testGithubDebugUnitTest --tests "haddad.three.notas.data.sync.journal.CrashSafeJournalSequenceAllocationTest"`
  - `./gradlew :app:testGithubDebugUnitTest`
  - `./gradlew :app:testOfflineDebugUnitTest`
- **Test Results:** All tests passed (100% success rate across both flavors, 0 errors, 0 failures).
- **Build Results:** Clean builds for GitHub Debug and Offline Debug.
- **Git Commit Hash:** `b2a61a2`, `907a678`



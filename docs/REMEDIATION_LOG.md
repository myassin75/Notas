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


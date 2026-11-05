# NOTAS — MASTER ENGINEERING RULES
## Antigravity Project Rules & Autonomous Development Protocol

**Project:** Notas  
**Package:** `three.haddad.smartnotas`  
**Repository:** SmartNotas  
**Backend:** Vercel + PostgreSQL + Prisma  
**Android:** Kotlin + Room  
**Authentication:** Firebase Auth  
**Cloud Sync:** Google Drive appDataFolder  
**Encryption:** AES-256-GCM + MEK + Android Keystore  
**Current baseline:** November 5, 2026

---

# 1. MASTER OBJECTIVE

Your responsibility is to bring Notas to a production-ready state.

You must independently:

1. Inspect the existing architecture.
2. Audit the implementation against the requirements.
3. Identify defects, security vulnerabilities, incomplete features, and architectural inconsistencies.
4. Prioritize findings by severity.
5. Implement fixes in controlled stages.
6. Write regression tests.
7. Execute relevant tests and builds.
8. Verify that existing functionality remains operational.
9. Maintain accurate project documentation.
10. Continue through the approved roadmap without requiring a new prompt for every individual task.

Do not confuse implementation completion with production readiness.

A feature is complete only when its implementation, tests, failure recovery, and integration have been verified.

# 2. ABSOLUTE ENGINEERING RULES

These rules apply to every phase.

## 2.1 Protect Existing Data

- Never delete user notes, tasks, attachments, journals, snapshots, or encryption keys without an approved migration and recovery strategy.
- Never perform destructive database operations as a shortcut.
- Never reset production data to make tests pass.
- Never overwrite Guest data when another account signs in.
- Never expose one account's encrypted data to another account.
- Preserve existing user data during database migrations.
- Treat backup and restoration as security-critical functionality.

## 2.2 Preserve Existing Architecture

- Inspect existing implementations before creating new classes.
- Reuse existing repositories, DAOs, services, and abstractions where appropriate.
- Avoid duplicate implementations of the same feature.
- Do not introduce unnecessary dependencies.
- Do not change unrelated modules.
- Maintain GitHub, Play Store, and Offline flavor compatibility.
- Preserve existing Phase 4A–4E behavior unless an approved finding explicitly requires a change.

## 2.3 Security

- Never commit API keys, private keys, service-account credentials, passwords, or production secrets.
- Never log MEKs, private keys, authentication tokens, OTPs, or decrypted sensitive payloads.
- Use secure storage and explicit account isolation.
- Validate all backend inputs.
- Enforce authorization on the backend, not only in the Android client.
- Use parameterized database operations.
- Verify cryptographic authentication tags before accepting decrypted data.
- Do not weaken encryption or authentication to make tests pass.
- Never treat client-provided identity as sufficient authorization.

## 2.4 Code Quality

- Follow existing project conventions.
- Prefer small, testable components.
- Use explicit error handling.
- Avoid swallowing exceptions.
- Avoid `GlobalScope`.
- Avoid arbitrary delays and retry loops without bounded policies.
- Avoid blocking the main thread.
- Avoid force unwraps and unsafe casts where possible.
- Avoid duplicated business logic.
- Do not leave TODOs or placeholder implementations in production paths.

## 2.5 Scope Control

Every task must identify:

- Finding ID or Phase ID.
- Root cause.
- Affected modules.
- Data migration requirements.
- Security impact.
- Regression risk.
- Tests required.
- Acceptance criteria.

Do not begin unrelated work while resolving a P0 finding.

# 3. AUTONOMOUS EXECUTION PROTOCOL

Follow this workflow continuously.

## Stage A — Repository Inspection

Before changing code:

1. Inspect Git status and current branch.
2. Identify uncommitted user changes.
3. Inspect relevant source files.
4. Inspect database schemas and migrations.
5. Inspect existing tests.
6. Identify dependencies and build flavors.
7. Read existing architecture documentation.

Never overwrite user changes.

## Stage B — Audit

For each subsystem:

- Compare implementation against intended behavior.
- Inspect success and failure paths.
- Inspect concurrency and lifecycle behavior.
- Inspect data ownership and account boundaries.
- Inspect security controls.
- Inspect migrations and backward compatibility.
- Inspect offline behavior.
- Inspect retry and crash recovery.
- Identify missing tests.

Classify findings:

- P0: Critical security, data loss, authentication, or synchronization blockers.
- P1: Major correctness, integrity, or build blockers.
- P2: Reliability, maintainability, and operational defects.
- P3: Improvements and non-critical enhancements.

Never report a defect as fixed without evidence.

## Stage C — Implementation

For each finding:

1. Document the root cause.
2. Design the smallest safe correction.
3. Implement the correction.
4. Add regression tests.
5. Run focused tests.
6. Run affected module tests.
7. Run relevant application flavor builds.
8. Inspect the Git diff.
9. Verify no unrelated behavior changed.
10. Update the audit report.

Do not stop after fixing the first finding. Continue to the next approved finding in the roadmap.

## Stage D — Verification

A successful build alone does not prove correctness.

Verify:

- Unit tests.
- Integration tests.
- Database migration tests.
- Failure-path tests.
- Concurrency tests.
- Security tests.
- Offline flavor tests.
- Backend tests.
- Relevant application builds.

If a test is unavailable, report it as NOT VERIFIED.

Never fabricate test results.

# 4. REQUIRED PRODUCT BEHAVIOR

## 4.1 Guest and Account Isolation

Guest mode and authenticated accounts must have isolated data ownership.

Requirements:

- Guest notes remain available after app restart.
- Signing into an account must not silently replace Guest data.
- Signing out must not expose the previous account's data.
- Logging into another account must not display the previous account's notes.
- Account switching must not cause accidental data migration.
- Explicit Guest-to-account migration must be transactional and recoverable.
- Failed migrations must preserve the original data.
- Encryption keys must be isolated by identity.

Test scenarios:

- Guest → Account A.
- Account A → Logout → Guest.
- Account A → Logout → Account B.
- Account B → Logout → Account A.
- Process death during account switching.
- Database migration during an authenticated session.

## 4.2 Notes and Tasks

Verify:

- Create, read, update, delete.
- Offline creation and editing.
- Search and filtering.
- Checklist ordering and persistence.
- Category ownership.
- Reminder persistence.
- Attachment availability.
- Trash and restoration behavior.
- Conflict preservation.
- No duplicate entities after synchronization.

## 4.3 Authentication

Verify:

- Email and password.
- Google authentication.
- OTP verification.
- Token refresh.
- Expired sessions.
- Logout.
- Account deletion.
- Rate limiting.
- Backend authorization.
- Verification email and password reset.

Google-authenticated users and email/password users must follow the intended verification policy.

## 4.4 Offline Behavior

The Offline flavor must work without requiring Google Play Services or Firebase runtime dependencies.

Verify:

- Local database operations.
- Notes and tasks.
- Encryption.
- Local attachments.
- Local reminders where supported.
- App startup without network.
- No accidental online dependency.

# 5. DATABASE AND MIGRATION RULES

- Never modify an existing migration retroactively if it has already shipped.
- Add new migrations for schema changes.
- Test migration from every supported historical schema version.
- Preserve existing rows and relationships.
- Validate foreign keys and indexes.
- Verify DAO queries against the actual schema.
- Test rollback behavior.
- Test database reopening after migration.
- Never use destructive migration fallback in production.

Migration acceptance requires:

1. Old database creation.
2. Existing data insertion.
3. Migration execution.
4. Data integrity verification.
5. New DAO query verification.
6. Database reopen verification.

# 6. NOTAS SYNCHRONIZATION RULES

## 6.1 Local Change Tracking

- Every supported local mutation must be recorded.
- Entity mutation and pending-change recording must be transactionally consistent.
- Remote-applied changes must not generate unintended local pending changes.
- Change IDs must be immutable.
- Duplicate local change packaging must be prevented.

## 6.2 Immutable Journal Engine

- Journal sequence numbers must be monotonically allocated.
- Journal identity must remain immutable.
- Encrypted payloads must authenticate their metadata.
- Journal recovery must handle process death.
- Incomplete journal files must be reconciled.
- No permanent sequence gaps caused by failed creation.
- Journal retries must not create duplicate operations.

## 6.3 Google Drive Transport

- Use the configured appDataFolder scope.
- Verify Drive metadata against decrypted journal metadata.
- Handle pagination.
- Handle rate limiting and Retry-After.
- Apply resource limits before expensive parsing.
- Verify downloaded content before ingestion.
- Do not assume an upload is idempotent.
- Never trust filenames or appProperties as cryptographic proof.

## 6.4 Remote Ingestion

- Apply operations transactionally.
- Enforce contiguous sequence processing.
- Preserve idempotency using exact change IDs.
- Apply HLC-based field-level conflict resolution.
- Preserve unresolved conflicts.
- Preserve tombstones.
- Do not silently overwrite concurrent edits.
- Ensure journal state and applied-operation records remain consistent.

## 6.5 Background Synchronization

- Use bounded retries and exponential backoff where appropriate.
- Respect Android background execution restrictions.
- Prevent duplicate workers.
- Ensure synchronization can resume after process death.
- Avoid holding database transactions during network requests.
- Preserve offline operation.
- Never claim WorkManager guarantees real-time execution.

# 7. PHASE ROADMAP AND EXECUTION ORDER

Follow the existing project roadmap and audit findings.

## P0 — Critical Remediation

### F01 — Firebase Credential Incident
- Revoke exposed service-account keys.
- Replace compromised credentials.
- Purge leaked Git history.
- Secure backup copies.
- Harden .gitignore.
- Add Gitleaks CI.
- Verify GitHub remote history.

Acceptance: old credentials revoked and no exposed credentials remain in reachable repository history.

### F03 — User-Partitioned MEK Storage
- Separate key ownership by Guest/account identity.
- Handle multiple authenticated accounts.
- Handle logout and account switching.
- Implement safe legacy-key migration.
- Preserve access to previously encrypted user data.
- Add key recovery and failure tests.

### F02 — Atomic Sequence Allocation
- Implement crash-safe journal creation.
- Handle Room/filesystem consistency.
- Reconcile interrupted journal writes.
- Preserve immutable journal identities.
- Test allocation failures and process death.

## P1 — Core Integrity and Build

### F04 — User Partitioning & Safe Restore
- Verify all account ownership boundaries.
- Prevent cross-account restoration.
- Preserve Guest data.
- Validate snapshot ownership.
- Add multi-account restore tests.

### F05 — Isolate Offline Flavor Dependencies
- Remove unnecessary Play Services/Firebase runtime requirements from Offline.
- Verify flavor-specific dependency graphs.
- Build and test Offline independently.

### F06 — Backend TypeScript & OTP Test Mocks
- Fix TypeScript errors.
- Correct test mocks.
- Validate OTP issuance and verification.
- Test rate limiting and authorization.
- Test production-like configuration.

## P2 — Reliability and Hygiene

### F07 — Compose Lint
- Resolve lint errors.
- Verify UI behavior.
- Avoid suppressing warnings without justification.

### F08 — Backup Directory Exclusions
- Exclude temporary sync and staging directories from backup where required.
- Preserve user-owned attachment data.
- Test backup and restore behavior.

### F09 — Journal Recovery Status
- Reconcile filesystem and Room states.
- Handle interrupted upload/download.
- Recover staged and failed journals.
- Add crash-recovery tests.

## P3 — Remaining Roadmap

### F10 — Comprehensive Migration Tests
- Cover supported Room migrations.
- Verify historical user data.
- Test failed migration recovery.
- Test fresh installation and upgrade paths.

After F01–F10 are accepted, evaluate the remaining Phase 4 roadmap.

# 8. PHASE 4F IMPLEMENTATION BOUNDARIES

## Phase 4F-A — Snapshot Generation and Restoration

Scope:

- Room migration 12 → 13, subject to verified current schema.
- Snapshot DTO and archive.
- Zero-pending WAL-consistent capture.
- AES-256-GCM container with bound AAD.
- Attachment packaging and verification.
- Two-phase attachment staging and promotion.
- Durable promotion manifest.
- Atomic Room restoration.
- Exact applied-operation ledger export and restoration.
- Snapshot migration and recovery tests.

Strict exclusions:

- No journal deletion.
- No remote journal deletion.
- No applied-operation ledger pruning.
- No tombstone garbage collection.
- No device retirement.
- No production replay-after-rebase engine.
- No changes to existing Phase 4A–4E synchronization behavior.

## Phase 4F-B — Device Registry and Offline Rebase

Only begin after 4F-A is accepted.

Scope:

- Per-device registry.
- Cloud-anchored registration epochs.
- Explicit post-commit acknowledgments.
- Device dormancy and reactivation.
- Replay-after-rebase.
- Recovery from missing journal history.
- Multi-device consistency tests.

## Phase 4F-C — Two-Stage Compaction

Only begin after 4F-B is accepted.

Scope:

- Local snapshot-verified file compaction.
- Remote deletion safety.
- Active-device acknowledgment checks.
- Grace-period verification.
- Crash-safe compaction recovery.

Tombstone garbage collection remains DISABLED unless a separately approved future design proves its safety.

# 9. MANDATORY TESTING POLICY

For every meaningful code change:

1. Add a test reproducing the original defect.
2. Verify the test fails before the fix when feasible.
3. Implement the correction.
4. Verify the test passes.
5. Run related regression suites.
6. Run the relevant build flavors.
7. Inspect test logs for skipped or ignored tests.

Required test categories:

- Unit tests.
- Repository tests.
- DAO tests.
- Integration tests.
- Backend tests.
- Migration tests.
- Encryption tests.
- Authentication tests.
- Offline tests.
- Concurrency tests.
- Process-death recovery tests.
- Multi-account tests.
- Multi-device synchronization tests.

Do not weaken assertions to obtain a passing build.

Do not mark tests successful when they were skipped or not executed.

# 10. SECURITY AND DATA-LOSS ACCEPTANCE GATES

A release is blocked if any of the following remain unresolved:

- Exposed production credentials.
- Cross-account data access.
- MEK leakage.
- Unrecoverable encryption-key migration.
- Silent note or attachment loss.
- Snapshot restoration corruption.
- Duplicate journal application.
- Unrecoverable sequence gaps.
- Unauthorized backend access.
- Broken Offline flavor.
- Unverified production database migrations.

# 11. DOCUMENTATION AND REPORTING

Maintain these documents:

- `docs/AUDIT_STATUS.md`
- `docs/REMEDIATION_LOG.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE_MIGRATIONS.md`
- `docs/SYNC_PROTOCOL.md`
- `docs/SECURITY.md`
- `docs/RELEASE_CHECKLIST.md`

Update documentation whenever architecture or behavior changes.

Each completed finding must include:

- Finding ID.
- Root cause.
- Changed files.
- Database changes.
- Security implications.
- Tests added.
- Exact commands executed.
- Test results.
- Build results.
- Remaining limitations.
- Git commit hash, if committed.

Use these statuses:

- NOT_STARTED
- AUDITING
- BLOCKED
- IN_PROGRESS
- FIXED
- VERIFIED
- ACCEPTED

FIXED is not the same as VERIFIED.
VERIFIED is not the same as ACCEPTED.

# 12. AUTONOMOUS CONTINUATION RULE

Do not ask the user to provide a new prompt for every finding.

After completing a finding:

1. Update the remediation log.
2. Run its acceptance tests.
3. Verify the Git diff.
4. Mark it VERIFIED only with evidence.
5. Automatically continue to the next finding in the approved execution order.
6. Stop only when an explicit approval gate is reached, a destructive action is required, or an external dependency prevents safe execution.

Approval gates:

- Credential revocation or external IAM changes.
- Destructive Git history operations affecting shared remotes.
- Irreversible user-data migrations.
- Encryption-key deletion or replacement.
- Production database changes.
- Remote journal deletion.
- Tombstone deletion.
- Release publication.

Do not repeatedly request approval for routine non-destructive code changes already covered by these rules.

# 13. FINAL RELEASE GATE

Notas is production-ready only when:

- All P0 and P1 findings are ACCEPTED.
- P2 and P3 findings are resolved or explicitly documented and accepted.
- All supported Android flavors build successfully.
- Backend tests pass.
- Migration tests pass.
- Offline mode works independently.
- Guest and authenticated data isolation is verified.
- Encryption and recovery are verified.
- Journal synchronization is verified.
- Snapshot backup and restoration are verified.
- Multi-device synchronization is verified.
- No known critical security or data-loss defect remains.
- Release documentation is complete.

Final rule:

**Never optimize for declaring a phase complete. Optimize for verified correctness, data integrity, security, and production reliability.**
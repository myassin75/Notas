# Notas — Comprehensive Application Audit Report

**Date:** 2026-10-29
**Scope:** Android app (Kotlin/Compose) + serverless backend (TypeScript/Vercel) — `/home/mohamed/Notas`
**Method:** Static analysis (6 parallel deep-dive passes), manual verification of all P0/P1-class findings against source, and execution of build/test/lint commands. **No source code was modified during the audit** (verified: working tree byte-identical before/after, git status unchanged).
**Commands actually executed:** backend `tsc --noEmit`, `vitest run`, `prisma validate`, `npm audit`; Android `testGithubDebugUnitTest` (692 tests), `detekt`, `ktlintGithubDebugSourceSetCheck`, `lintGithubDebug`. **Not executed:** instrumented tests (no emulator), R8 release build, visual/device UI tests, live network probes, performance profiling.

---

## 1. Executive Summary

| Dimension | Verdict |
|---|---|
| App completeness (local features) | **Strong** — notes/editor, reminders, trash, themes, lock, widgets, search, calendar: fully wired and largely tested |
| Cloud features (sync, attachments, account mgmt) | **Dormant/incomplete** — sync engine, cloud attachments, profile editing, account deletion are implemented end-to-end (client+server) but **never invoked from the app** |
| Code quality | Good architecture spoiled by heavy UI god-files, ~7 duplicated HTTP layers, toothless static analysis config |
| Security | **One critical live secret leak (P0)** + multiple confirmed backend P1s (cross-user writes, deletion data-retention gap) |
| Testing | 692 backend-flavor unit tests pass; 66 backend tests pass; static analysis **fails** (lint 7 errors, detekt 88 issues); `tsc` fails; CI runs almost no Android checks |
| **Production readiness** | **NOT READY** (see §10) |

The single most urgent action — before any feature work — is **rotating_and purging the leaked Firebase service-account private key** (§5, AUD-01). It grants full admin over `notas-7db14` and is in pushed GitHub history.

---

## 2. Project Overview

### 2.1 Stack

| Layer | Technology |
|---|---|
| App | Kotlin 2.4.20, Jetpack Compose (BOM 2026.09.01, M3 Expressive alpha), Hilt 2.60.1, Room 2.8.5 (FB adjacent), DataStore, Glance widgets, WorkManager/AlarmManager |
| App build | minSdk 31 / target+compile 37, versionName 1.10.0 (versionCode 11000), 3 flavors (github/offline/playstore), R8 full mode |
| Networking | Raw `HttpURLConnection` (no OkHttp/Retrofit), Coil 3 for images, Firebase Auth + Credential Manager |
| Backend | Node ≥20 on Vercel (`@vercel/node`), one handler in `backend/src/api/index.ts`, Prisma 5.22 + PostgreSQL, Firebase Admin 12.7 (`verifyIdToken`), S3-compatible storage, zod 3, vitest |
| CI | `backend-ci.yml` (prisma validate, tsc, vitest) + `build-release.yml` (manual dispatch; signing from secrets, Fastlane optional, artifact attestation) |

### 2.2 Major components

- **App packages:** `data/` (Room, repos, ~25 pref stores), `data/auth`, `data/sync`, `data/backup`, `reminders/`, `trash/`, `googletasks/`, `widget/`, `quickcapture/`, `quicksettings/`, `update/`, `ui/**` (auth, edit, calendar, search, history, settings, profile, lock, onboarding, theme), `di/`, `diagnostics/`.
- **Backend routes (all authenticated except `GET /health`):** `/me` (GET/PATCH/DELETE), `/me/stats`, `/sync/push`, `/sync/changes`, `/backups` (+ `:id`, `/verify`, `/restore`), `/attachments/*` (upload-url, verify, download-url, DELETE).
- **DB models:** `User`, `Category`, `Note`, `ChecklistItem`, `NoteAttachment`, `UserSettings`, `DeviceSyncCursor`, `Backup`. Single additive init migration; schema valid (`prisma validate` ✅).

---

## 3. Feature Completeness

### 3.1 Complete and functional (code-level)

Notes & Markdown editor, checklists, reminders (incl. exact alarms, critical repeat, snooze, boot re-arm), trash/archive with undo + 30-day sweep, search (FTS4), calendar + details, app lock (PBKDF2 PIN + biometric), themes (7 accents × light/dark/OLED), onboarding, help, in-app updates per flavor, quick settings tiles, 3 Glance widgets, share-in, local backup pipeline, Google Tasks import (github/playstore flavors).

Manifest hygiene is excellent: every declared component/permission has a matching implementation; nothing implemented is unregistered.

### 3.2 Implemented but **not wired** (dormant features — the app does NOT perform them)

| Feature | Topic | Status |
|---|---|---|
| **Cloud sync** | `SyncEngine.kt` — `pushSync`/`fetchChanges` implemented and unit-tested, but `SyncEngine.synchronize()` has **zero callers**: no WorkManager job, no post-login hook, no UI trigger | Dormant |
| **Cloud attachments** | `AttachmentStorageManager` (upload/verify/download) has zero callers; cloud backup hardcodes `"includeMedia": false` | Dormant |
| **Profile editing** | Backend supports `PATCH /me`; client never calls it; no edit UI | Missing counterpart |
| **Account deletion** | Backend implements resilient `DELETE /me` (incl. Firebase + S3 purge flag flow); app offers only sign-out | Missing counterpart |
| **Stats endpoint** | `GET /me/stats` unused; stats computed locally | Cosmetic duplication |
| **Sync status UI** | `SyncStatusIndicator.kt` (~300 lines) unused; `SyncState` stores written but never merged into UI | Dead code |

**Practical meaning:** today the app is a **local-only notes app with an auth façade and abandoned cloud plumbing**. Cloud backup runs (Settings → Backup works); metadata-level sync does not.

### 3.3 Wiring defects in "complete" features

| # | Feature | Conclusion |
|---|---|---|
| F1 | **Register → Login email prefill never works** — `AuthViewModel.kt:171` writes to `_loginState`, but `LoginScreen.kt:58`/`RegisterScreen.kt:57` each get a *different* VM instance via `hiltViewModel()` scoped to their own `NavBackStackEntry` | **Confirmed** (logic-level) |
| F2 | **Quick-capture notification toggle is not live** — `RememberApp.observeQuickCapturePref()` (`RememberApp.kt:132`) is never called; the toggle only takes effect after reboot/package-replace | **Confirmed** |
| F3 | **Offline flavor shows cloud backup UI** with no `INTERNET` permission — buttons visible in Settings → Backup (`SettingsScreen.kt:980` has no flavor gate) and fail only at runtime | **Confirmed** |
| F4 | **Guest → account upgrade end-state is abrupt**: login success navigates to Home with `popUpTo(graph){inclusive=true}` — no return to profile, no confirmed-sign-in feedback (`NavGraph.kt:845–859`) | Confirmed (UX) |
| F5 | Vercel deployment protection currently blocks staging API calls (documented in `phase_12e` report; clients detect it but sync cannot proceed) | Known blocker |

### 3.4 Orphaned / dead code

`ui/event/EditEventScreen.kt` (367 lines, no nav destination; its VM helper is used), `ui/onboarding/OnboardingTitleScreen.kt` (imported in `NavGraph.kt:91`, never composed), `SyncEngine`/`SyncStatusIndicator`/`AttachmentStorageManager`, `RememberApp.observeQuickCapturePref()`, root scratch files (`fix_check.kt`, `fix.sh`, `fix_google_services.py`), orphan `baselineprofile/` module (**not registered in `settings.gradle.kts`**), stale legacy schemas `app/schemas/dev.bikram.remember.data.RememberDatabase/*.json`, tracked APKs/scratch PNGs/phase reports at repo root.

---

## 4. Code Quality (Phases 2 & 8 condensed)

### 4.1 Architecture

- **Good:** single source of truth via Room flows; ViewModels are plain `ViewModel` + `StateFlow`; no UI file touches Room directly; flavors wired via Hilt plugins, not copy-paste; draft/session persistence in the editor is genuinely well-engineered.
- **Violations (data → ui imports):** `SyncEngine.kt:18` + `SyncStateStore.kt:16` import `ui.components.SyncStatus`; `ThemePrefs.kt:11` imports `ui.theme.CustomFontStorage`; `NotesWidget.kt:74–76` imports `ui.edit` constants; `ReminderScheduler.kt:27` imports `ui.edit.iconEmojiPayload`.
- **Layer leaks:** ViewModels performing HTTP directly (`RememberUpdateViewModel.kt:299–315`, `SettingsUpdateDownloads.kt:56`, `GoogleTasksApi.kt:27`).
- **Nullable production DI seams:** `SyncEngine`/`CloudBackupManager` take `database/graph default null` params designed only for tests.

### 4.2 Duplication & size (measured)

- HTTP layer re-implemented **7×** (`HttpURLConnection` + status ladder + `Json{ignoreUnknownKeys}`): `BackendApiClient.kt`, `SyncApiClient.kt`, `BackupApiClient.kt`, `GoogleTasksApi.kt`, `RememberUpdateChecker.kt:74`, `RememberUpdateViewModel.kt:299–315`, `SettingsUpdateDownloads.kt:56`. ~400–500 removable LOC. The Vercel-redirect detection block is copy-pasted ≥3×.
- `LoginScreen` vs `RegisterScreen` ≈ 60% structurally duplicated with **drifted tokens** (`LoginScreen.kt:209–215` vs `RegisterScreen.kt:225–232`) and unused imports (`LoginScreen.kt:22–23,31,34`).
- God files (LOC measured): `ReminderPicker.kt` 2,695 · `GoogleTasksImportScreen.kt` 2,436 · `IconPicker.kt` 2,014 · `HistoryScreen.kt` 1,974 · `SearchScreen.kt` 1,949 · `EditEventScreen.kt` 1,813 · `NavGraph.kt` 1,563 · `AdaptiveRoutes.kt` 1,523 · `DevOptionsScreen.kt` 1,437 · `NotesWidget.kt` 1,328 · `CalendarViewModel.kt` 1,068 (contains domain layout math).
- `CloudBackupManager.kt` (517 LOC) mixes ViewModel state + zip codec + hashing + HTTP.

### 4.3 Static analysis (executed)

| Tool | Result |
|---|---|
| `./gradlew :app:detekt` | ❌ **88 issues**: UnusedParameter 57, DestructuringDeclarationWithTooManyEntries 11, EmptyFunctionBlock 10, CyclomaticComplexMethod 3, LongMethod/LargeClass/FunctionOnlyReturningConstant 2 each, VariableNaming 1 |
| `./gradlew :app:lintGithubDebug` | ❌ **7 errors / 265 warnings / 5 hints**: 3× `NonObservableLocale` (`CalendarTimelineComponents.kt:146`, `SearchScreen.kt:1171,1200`), 4× `LocalContextGetResourceValueCall` (`EditEventScreen.kt:206,208`, `HistoryScreen.kt:1069,1092`) |
| detekt/lint thresholds | Effectively toothless: detekt.yml allows complexity ≤90, classes ≤900 lines, LongMethod excluded for `ui/**`; lint-baseline.xml filters only 4 items |
| `npx tsc --noEmit` | ❌ Exit 2: `tests/middleware.test.ts(5,31)` unused import — **this fails backend CI today** |
| `npx eslint .` | ❌ Exit 2: **no eslint config file exists**; the `lint` script can never run |
| Circular lint auto-fix | `lintGithubDebug` printed "This issue has been automatically fixed" for `CalendarTimelineComponents.kt:146`, but `git diff` confirms **file unchanged** (fix proposal only in reports) |

### 4.4 UX quality highlights (code-level; **no visual test performed**)

- **App-wide ripple suppression:** `ui/feedback/ClickModifiers.kt:27,57` defaults `indication = null`; ~91 call sites, only 2 restore a ripple → most tappables give no touch feedback (**verified**).
- **154 hardcoded `Color(0x…)` literals outside theme/** across 24 files (worst: `CalendarDesignTokens.kt` 31, `SetDateBottomSheet.kt` 20, `SearchScreen.kt` 19) — accent themes don't propagate to search/calendar/history sheets.
- **State-handling gaps:** Home shows a blank list while loading (no spinner; `HomeScreen.kt:501`); History can flash its empty illustration (no loading flag); GuestProfile "Sync Now" is fire-and-forget with no feedback; **Sign Out has no confirmation dialog** (`GuestProfileScreen.kt:363–366`) while every other destructive action does.
- **Accessibility:** only 1 `semantics { heading() }` in the app (TalkBack heading navigation effectively broken); hardcoded English `contentDescription`s overriding localization (`HomeSearchComponents.kt:112,163,183`); 28 decorative-null descriptions mostly fine; font-scale deliberately capped at 1.10× (`Theme.kt:51`, product decision).
- **No NavigationRail on tablets ≥840dp** despite an unused import (`MainTabScaffold.kt:51`); History transitions ignore reduced-motion (`NavGraph.kt:1010–1013`).
- Positive: calendar screen has the full loading/empty/error trifecta; no permanently disabled buttons; adaptive two-pane routing exists and is reasoned.

---

## 5. Security Findings

Legend: each finding is Confirmed (read/verified in source or executed) / Potential / Recommendation. **No secret values are reproduced.**

| ID | Finding | Sev | Status | Evidence / Fix |
|---|---|---|---|---|
| AUD-01 | **Firebase Admin SDK private key committed to git and pushed to `origin/master`** (github.com/myassin75/Notas). Commit `1c56146` (2026-10-24), blob in current tip. | **P0** | **Confirmed** | Holder can mint valid tokens impersonating any user, enumerate/delete users. **Fix:** rotate the key in Firebase console **now**, update Vercel `FIREBASE_PRIVATE_KEY`, then rewrite history (`git filter-repo`), add `*firebase-adminsdk*.json` to `.gitignore`, force-rotate `DATABASE_URL`/S3/Upstash if co-managed |
| AUD-02 | **Cross-user write via nested sync upserts** — `checklistItem.upsert`/`noteAttachment.upsert` update branches use `where: {id}` with **no userId/ownership check** (`backend/src/services/syncService.ts:218–246, 253–278`) | **P1** | **Confirmed** (read in source) | Attacker overwrites another user's checklist item/attachment content (incl. `storageKey`) by id. Fix: fetch-then-verify ownership (or composite unique `where`) |
| AUD-03 | **In-batch category ownership bypass** — when `note.categoryId` is in the push batch, validation is skipped on membership alone even if that category was rejected as cross-user/stale (`syncService.ts:114–122`) | **P1** | **Confirmed** (read in source) | Relational IDOR + FK-abort path (whole transaction 500). Fix: track validated category ids, not merely "in batch" |
| AUD-04 | **Account deletion never purges `attachments/{userId}/` S3 objects** — `deleteAllUserFiles()` hardcodes `backups/` prefix only (`storageService.ts:176`) | **P1** | **Confirmed** | GDPR/retention leak. Fix: delete both prefixes + lifecycle rule |
| AUD-05 | **Sync revision = `Date.now()` per push batch** → concurrent lambdas collide; pull uses `serverRevision > X` so records sharing a revision are silently skipped (`syncService.ts:17, 367/375`) | **P1** | **Confirmed** | Multi-device data loss. Fix: monotonic sequence counter |
| AUD-06 | **Pull pagination watermark skip** — per-entity `take` with one shared `maxRevision` watermark can advance past truncated lists; settings excluded from `hasMore` (`syncService.ts:370–404`) | **P1** | **Confirmed** | Fix: paginate per-model or compute watermark from the returned set of each list |
| AUD-07 | **Sync push transaction default 5s timeout vs unbounded sequential upsert loop** (≤500 notes + nested arrays) → large pushes die `P2028`→500 (`syncService.ts:26`) | **P1** | Confirmed | Fix: raise/-split with explicit `timeout`, chunk pushes |
| AUD-08 | **Notes DB + DataStore (incl. `lock_prefs` PIN hash/salt) included in Google cloud/device backup** — `allowBackup=true`; exclusion lists cover only file dirs (`AndroidManifest.xml:57`, `backup_rules.xml`, `data_extraction_rules.xml`) | **P1/P2** | **Confirmed** | SECRET/PRIVATE notes + PIN hash exfiltrated via user's Google account restore path; offline brute-force impossible-of-lockout. Fix: exclude `databases/`, `sharedpref/`, DataStore files |
| AUD-09 | **npm audit: 24 vulns (3 critical, 8 high, 13 moderate)** — notably `zod<4.6.6` high (DoS on large constrained collections; relevant: up-to-4MB `sync/push` bodies feed zod); `@vercel/node` high chain; firebase-admin moderate; dev: tar critical, vitest tinypool RCE-class | **P1/P2** | **Confirmed** (`npm audit` executed) |
| AUD-10 | **Cloud backups are plaintext zips while UI says "Uploading encrypted backup to cloud"** (`CloudBackupManager.kt` ~115–204; server default `isEncrypted:false`) | **P2** | **Confirmed** | Fix: implement client-side envelope encryption or correct the copy |
| AUD-11 | **`empty ?path=` duplicate query param crashes the function before try/catch** — `req.query.path` becomes `string[]` → TypeError (`api/index.ts:57`) | **P2** | Confirmed | Fix: normalize then validate |
| AUD-12 | **CORS defaults to `*` + `Access-Control-Allow-Credentials: true`** when `ALLOWED_ORIGINS` unset (`api/index.ts:32–48`) | **P2** | Confirmed | Fix: fail closed in production; drop credentials header |
| AUD-13 | **Attachment upload size limits decorative** — presigned PUT has no ContentLength condition; verify never compares declared size (50MB cap unenforced) vs backups which do enforce (`attachmentSchemas.ts:8`, `storageService.ts:51–57`, `attachmentStorageService.ts:44–83`) | **P2** | Confirmed | Storage-billing DoS per user |
| AUD-14 | **Route access: `authenticate` has env-conditional swallow** (`auth.ts:147–151`) and untested `IDENTITY_VERIFICATION_FAILED` branch; `checkRevoked` only for `DELETE /me` (revocation window elsewhere) | **P2/P3** | Partial | Tighten + unit test |
| AUD-15 | **Rate limiter strictly per-IP pre-auth** (`api/index.ts:84` before auth; `req.user` branch dead): shared egress users compete for a single 120/min bucket; `x-forwarded-for` first-hop trusted | **P2/P3** | Confirmed | Move after auth for user keys; trust platform IP only |
| AUD-16 | S3 config **fails silently** with `dummy-access-key`/`'notas-user-backups'` fallbacks (`storageService.ts:20–40`) | **P2** | Confirmed | Fail fast on missing creds |
| AUD-17 | Rate-limit env vars `RATE_LIMIT_*_PER_MINUTE` documented in `.env.example` but **read by no code** (hardcoded 120/min) | **P3** | Confirmed |
| AUD-18 | **Protocol/DoS:** `zod.photoUrl` accepts non-HTTPS schemes; `deviceId` free-form → unbounded device rows | **P3** | Confirmed | Constrain |
| AUD-19 | FileProvider exposes the whole cache root (`backup_file_paths.xml` cache-path `./`), including transient backup zips | **P3** | Confirmed | Scope to subdirs |
| AUD-20 | No TLS pinning on `BACKEND_API_BASE_URL`; HTTPS-only by default, no cleartext, redirect-safe clients without token forwarding | **P3** | Confirmed | Defer unless SECRET notes marketed; prefer E2E encryption (AUD-10) |
| AUD-21 | Room DB plaintext; app lock is a UI gate; PBKDF2 PIN (50k iters, salt, constant-time) good, but **no wrong-PIN throttling** | **P3** | Confirmed | Throttle attempts; consider SQLCipher if threat model requires |
| AUD-22 | Debug/devRelease builds embed staging URL; `devRelease` signed with debug key but minified; **staging backend shares the production Firebase project's users base** (`app/build.gradle.kts:124–149`) | **P3** | Confirmed | Use isolated staging Firebase project |
| AUD-23 | App Check absent (no attestation for API calls) | **P3** | Confirmed | Consider Firebase App Check enforcement |
| POSITIVE | SQL injection:**none** — all Prisma access parameterized; `$queryRaw` only health + re-key (tagged template); no `$queryRawUnsafe`; zod everywhere on routes; no missing auth on business routes; logger redacts `authorization/token/secret`; no token logging in app; notification redaction for SECRET notes; `google-services.json` contains identifier-class values only (acceptable); no WebView surface; no debug logging of secrets | — | Confirmed |

---

## 6. Testing & Verification (executed + inspected)

### 6.1 Executed results

| Command | Result |
|---|---|
| `./gradlew :app:testGithubDebugUnitTest` | ✅ **692 tests / 79 classes, 0 failures, 0 errors, 0 skipped** |
| Backend `npx vitest run` | ✅ **66/66 passed (8 files)** |
| Backend `npx tsc --noEmit` | ❌ 1 error (unused import, `tests/middleware.test.ts:5`) |
| Backend `npx prisma validate` | ✅ schema valid |
| `cd backend && npm audit` | ❌ 24 vulnerabilities (see AUD-09) |
| `./gradlew :app:detekt` | ❌ 88 issues (thinking §4.3) |
| `./gradlew :app:ktlintGithubDebugSourceSetCheck` | (ran as part of the static pass) |
| `./gradlew :app:lintGithubDebug` | ❌ 7 errors, 265 warnings |

### 6.2 Coverage shape

- **Android:** 687 unit tests across 83 files + 20 instrumented tests (4 files). Strong: markdown editor (~300 tests), sync engine conflict/rollback/tombstone tests, BackendApiClient fake-HTTP tests, reminders suite, calendar. Weak/1-test files: `FirebaseAuthRepositoryTest` (1), `GuestProfileViewModelTest` (1), migrations (1 each on-device only). **Untested areas:** sync-serving engine parts beyond core, GoogleTasks network layer, trash sweep worker, quick capture, quick settings, notifications, DI wires, all flavor-specific code, zero Compose UI/Espresso tests.
- **Backend:** 66 vitest tests, all Prisma-mocked (no integration/DB tests). Mock fidelity is honest; README-precise. Untested: nested-IDOR (AUD-02), in-batch bypass (AUD-03), revision collision (AUD-05), pagination skip (AUD-06), timeout (AUD-07), attachment size enforcement (none exists). The suite **encodes today's bugs as correct behavior** in some places (notably pull pagination).
- **`middleware.test.ts` ↔ current `auth.ts`:** aligned (adapted together); no contract drift; but the new fail-closed `getUser`-error branch has **no test**.
- **CI gaps:** backend CI does not run lint (broken anyway); **no Android unit tests/detekt/ktlint/lint in any workflow**; no coverage gates anywhere; no SCA/secret scanning (which is why AUD-01 shipped).

### 6.3 Not verified (limitations)

Instrumented tests not run (no emulator); no R8 release build attempted; live deployment behavior (`api.smartnotas.com`, staging Vercel URL), Firebase console restrictions, real device alarm/network behavior, visual UI, and **all performance numbers** were not measured.

---

## 7. Performance & Reliability (code-level risks only — nothing was profiled)

- Room queries indexed for the main patterns (FTS4 + content sync triggers). `/me/stats` performs 6 `count` scans without `(userId, isDeleted, kind)`/`(userId, endAt)` composites — cheap but repeated.
- `listUserBackups` unbounded (`backupService.ts:114`) — serialized response grows with user's backup history.
- Backend push loop N+1-by-design inside one `$transaction` (AUD-07 severity ties performance to correctness).
- Browser/UI: home arrangement engine recomputes 6 `derivedStateOf` collections per change (`HomeScreen.kt:366–422`); large screens are heavy but not measured.
- APK/payload hygiene: 65MB tracked debug APK inflates repo; no app-size concern measured.
- Reliability-of-record verified: whole push atomic (single transaction), rollback on failure; restore failure surfaces to the user; trash sweep daily; boot re-arms reminders. No measured incidents → risks only.

---

## 8. Database & Backend Audit (condensed from Phase 7)

- **Schema** single init migration, additive; FK `ON UPDATE CASCADE` consistent with the auth re-key raw UPDATE; missing **FK on `checklist_items.userId`/`note_attachments.userId`** (orphan risk, ties to AUD-02); tombstones/PENDING backups/device cursors never GC'd (no Vercel crons; `cleanupAbandonedBackups` is dead code).
- **Contract:** user-scoped everywhere except AUD-02/03; envelope + error taxonomy consistent; raw Prisma objects never returned; DTO field shapes match client (`sizeBytes` string-vs-Long only works because `Json{isLenient=true}` — fragile).
- **Client↔server drift:** none blocking; `reminders/recurrence/actions` server columns accepted but the client never sends them (feature drift, not corruption).
- **Auth middleware races:** new-user create path not in upsert/transaction → cold-start `P2002`→500 possible (P2).
- **Vercel rewrite** `/api/v1/(.*) → /api/index?path=$1` correct except AUD-11 (unhandled parsing).

---

## 9. Findings & Severity Index

**P0 (1):** AUD-01 leaked admin key.
**P1 (9):** AUD-02 cross-user write · AUD-03 category bypass · AUD-04 attachment retention · AUD-05 revision collision · AUD-06 pagination skip · AUD-07 transaction timeout · AUD-08 backup of DB+PIN hash · AUD-09 (high) zod-class dependency risk · plus **AUD-24: Sync watermark ≥500 items data loss** (`SyncEngine.kt:171–178` pushes `take(500)`; watermark advance at `:206–215` counts **full** `modifiedShoulders/Notes` list — **manually verified in source**) and **AUD-25: Sync engine never invoked** (feature does not run at all; completeness P1 rather than bug).
**P2 (21):** F1 prefill · F2 quick-capture toggle · F3 offline flavor cloud UI · AUD-10/11/12/13/16 · tsc/eslint breakage · lint errors (7) · detekt 88 (aggregated) · ripple suppression · hardcoded colors/theme bypass · auth-screen duplication · data→ui layer violations · god files (aggregate) · auth middleware race · unbounded backups list · inline error middleware/swallows · CI gaps (no Android checks in CI; no lint in backend CI; no coverage; no secret scanning) · guest upgrade abruptness (UX) · Sign-Out-no-confirm · dead code inventory (aggregated; baselineprofile module orphan) · stale schema package + scratch files at root.
**P3 (14):** AUD-14,15,17–23 as listed · blocking_roll · no rail on tablets · reduced-motion for History · heading semantics absent · hardcoded English contentDescriptions · language drift (`sizeBytes`), navigation flags (unused `LEGACY` import icons), naming sloths (`darckP`), `damaged` token / `Toolbar`-style stuff confirmed but cosmetic.

(Counts of P1/P2 aggregate cluster-level findings; AUD numbering is the authoritative registry.)

---

## 10. Production Readiness verdict

### **NOT READY**

Rationale (evidence-based):
1. **A P0 secret is live in GitHub history** (AUD-01). Until the key is rotated, admin compromise of the Firebase project is a standing risk to every user of the backend.
2. **The headline cloud-sync feature does not actually run** (AUD-25), and even if enabled, four independent P1 defects (AUD-02, AUD-05, AUD-06, AUD-07 + client AUD-24) would cause **silent data loss and cross-user writes** — unacceptable for a notes-sync product.
3. **Verified verification failures** carried in the main line: backend `tsc` (will fail CI), ESLint unconfigured, Android lint 7 errors, detekt 88 issues.
4. Account deletion is a privacy/GDPR gap (AUD-04) and local note content + PIN material is cloud-backed up to Google in plaintext (AUD-08).

Strengths keeping it not-worth-destroying: exemplary card/app engineering (editor, reminders, adaptive UI), real test suites (692 + 66 passing), correct auth-verification core, clean schema/migration, no injection-class vulnerabilities, and a sane flavor/DI structure. With the P0/P1 security-and-data-loss list closed and the cloud wiring completed, this is realistically **2–4 focused weeks from READY WITH CONDITIONS**.

---

## 11. Remediation Plan

### Phase 1 — Critical (blocks any production) — target: days
1. **AUD-01:** Rotate Firebase service-account key; update Vercel env; purge git history; add secret pattern to `.gitignore`; run secret scanning retro; rotate co-located credentials if cult risk.
2. **AUD-25 + AUD-24:** Wire `SyncEngine` into a post-login hook + WorkManager periodic job; fix the ≥500 watermark bug (iterate pushes until drained, or advance watermark only over pushed slice).
3. **AUD-02/03:** Ownership-guard nested upserts; validated-category-id tracking.
4. **AUD-08:** Exclude `databases/`, `sharedpref/`, DataStore from `backup_rules.xml` and `data_extraction_rules.xml`; throttle PIN attempts.
5. **AUD-07:** Explicit transaction `timeout` + chunked pushes; **AUD-06:** per-model lazy pagination and watermark sanity.
6. **AUD-09:** Upgrade zod→v4 (plan migration), `@vercel/node`, firebase-admin, vitest in a reviewed PR; add SCA to CI.

### Phase 2 — High (before public launch) — target: 1–2 weeks
7. **AUD-04:** Purge `attachments/` on account deletion + lifecycle rule for unverified uploads; **AUD-13:** ContentLength condition + size-compared verify.
8. **AUD-10:** Implement E2E envelope encryption for backups (or fix the UI copy), **AUD-12** CORS fail-closed, **AUD-16** fail-fast S3 config, **AUD-11** path param hardening, **AUD-15/AUD-14** rate-limit placement + token revocation policy.
9. Wire backend `lint` (add eslint.config.mjs) + fix `tsc`; add Android unit tests/detekt/ktlint/lint to CI; add coverage gates; add secret scanning.
10. Complete the dormant features or remove them now: sync status UI (`SyncStatusIndicator`), attachment manager, PATCH `/me`, DELETE `/me`, GET `/me/stats` — each needs a deliberate "ship or delete" decision.
11. Fix F1 (single VM scope for auth flow), F2 (`observeQuickCapturePref`), F3 (flavor-gate the Backup section).

### Phase 3 — Medium — target: next iteration
12. Extract shared HTTP client (`data/net/`); de-duplicate auth screens (AuthScaffold); fix data→ui layering (move `SyncStatus` into `data/core`); split `CloudBackupManager`; move `CalendarViewModel` layout math into `domain/`.
13. UX pass: ripple default (`indication = LocalIndication.current`), Sign-out confirmation, Sync-Now feedback, Home loading state, History loading flag, tablet nav rail, reduced-motion for History, heading semantics, localization of contentDescriptions.
14. Refactor detekt thresholds to a lint-first curve; retire the UI exclusions; burn down `UnusedParameter` (57) via a config pass, not suppression; register or delete `baselineprofile/`; prune legacy schemas and root scratch files; untrack APKs (use Releases).

### Phase 4 — Improvements & optimization
15. Edge-to-edge/theme consolidation (retire hardcoded color lits), `WindowWidthSizeClass` alignment, widget rail.
16. Tentpole testing: emulator job in CI (connected tests), Compose UI tests for auth/home/editor, Prisma integration tests against a test DB (kills the "mocks encode bugs" class), detekt for backend (currently absent).
17. Performance: add composites for `/me/stats`, `take` on backups list, pagination on backups, app start/baselineprofile run (once the module is registered), memory profiling of Home arrangement.
18. Hygiene: `google-services.json` restrictions (SHA-1/SHA-256) in Firebase console, App Check evaluation, optional TLS pinning / E2E for SECRET notes, Dependabot for the Android catalog (present for backend/gh already).

---

## 12. Phase 1 Remediation Status (applied 2026-10-29)

Phase 1 code fixes were implemented and verified. **The report verdict remains NOT READY** — AUD-01 (live secret in git history) is a manual operation outside code remediation, AUD-25 (sync wiring) and AUD-04 (S3 retention) are outside Phase 1 scope, and P2/P3 findings are open.

### 12.1 Fixes applied

| Item | Finding | Fix | Files |
|---|---|---|---|
| 1 | AUD-24 (client watermark ≥500 data loss) | Push now drains **all** pending records in 500-item batches (max 100 cycles). Per-batch tombstone confirmation (top-level ids + child tombstones via parent `noteUuid`). The `lastPushedAt` watermark advances **only after a complete drain**, over applied-minus-conflict records; a partial drain leaves it untouched so the next sync re-pushes the remainder (idempotent under LWW). Mid-drain `Unauthorized/RateLimited/Error/Offline` abort immediately. | `app/.../data/sync/SyncEngine.kt` || 2 | AUD-02 (nested-IDOR cross-user writes) | `checklistItem`/`noteAttachment` upserts now `findUnique`-then-verify `userId` first; foreign rows are rejected as conflicts (`entityType: 'note'`, reason includes the entity and "belongs to another user") without touching the row. | `backend/src/services/syncService.ts` |
| 3 | AUD-03 (in-batch category bypass) | `batchCategoryIds` (membership) replaced by `validatedCategoryIds`, populated only **after** a category upsert succeeds; connection checks now fall through to the DB ownership check otherwise. | same |
| 4 | AUD-05 (revision collisions) | `serverRevision` now issued by per-process strictly-monotonic helpers (`nextRevision()` / `nextRecordRevision()`, sub-tick resolution 1_000/ms). Every applied record gets a unique revision; pull cursors can no longer share a value. **Note:** cross-lambda-instance collisions remain theoretically possible — the definitive fix is a database sequence (follow-up). | same |
| 5 | AUD-06 (pull pagination watermark skip) | Watermark now computed per truncation: `hasMore` = any list truncated; cursor advances only to the **lowest truncated-list ceiling** instead of the global max revision. Non-truncated results still advance to the global max. | same |
| 6 | AUD-07 (transaction timeout) | Push transaction gets explicit `{ timeout: 45_000, maxWait: 10_000 }` so legal max-size pushes stop dying with `P2028`. (Chunking at the API boundary is inherent: backend caps 500/model/request; client now honors it via the drain loop.) | same |
| 7 | AUD-08 (DB + PIN material in cloud/device backup) | `backup_rules.xml` / `data_extraction_rules.xml` now exclude `domain="database"` (`remember.db` + `-wal`/`-shm`), `domain="sharedpref"` (whole domain), and DataStore files (`domain="file" path="datastore/"`), for both cloud-backup and device-transfer scopes. | `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` |
| 8 | AUD-09 (dependency vulnerabilities, safe part) | `npm audit fix` (non-forced) applied: 1 package removed, `gaxios` 6.3.0 → 6.7.1. All remaining 22 advisories (12 moderate / 7 high / 3 critical) sit inside `@vercel/node` (≥2.1 → needs 28) and `vitest` (4.x → needs 5) trees — **breaking majors, deferred to a reviewed upgrade PR** | `backend/package-lock.json` |
| 9 | Pre-existing `tsc` failure | Removed unused `getFirebaseAdmin` import in `tests/middleware.test.ts` (disclosed separately from the audit findings). | `backend/tests/middleware.test.ts` |

### 12.2 Regression tests added

- **Backend** (`backend/tests/sync.test.ts`, 7 existing sync tests → 18): AUD-02 nested checklist-item + attachment IDOR and happy path; AUD-03 in-batch bypass + validated-category happy path; AUD-05 unique revisions within one push + strict monotonicity across pushes in the same millisecond; AUD-07 explicit transaction options; AUD-06 cursor capped at truncated ceiling, lowest-of-two-ceilings, and no-truncation sanity.
- **Android** (`app/src/test/.../data/sync/SyncEngineTest.kt`): 600-note drain → two batches with correct 500/100 split and watermark = max applied; second batch fails mid-drain → watermark untouched (the AUD-24 data-loss path); all-records-conflicted → watermark untouched.

### 12.3 Verification executed

| Command | Result |
|---|---|
| `backend: npx vitest run` | ✅ **77/77 passed (8 files)** — was 66/66 before Phase 1 |
| `backend: npx tsc --noEmit` | ✅ exit 0 (was ❌ 1 error) |
| `backend: npx prisma validate` | ✅ valid |
| `backend: npm audit fix` (no `--force`) + follow-up `npm audit` | ⚠ safe subset applied; 22 remaining (all in `@vercel/node`/`vitest` trees, breaking) |
| `./gradlew :app:testGithubDebugUnitTest` | ✅ **695 tests (692 baseline + 3 new AUD-24 regressions), 0 failures, 0 errors, 0 skipped** — includes the rewritten `SyncEngine.kt` push phase |
| `./gradlew :app:lintGithubDebug` / detekt | ❌ pre-existing failures (7 lint errors / 88 detekt issues), unchanged — out of Phase 1 scope |

### 12.4 Remaining manual actions (required before any deploy)

1. **AUD-01 (P0) — Firebase Admin key rotation and history purge (manual, do not paste keys anywhere):**
   1. Firebase console → Project settings → Service accounts → generate a **new** private key; delete/disable the leaked key immediately.
   2. Update `FIREBASE_PRIVATE_KEY` (+ `FIREBASE_CLIENT_EMAIL`/`PROJECT_ID` if changed) in the Vercel staging **and** production environments; redeploy.
   3. Purge the key from git: `git filter-repo --invert-paths --glob '*firebase-adminsdk*.json'` (or BFG), then force-push and coordinate with collaborators on fresh clones.
   4. Add `*firebase-adminsdk*.json` (and the exact committed filename) to `.gitignore`; run secret scanning retro (GitHub secret scanning / gitleaks) and rotate `DATABASE_URL`/S3/Upstash credentials if they were committed alongside anything sensitive.
   5. The removed old key should be marked as rotated in the incident notes; rotation invalidates tokens signed by it only after the SDK config is redeployed — verify `/api/health` and an authenticated call afterwards.
2. **AUD-04 / AUD-13** and the sync-engine **wiring (AUD-25)** remain open (Phase 1/2 items not in this pass's scope).
3. Android instrumented tests, release/R8 build, lint and detekt were not re-run in Phase 1 (pre-existing failures tracked in §6.1).

---

*Report compiled from six specialized audit passes with first-hand verification of every P0/P1 claim (`git ls-files`, `git log`, `npm audit`, source reads, executed builds/tests). Full agent transcripts retained in session history. The report file itself is the only artifact the audit added to the repo.*

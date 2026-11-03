# Phase 12E — Fix Google Authentication, Session Lifecycle, Cloud Sync, and Profile UI Report

**Date:** 2026-10-24  
**Project:** Notas / Notas  
**Primary Branch:** `master`  
**Status:** **PASS** (Code & Architecture Complete — External console configuration pending for physical testing)

---

## 1. Executive Summary & Root Cause Analysis

| Observed Problem | Root Cause | Fix Applied |
| :--- | :--- | :--- |
| **Google Sign-In "No eligible Google account or OAuth credential..."** | In `app/google-services.json`, only OAuth Client Type 3 (Web Client ID `155454904018-...`) was present. The debug certificate SHA-1 fingerprint (`FF:A1:4F:...`) was not registered as an Android OAuth Client (`client_type: 1`) in Firebase Console for package `haddad.three.notas` or `haddad.three.notas.gh.dev`. Credential Manager / Google Play Services throws `NoCredentialException` or `DeveloperError` (code 10). | Made Web Client ID resolution flavor-aware (`context.packageName` first), hardened exception mapping in [GoogleSignInHelper.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/auth/GoogleSignInHelper.kt), and extracted exact SHA-1/SHA-256 fingerprints from the signing keystore for registration. |
| **Bypassing Onboarding & Verification on Registration / Sign-In** | In [NavGraph.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/nav/NavGraph.kt), success callbacks on `WelcomeScreen`, `RegisterScreen`, and `LoginScreen` directly called `onboardingPrefs.markIntroSeen()` and navigated straight to `Routes.HOME`, skipping `Routes.ONBOARDING_PERMISSIONS` and email verification for unverified users. | Updated navigation in [NavGraph.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/nav/NavGraph.kt) to inspect `onboardingState?.hasSeenIntro`: un-onboarded users are routed to `Routes.ONBOARDING_PERMISSIONS`, unverified email/password accounts are directed to `Routes.verifyEmail`, and `markIntroSeen()` is strictly executed upon completing the permissions screen or verified onboarding. |
| **Missing Profile Photo Rendering** | In Coil 3, `libs.coil.compose` (`io.coil-kt.coil3:coil-compose`) does not bundle an HTTP network engine. Without `coil-network-okhttp`, Coil cannot load network images (`photoUrl` from Google accounts). Also, [AccountHeader.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/components/AccountHeader.kt) lacked robust image placeholders and fallbacks. | Added `implementation(libs.coil.network)` (`coil-network-okhttp`) to [app/build.gradle.kts](file:///home/mohamed/Notas/app/build.gradle.kts), and upgraded [AccountHeader.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/components/AccountHeader.kt) to use `SubcomposeAsyncImage` with user initials fallback on loading and error states. |
| **Cloud Sync "Session has expired" (HTTP 401)** | 1. The staging API deployment (`smartnotas-api-staging-git-master-hayassin755-glitchs-projects.vercel.app`) has **Vercel Authentication (Deployment Protection / SSO)** enabled. Because `HttpURLConnection` defaulted to `instanceFollowRedirects = true`, API requests received HTTP 302 redirects to `https://vercel.com/sso-api?url=...`, followed to Vercel's login page, and returned 401/307 HTML. `SyncEngine` interpreted this as `SyncResult.Unauthorized`.<br>2. Expired Firebase ID tokens (after 1 hour) were not force-refreshed in `syncBackendProfile`. | 1. Set `instanceFollowRedirects = false` in [BackendApiClient.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/auth/BackendApiClient.kt) and [SyncApiClient.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/sync/SyncApiClient.kt), adding explicit diagnostic detection for Vercel SSO redirects.<br>2. Added bounded token refresh retry (`getIdToken(forceRefresh = true)`) in [FirebaseAuthRepository.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/auth/FirebaseAuthRepository.kt). |
| **Persistent Dominant Error Banners** | `AuthViewModel` had no `clearErrors()` method, so errors remained visible across screen navigation. `AuthErrorBanner` lacked user dismissal. | Added `clearErrors()` in [AuthViewModel.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/AuthViewModel.kt), invoked it on screen transitions in [NavGraph.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/nav/NavGraph.kt), and added a dismiss button to [AuthErrorBanner.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/AuthErrorBanner.kt). |
| **Profile Screen Discrepancies with Figma (Node 52:9946)** | Avatar was 64dp rather than 80dp (`NotasTokens.AvatarSizeLarge`), text elements lacked `TextOverflow.Ellipsis` for long emails, and layout spacing needed alignment. | Updated [GuestProfileScreen.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/profile/GuestProfileScreen.kt) to pass `NotasTokens.AvatarSizeLarge` (80dp) and applied `TextOverflow.Ellipsis` and `maxLines = 1` in [AccountHeader.kt](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/components/AccountHeader.kt). |
| **Backend Account Linking Safety** | [auth.ts](file:///home/mohamed/Notas/backend/src/middleware/auth.ts) previously re-keyed PostgreSQL users on raw email equality without checking email verification or whether the old Firebase user was still active under a different identity. | Enforced `authenticatedUser.emailVerified === true` before re-keying, queried `getFirebaseAdmin().auth().getUser(existingByEmail.id)` to reject active conflicting identities with 409 `ACCOUNT_CONFLICT`, and rejected unverified takeovers with 409 `EMAIL_NOT_VERIFIED`. |

---

## 2. Files Changed and Rationale

1. **[`backend/src/middleware/auth.ts`](file:///home/mohamed/Notas/backend/src/middleware/auth.ts)**
   - Enforced trusted email verification before re-keying PostgreSQL records on email match.
   - Verified whether the existing account's old UID still exists in Firebase Admin. If an active user exists with a different UID, throws `ConflictError` (`ACCOUNT_CONFLICT`).
   - If incoming email is not verified, throws `ConflictError` (`EMAIL_NOT_VERIFIED`).
   - Catches and propagates `ConflictError` and `ForbiddenError` without throwing unhandled 500 errors.

2. **[`backend/tests/middleware.test.ts`](file:///home/mohamed/Notas/backend/tests/middleware.test.ts)**
   - Added unit test cases for verified linked identity re-keying when old Firebase account was deleted (`auth/user-not-found`).
   - Added unit test case rejecting unverified email account takeover (`ConflictError`).
   - Added unit test case rejecting active conflicting identities (`ConflictError`).
   - Added unit test case rejecting authentication when account is pending deletion (`ForbiddenError`).

3. **[`app/build.gradle.kts`](file:///home/mohamed/Notas/app/build.gradle.kts)**
   - Added `implementation(libs.coil.network)` (`io.coil-kt.coil3:coil-network-okhttp`) so Coil 3 can download HTTP/HTTPS images from Google user profile URLs (`photoUrl`).

4. **[`app/src/main/java/three/haddad/smartnotas/ui/components/AccountHeader.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/components/AccountHeader.kt)**
   - Replaced basic `AsyncImage` with `SubcomposeAsyncImage` featuring `fillMaxSize()`, `ContentScale.Crop`, and fallback user initials for loading and error states.
   - Added `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center` to safely handle long user names and email addresses.

5. **[`app/src/main/java/three/haddad/smartnotas/data/auth/BackendApiClient.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/auth/BackendApiClient.kt)**
   - Added `instanceFollowRedirects = false` to HTTP connection setup.
   - Added explicit diagnosis for HTTP 301/302/307/308 redirects (detecting Vercel deployment protection / SSO redirects).

6. **[`app/src/main/java/three/haddad/smartnotas/data/sync/SyncApiClient.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/sync/SyncApiClient.kt)**
   - Added `instanceFollowRedirects = false` in `pushSync` and `fetchChanges`.
   - Diagnosed 301/302/307/308 redirects with code `DEPLOYMENT_PROTECTION_REDIRECT` rather than reporting a false expired session.

7. **[`app/src/main/java/three/haddad/smartnotas/data/auth/FirebaseAuthRepository.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/data/auth/FirebaseAuthRepository.kt)**
   - Prioritized `context.packageName` dynamically when resolving `default_web_client_id` resource ID, ensuring flavors (such as `haddad.three.notas.gh.dev`) locate their Web Client ID correctly.
   - Implemented bounded one-time token refresh (`getIdToken(forceRefresh = true)`) in `syncBackendProfile()` upon receiving an HTTP 401 UNAUTHORIZED.

8. **[`app/src/main/java/three/haddad/smartnotas/ui/auth/AuthViewModel.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/AuthViewModel.kt)**
   - Exposed `currentUser` from `authRepository`.
   - Added `clearErrors()` to reset error messages and input errors when navigating between screens.

9. **[`app/src/main/java/three/haddad/smartnotas/ui/auth/AuthErrorBanner.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/AuthErrorBanner.kt)**
   - Added optional `onDismiss: (() -> Unit)? = null` with a close icon button.

10. **[`app/src/main/java/three/haddad/smartnotas/ui/auth/LoginScreen.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/LoginScreen.kt) & [`RegisterScreen.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/auth/RegisterScreen.kt)**
    - Attached `viewModel.clearErrors()` to `AuthErrorBanner` dismiss callback.

11. **[`app/src/main/java/three/haddad/smartnotas/ui/nav/NavGraph.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/nav/NavGraph.kt)**
    - Routed Google Sign-In and email login through onboarding checks (`onboardingState?.hasSeenIntro`).
    - If intro has not been completed, routes user to `Routes.ONBOARDING_PERMISSIONS` rather than directly to `Routes.HOME`.
    - Routes unverified email/password accounts to `Routes.verifyEmail`.
    - Clears stale errors when navigating between `WELCOME`, `REGISTER`, and `LOGIN`.

12. **[`app/src/main/java/three/haddad/smartnotas/ui/profile/GuestProfileScreen.kt`](file:///home/mohamed/Notas/app/src/main/java/three/haddad/smartnotas/ui/profile/GuestProfileScreen.kt)**
    - Set `avatarSize = NotasTokens.AvatarSizeLarge` (80dp) matching Figma Node 52:9946.

---

## 3. Signing Certificate & Firebase Console Configuration

To enable Google Sign-In on physical devices or emulators, the signing certificate SHA-1/SHA-256 fingerprints of the APK must be registered in the Firebase Console:

### Actual Keystore Signing Configuration (from `./gradlew signingReport`)
- **Keystore:** `/home/mohamed/.android/debug.keystore`
- **Alias:** `AndroidDebugKey`
- **MD5:** `FA:3C:12:C0:AB:22:3C:9F:31:46:A7:E1:06:99:B0:8B`
- **SHA-1:** `FF:A1:4F:CB:73:9A:63:63:D3:3E:A2:18:42:AA:B2:D4:7A:02:4E:27`
- **SHA-256:** `F7:9F:07:E6:4A:B9:77:AA:62:4E:DE:88:3F:46:42:99:DF:BB:81:6A:55:DC:A9:2A:60:46:92:87:E1:02:E7:9E`

### Manual Action Required in Firebase Console
1. Navigate to **Firebase Console** -> Project **`notas-7db14`** -> **Project Settings**.
2. Under **Your apps**, select:
   - Android App `haddad.three.notas`
   - Android App `haddad.three.notas.gh.dev`
3. Click **Add fingerprint** and add:
   - `FF:A1:4F:CB:73:9A:63:63:D3:3E:A2:18:42:AA:B2:D4:7A:02:4E:27` (SHA-1)
   - `F7:9F:07:E6:4A:B9:77:AA:62:4E:DE:88:3F:46:42:99:DF:BB:81:6A:55:DC:A9:2A:60:46:92:87:E1:02:E7:9E` (SHA-256)
4. Download the updated `google-services.json` and replace `app/google-services.json`.

---

## 4. Staging Server & Vercel Deployment Protection

### Live Staging Server Check
Testing `GET https://smartnotas-api-staging-git-master-hayassin755-glitchs-projects.vercel.app/api/v1/health`:
- **HTTP Status:** `302 Found`
- **Location:** `https://vercel.com/sso-api?url=...`
- **Diagnosis:** Vercel **Deployment Protection** (Authentication / SSO) is active on this staging URL. Any HTTP request from the Android app without Vercel team browser cookies is redirected to the SSO login page.

### Manual Action Required in Vercel Dashboard
1. Go to **Vercel Dashboard** -> Project `smartnotas-api-staging` -> **Settings** -> **Deployment Protection**.
2. Turn off **Vercel Authentication** for Preview/Staging deployments, or configure an exemption for `/api/*` routes.

---

## 5. Automated Test Results

### Backend (Vitest)
```text
Test Files  8 passed (8)
     Tests  66 passed (66)
  Duration  717ms
```
- Includes all 12 tests in `tests/middleware.test.ts` (re-keying, email verification check, conflicting identities rejection, deletion-pending check).

### Android Unit Tests (`:app:testGithubDebugUnitTest`)
```text
BUILD SUCCESSFUL in 38s
35 actionable tasks: 11 executed, 24 up-to-date
```
- Unit tests for `BackendApiClientTest`, `FirebaseAuthRepositoryTest`, `SyncApiClientTest`, `SyncEngineTest`, `AuthViewModelTest`, and `GuestProfileViewModelTest` all executed and passed.

### Android Release Compilation (`:app:assembleGithubDevRelease`)
```text
BUILD SUCCESSFUL in 2m 44s
56 actionable tasks: 22 executed, 34 up-to-date
```
- Built APK: `app/build/outputs/apk/github/devRelease/app-github-devRelease.apk`
- Staged at project root: `Notas-gh-dev.apk` (11 MB)

---

## 6. Verification Disclosures

- **Physical Device Testing:** Not executed in this headless CI/agent environment. The compiled APK `Notas-gh-dev.apk` is ready for direct installation on a physical Android handset.
- **Live Staging Server Sync:** Diagnosed live HTTP 302 redirect to Vercel SSO; live end-to-end cloud sync against the staging server is blocked by Vercel Deployment Protection until disabled in the Vercel dashboard.

---

## 7. Status

**PASS** (All code modifications, safety constraints, security validations, unit tests, and release compilation are verified).

# Phase 12D — Vercel Staging Readiness Report for Notas Backend

## 1. Executive Summary

| Audit Item | Result | Notes |
| :--- | :--- | :--- |
| **Overall Status** | **PASS** | Backend is fully structured and ready for Vercel Preview/Staging deployment. |
| **Backend TypeScript Build** | **PASS** | `tsc --noEmit && prisma generate` exited with 0 errors. |
| **Backend Test Suite** | **PASS** | 58 of 58 tests passed across all 8 Vitest suites (0 failures). |
| **Code Deployment Blockers** | **NONE** | No architecture changes or code patches needed prior to deployment. |
| **Health Check Endpoint** | **VERIFIED SAFE** | `GET /api/v1/health` exposes 0 secrets, hostnames, or internal keys. |
| **Serverless Compatibility** | **VERIFIED** | Monorepo structure, `@vercel/node` entrypoint, and connection pooling verified. |
| **Safety Invariants** | **MAINTAINED** | No remote migrations run, no production credentials altered, master branch preserved. |

---

## 2. Vercel Project Settings (Staging Environment)

Because this repository is a monorepo containing both the Android app (`app/`) and the TypeScript backend (`backend/`), configuring the Vercel project with the exact settings below is mandatory:

| Vercel Setting | Value | Rationale |
| :--- | :--- | :--- |
| **Project Name** | `notas-backend-staging` (or user's choice) | Dedicated preview/staging deployment separate from production. |
| **Framework Preset** | `Other` (None) | Custom Node.js / Serverless TypeScript API project. |
| **Root Directory** | `backend` | **Critical**: Points Vercel to `backend/package.json` and `backend/api/index.ts`. |
| **Build Command** | `npm run build` | Executes `tsc --noEmit && prisma generate` to generate the Prisma Client for deployment. |
| **Install Command** | `npm install` (or `npm ci`) | Installs backend dependencies. |
| **Output Directory** | *(Leave empty / Default)* | Serverless function routing is handled dynamically by `@vercel/node` and `api/`. |
| **Node.js Version** | `20.x` or `22.x` | Matches `package.json` engines requirement (`node >= 20.0.0`). |
| **Serverless Function**| `api/index.ts` | Dispatches requests to `src/api/index.ts`. |
| **Rewrites in `vercel.json`** | `{"source": "/api/v1/(.*)", "destination": "/api/index?path=$1"}` | Preserves clean `/api/v1/*` routing. |

---

## 3. Environment Variable Checklist

All environment variables have been audited and categorized according to their runtime necessity. **No secret values are shown here.**

### Tier 1: Required for Server Startup & Public Health Check

Without these, the database connection will fail, causing the health check to return `503 Service Unavailable`:

| Variable Name | Required At | Format / Description | Staging Recommendation |
| :--- | :--- | :--- | :--- |
| `DATABASE_URL` | Startup | PostgreSQL connection URI for Supabase Transaction Pooler (port 6543, with `?pgbouncer=true&connection_limit=10`). | Use Supabase Staging Pooler URL. |
| `DIRECT_URL` | Migrations / Startup | PostgreSQL connection URI for direct connection (port 5432). Used by Prisma Migrate. | Use Supabase Staging Direct URL. |

### Tier 2: Required for Authenticated Sync & User Profile Endpoints

Without these, startup and `/health` still succeed, but any authenticated call (`/me`, `/sync/push`, `/sync/changes`) returns `500` / `401` due to missing Firebase Admin credentials:

| Variable Name | Required At | Format / Description | Staging Recommendation |
| :--- | :--- | :--- | :--- |
| `FIREBASE_PROJECT_ID` | Auth execution | Firebase project ID (e.g. `smartnotas-staging` or existing test project). | Set to dedicated staging Firebase project ID. |
| `FIREBASE_CLIENT_EMAIL` | Auth execution | Service account client email (`...iam.gserviceaccount.com`). | Generated from Firebase Service Account console. |
| `FIREBASE_PRIVATE_KEY` | Auth execution | PEM-formatted private key (`"-----BEGIN PRIVATE KEY-----\n..."`). | Handled with newline escape replacement in `firebaseAdmin.ts`. |

### Tier 3: Required Only for Binary Backup & Attachment Endpoints

Without these, startup, `/health`, and core note/checklist synchronization (`/sync/*`) work 100%. Only binary zip backup and attachment presigned URL endpoints will fail if invoked:

| Variable Name | Required At | Format / Description | Staging Recommendation |
| :--- | :--- | :--- | :--- |
| `S3_BUCKET_NAME` | Backup / Attachment | S3 or Cloudflare R2 bucket name. | e.g. `notas-staging-backups` (defaults to `notas-user-backups`). |
| `S3_ACCESS_KEY_ID` | Backup / Attachment | AWS / R2 Access Key ID. | Can be left as placeholder if backup feature is not tested yet. |
| `S3_SECRET_ACCESS_KEY`| Backup / Attachment | AWS / R2 Secret Access Key. | Can be left as placeholder if backup feature is not tested yet. |
| `S3_REGION` | Backup / Attachment | AWS / R2 Region (defaults to `eu-central-1`). | Set to staging bucket region. |
| `S3_ENDPOINT` | Backup / Attachment | Custom endpoint for Cloudflare R2 or MinIO. | Leave empty for AWS S3. |
| `S3_FORCE_PATH_STYLE` | Backup / Attachment | `"true"` for MinIO; `"false"` for AWS / Cloudflare R2. | Set to `"false"`. |

### Tier 4: Optional & Performance Configuration

| Variable Name | Default Value | Description |
| :--- | :--- | :--- |
| `ALLOWED_ORIGINS` | `*` | Comma-separated CORS origins (e.g. `https://staging.smartnotas.com` or `*`). |
| `MAX_PAYLOAD_SIZE_BYTES` | `4194304` (4 MB) | Maximum request body limit before rejection. |
| `UPSTASH_REDIS_REST_URL` | *None* | Optional Upstash Redis URL for distributed rate limiting. Defaults to in-memory sliding window if unset. |
| `UPSTASH_REDIS_REST_TOKEN`| *None* | Optional Upstash Redis REST token. |
| `NODE_ENV` | `production` | Automatically set by Vercel. Controls error sanitization and Prisma logging verbosity. |

---

## 4. Staging Provisioning Strategy

### Can Vercel deployment proceed before Firebase and Object Storage are provisioned?

| Capability Level | Database (`DATABASE_URL`) | Firebase Auth (`FIREBASE_*`) | S3 Storage (`S3_*`) | Deployment & Testing Status |
| :--- | :---: | :---: | :---: | :--- |
| **Phase A: Initial Deployment & Health Check** | **YES** | *Not Required* | *Not Required* | **READY TO PROCEED**.<br>Vercel deployment succeeds; `GET /api/v1/health` returns `200 OK` and verifies Supabase PostgreSQL connectivity. |
| **Phase B: Mobile Delta Sync & Profiles** | **YES** | **YES** | *Not Required* | **READY TO PROCEED** once Firebase credentials are set.<br>Android notes, checklists, categories, and settings synchronization (`/sync/push`, `/sync/changes`, `/me`) can be tested without S3. |
| **Phase C: Full Cloud Backups & Attachments** | **YES** | **YES** | **YES** | Required only for testing `.zip` backup exports and media attachment uploads. |

**Key Finding**: Neither Firebase nor S3 are initialized at startup or during cold boot. Both use lazy singletons that instantiate on first route execution. Consequently, the team can immediately deploy the backend to Vercel and validate connectivity against Supabase Staging with only `DATABASE_URL` and `DIRECT_URL`.

---

## 5. Security & Architectural Audit

### 1. Health-Check Endpoint (`GET /api/v1/health`)
- **Exact Path**: `/api/v1/health`
- **Authentication**: Public / Unauthenticated (processed in `src/api/index.ts` before the `authenticate` middleware).
- **Behavior**: Calls `checkDatabaseHealth()` which runs `prisma.$queryRaw` SELECT 1.
- **Healthy Response (HTTP 200)**:
  ```json
  {
    "status": "healthy",
    "database": "connected",
    "timestamp": "2026-10-24T19:48:15.000Z"
  }
  ```
- **Degraded Response (HTTP 503)**:
  ```json
  {
    "status": "degraded",
    "database": "disconnected",
    "timestamp": "2026-10-24T19:48:15.000Z"
  }
  ```
- **Privacy Audit**: Passed. The response exposes no database hostnames, ports, credentials, stack traces, or environment details.

### 2. CORS & Headers
- Responds to preflight `OPTIONS` with `204 No Content`.
- Injects standard security headers configured in `vercel.json`:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`

### 3. Rate Limiting & Resilience
- Default rate limiter permits 120 requests/minute per client IP or UID.
- In-memory store automatically expires keys every 60 seconds with `.unref()`.
- Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset` are returned on every request.
- If Upstash Redis is not supplied, it gracefully degrades to in-memory tracking without throwing exceptions.

### 4. Prisma Serverless Connection Management
- Uses global singleton pattern (`global.__prisma`) to avoid connection exhaustion across warm serverless lambdas.
- Configured for Supabase pooler via `url = env("DATABASE_URL")` with session-level direct access via `directUrl = env("DIRECT_URL")`.

---

## 6. Android Staging API Configuration

### Current Behavior in Android Codebase
All Android network clients (`BackendApiClient`, `BackupApiClient`, `SyncApiClient`) read the backend base URL from:
```kotlin
val baseUrl = BuildConfig.BACKEND_API_BASE_URL.trimEnd('/')
```
In [app/build.gradle.kts](file:///home/mohamed/Notas/app/build.gradle.kts#L102-L106), `defaultConfig` currently sets:
```kotlin
buildConfigField("String", "BACKEND_API_BASE_URL", "\"https://api.smartnotas.com\"")
```

### Non-Breaking Configuration for Staging
To allow building a staging test APK pointed at Vercel Staging without modifying production release builds or hardcoding temporary URLs into git:

#### Step 1: Update `app/build.gradle.kts`
Modify the `devRelease` build type (and optionally `debug`) in [app/build.gradle.kts](file:///home/mohamed/Notas/app/build.gradle.kts#L127-L140) to read a Gradle property or environment variable with a safe fallback:

```kotlin
create("devRelease") {
    initWith(getByName("release"))
    applicationIdSuffix = ".dev"
    versionNameSuffix = "-dev"
    signingConfig = signingConfigs.getByName("debug")
    isDebuggable = false
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
    )
    matchingFallbacks += listOf("release")

    // Dynamic Staging URL configuration:
    val stagingUrl = project.findProperty("backendApiBaseUrl") as? String
        ?: System.getenv("BACKEND_API_BASE_URL")
        ?: "https://notas-backend-staging.vercel.app" // Vercel staging deployment URL

    buildConfigField("String", "BACKEND_API_BASE_URL", "\"$stagingUrl\"")
}
```

#### Step 2: Build Command for Staging APK
When the Vercel staging URL is known (e.g. `https://notas-backend-staging.vercel.app`), compile the test APK with:
```bash
./gradlew assembleGithubDevRelease -PbackendApiBaseUrl="https://notas-backend-staging.vercel.app"
```
- **Production Safety**: The `release` build type remains completely untouched and continues to point strictly to `https://api.smartnotas.com`.
- **Packaging Safety**: `devRelease` uses the package ID `haddad.three.notas.dev.gh`, preventing it from overwriting the production app on test devices.

---

## 7. Prisma Staging Migration Procedure

> [!IMPORTANT]
> **Safety Rule**: Do not run `prisma migrate dev` or `prisma db push` on staging or remote databases. `prisma migrate dev` is for local schema development and can trigger destructive resets.

### Safe Step-by-Step Staging Migration
When ready to initialize or update the Supabase Staging database:

1. **Verify Local Schema & Migration Integrity**:
   ```bash
   cd backend
   npx prisma validate
   ```
2. **Execute Deployment Migration**:
   Run `prisma migrate deploy` (which executes pending migration SQL scripts tracked in `backend/prisma/migrations/` using `DIRECT_URL`):
   ```bash
   cd backend
   DATABASE_URL="<SUPABASE_STAGING_POOLER_URL>" DIRECT_URL="<SUPABASE_STAGING_DIRECT_URL>" npx prisma migrate deploy
   ```
3. **Verify Migration Status**:
   ```bash
   DATABASE_URL="<SUPABASE_STAGING_POOLER_URL>" DIRECT_URL="<SUPABASE_STAGING_DIRECT_URL>" npx prisma migrate status
   ```
   Expected output: `Database schema is up to date!`.

---

## 8. Safe Next Steps

1. **Prisma Staging Migration**: Execute `prisma migrate deploy` against the Supabase staging database following the procedure above.
2. **Create Vercel Staging Project**:
   - Link the GitHub repository to Vercel.
   - Set Root Directory to `backend`.
   - Set Build Command to `npm run build`.
   - Configure Tier 1 environment variables (`DATABASE_URL`, `DIRECT_URL`).
3. **Deploy & Validate Health**:
   - Deploy to Vercel.
   - Issue a GET request to `https://<staging-app>.vercel.app/api/v1/health` and verify HTTP 200 `{ status: "healthy", database: "connected" }`.
4. **Configure Staging Firebase Auth**:
   - Add `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, and `FIREBASE_PRIVATE_KEY` to Vercel project settings.
5. **Compile Staging Android APK**:
   - Build `devRelease` targeting the Vercel staging URL.
6. **Integration Smoke Test**:
   - Log into the staging APK and verify live incremental sync against the Supabase staging database.

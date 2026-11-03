# Notas Backend Architecture & Specification

## 1. Overview & Technology Stack

Notas backend is a serverless, highly scalable API built to provide cloud synchronization, multi-device sync, and secure private backups for the **Notas** Android application.

- **Platform:** Vercel Serverless Functions (`@vercel/node`)
- **Runtime:** Node.js (>= 20) with TypeScript (`target: ES2022`, `NodeNext`)
- **Primary Database:** PostgreSQL (Neon / Supabase / AWS RDS)
- **Object Relational Mapper (ORM):** Prisma ORM (`v5.22+`)
- **Authentication:** Firebase Authentication (validated server-side via Firebase Admin SDK)
- **Backup & Object Storage:** S3-Compatible Private Object Storage (AWS S3, Cloudflare R2, MinIO)
- **Local Client Source of Truth:** Android Room Database (Offline-first architecture)
- **API Base Route:** `/api/v1`

---

## 2. Directory Structure

```
backend/
├── .env.example              # Environment variables template
├── .gitignore                # Git ignore rules for node_modules, .env, dist
├── package.json              # Backend scripts and dependencies
├── tsconfig.json             # TypeScript compiler configuration (NodeNext, ES2022)
├── vercel.json               # Vercel deployment rewrites, routes, and security headers
├── prisma/
│   └── schema.prisma         # PostgreSQL domain schema with full Room entity parity
├── api/
│   └── index.ts              # Vercel Serverless entrypoint
├── src/
│   ├── api/
│   │   ├── index.ts          # Central API request dispatcher & CORS/error handling
│   │   └── routes/
│   │       ├── me.ts         # User profile, account deletion, and statistics handlers
│   │       ├── sync.ts       # Incremental bidirectional synchronization handlers
│   │       └── backups.ts    # Backup metadata and presigned URL handlers
│   ├── db/
│   │   └── client.ts         # Singleton Prisma client with connection pooling
│   ├── errors/
│   │   └── AppError.ts       # Structured error hierarchy and HTTP error serializer
│   ├── middleware/
│   │   ├── auth.ts           # Firebase ID token validation & user auto-provisioning
│   │   └── rateLimit.ts      # Rate limiter with X-RateLimit-* headers
│   ├── services/
│   │   ├── profileService.ts # User profile, stats, and account purge logic
│   │   ├── syncService.ts    # Transactional batch sync and revision watermarking
│   │   ├── backupService.ts  # Backup creation, listing, details, and restore logic
│   │   └── storageService.ts # S3 presigned URL generation and bucket management
│   ├── types/
│   │   └── index.ts          # Shared TypeScript interfaces and DTOs
│   ├── utils/
│   │   ├── firebaseAdmin.ts  # Lazy Firebase Admin SDK initialization
│   │   └── logger.ts         # Structured JSON logger with credential masking
│   └── validation/
│       ├── profileSchemas.ts # Zod schemas for user profile operations
│       ├── syncSchemas.ts    # Zod schemas for push/pull delta sync
│       └── backupSchemas.ts  # Zod schemas for backup creation and restoration
└── tests/
    ├── errors.test.ts        # Unit tests for error classes and serialization
    ├── middleware.test.ts    # Unit tests for rate limiting and auth validation
    └── validation.test.ts    # Unit tests for Zod schemas and data constraints
```

---

## 3. Database Schema & Room Parity

The PostgreSQL schema is defined in [schema.prisma](prisma/schema.prisma) and mirrors Room v7/v8 entities without data loss.

### Entity Mapping Matrix

| Room Entity (Local Android) | Prisma Model (PostgreSQL Cloud) | Stable Key Strategy |
| :--- | :--- | :--- |
| `UserAccount` (Auth state) | `User` | Firebase Auth `UID` (String `@id`) |
| `ShoulderEntity` / Categories | `Category` | Stable UUID v4 (`id`) |
| `NoteEntity` | `Note` | Stable UUID v4 (`id`) |
| `ChecklistItemEntity` | `ChecklistItem` | Stable UUID v4 (`id`), `noteId` FK |
| `NoteAttachmentEntity` | `NoteAttachment` | Stable UUID v4 (`id`), `noteId` FK |
| `UserSettings` (DataStore/Prefs) | `UserSettings` | `userId` (1-to-1 relation) |
| *Sync tracking metadata* | `DeviceSyncCursor` | `userId_deviceId` composite unique |
| *Exported ZIP Backups* | `Backup` | Stable UUID v4 (`id`), S3 `storageKey` |

### ID Mapping Strategy (Room Long PK vs Cloud UUID)
1. **Room v7 (Current):** Uses auto-increment `Long` IDs for primary keys.
2. **Room v8 Migration (Planned):**
   - Adds a non-null, indexed `uuid: String` column to `notes`, `checklist_items`, `attachments`, and `shoulders`.
   - Existing rows receive deterministically generated or random UUID v4 values.
   - Cloud synchronization communicates **exclusively via UUIDs**.
   - Room preserves local `Long` row IDs internally for zero-cost UI performance and backwards compatibility.

---

## 4. REST API Contract (`/api/v1`)

All authenticated endpoints require an `Authorization: Bearer <Firebase_ID_Token>` header.

### Standard Response Envelope
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": {
    "timestamp": "2026-10-24T00:00:00.000Z",
    "requestId": "req_abc123"
  }
}
```

### Endpoints Specification

#### 1. Profile & Account
- **`GET /api/v1/me`**
  - **Auth:** Required
  - **Description:** Returns the authenticated user's profile and last sync timestamp.
  - **Response (200):** `{ id, email, emailVerified, displayName, photoUrl, createdAt, updatedAt, lastSyncAt }`

- **`PATCH /api/v1/me`**
  - **Auth:** Required
  - **Body:** `{ displayName?: string, photoUrl?: string }`
  - **Response (200):** Updated profile object.

- **`GET /api/v1/me/stats`**
  - **Auth:** Required
  - **Description:** Returns counts of active notes, tasks, calendar events, categories, and backups.
  - **Response (200):** `{ notesCount, tasksCount, eventsCount, categoriesCount, backupsCount, lastBackupAt, lastSyncAt }`

- **`DELETE /api/v1/me`**
  - **Auth:** Required
  - **Description:** Permanently deletes user account, all PostgreSQL rows (cascade), S3 backups, and Firebase Auth record.
  - **Response (200):** `{ message: "User account and all associated cloud data deleted permanently." }`

#### 2. Incremental Cloud Synchronization
- **`POST /api/v1/sync/push`**
  - **Auth:** Required
  - **Body:**
    ```json
    {
      "deviceId": "device-uuid",
      "deviceName": "Pixel 8 Pro",
      "appVersion": "1.4.0",
      "categories": [ ... ],
      "notes": [ ... ],
      "settings": { ... }
    }
    ```
  - **Response (200):**
    ```json
    {
      "serverRevision": "1730000000000",
      "appliedCount": { "categories": 3, "notes": 12, "settings": 1 },
      "conflicts": []
    }
    ```

- **`GET /api/v1/sync/changes?sinceRevision=0&limit=100`**
  - **Auth:** Required
  - **Query Params:**
    - `sinceRevision`: BigInt timestamp watermark (default: `0`)
    - `limit`: Max items per request (1..500, default: `100`)
  - **Response (200):**
    ```json
    {
      "currentServerRevision": "1730000000000",
      "categories": [ ... ],
      "notes": [ ... ],
      "settings": { ... },
      "hasMore": false
    }
    ```

#### 3. Backups (S3-Compatible Object Storage)
- **`POST /api/v1/backups`**
  - **Auth:** Required
  - **Body:**
    ```json
    {
      "fileName": "notas-backup-20261024.zip",
      "sizeBytes": 2048000,
      "sha256Checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "notesCount": 42,
      "tasksCount": 10,
      "eventsCount": 5,
      "categoriesCount": 4,
      "schemaVersion": 7,
      "isEncrypted": false
    }
    ```
  - **Response (201):**
    ```json
    {
      "backupId": "backup-uuid",
      "uploadUrl": "https://s3.eu-central-1.amazonaws.com/notas-user-backups/backups/...?X-Amz-Signature=...",
      "expiresInSeconds": 900
    }
    ```

- **`GET /api/v1/backups`**
  - **Auth:** Required
  - **Response (200):** Array of user backups with file sizes, checksums, counts, and creation dates.

- **`GET /api/v1/backups/:id`**
  - **Auth:** Required
  - **Response (200):** Backup details by ID (enforcing user ownership).

- **`POST /api/v1/backups/:id/verify`**
  - **Auth:** Required
  - **Description:** Verifies the uploaded backup against the actual object stored in private S3 (validating byte size and computing SHA-256). Marks backup `COMPLETED` on success, or `FAILED` on size/hash mismatch.
  - **Response (200):**
    ```json
    {
      "backupId": "backup-uuid",
      "status": "COMPLETED",
      "verifiedAt": "2026-10-24T00:15:00.000Z",
      "actualSizeBytes": "2048000",
      "message": "Backup uploaded and verified successfully."
    }
    ```

- **`POST /api/v1/backups/:id/restore`**
  - **Auth:** Required
  - **Description:** Verifies backup status is `COMPLETED` and returns a short-lived presigned download URL.
  - **Response (200):**
    ```json
    {
      "backupId": "backup-uuid",
      "downloadUrl": "https://s3.eu-central-1.amazonaws.com/notas-user-backups/backups/...?X-Amz-Signature=...",
      "expiresInSeconds": 900,
      "sha256Checksum": "...",
      "sizeBytes": "2048000"
    }
    ```

---

## 5. Security & Isolation Model

1. **Authentication:**
   - Client sends Firebase ID token (`Bearer <token>`).
   - Server verifies token cryptographically using Firebase Admin SDK (`verifyIdToken`).
   - User identity (`uid`) is derived exclusively from the verified token, **never from client request parameters**.
2. **Per-User Isolation:**
   - Every database query enforces `where: { userId: req.user.uid }`.
   - Foreign key constraints with `onDelete: Cascade` ensure no orphaned data.
3. **Storage Security:**
   - The S3 backup bucket is strictly private (no public read/write access).
   - Android client uploads and downloads backups via short-lived (15-minute) presigned URLs generated server-side.
   - S3 keys are strictly scoped by user ID: `backups/${userId}/${backupId}.zip`.
4. **Input Validation & Sanitization:**
   - All payloads are strictly parsed and validated using Zod schemas.
   - JSON payloads are capped at 4MB to prevent denial-of-service.
5. **Rate Limiting:**
   - In-memory rate limiting with `X-RateLimit-*` headers prevents abuse.
6. **Connection Pooling:**
   - Serverless functions pool connections through Neon Serverless Pooler / AWS RDS Proxy / PgBouncer (`DATABASE_URL` with `?pgbouncer=true`).
   - `DIRECT_URL` is retained for Prisma schema migrations (`prisma migrate deploy`).

---

## 6. Local Development & Deployment

### Local Development Setup
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Generate Prisma client:
   ```bash
   npm run prisma:generate
   ```
4. Run tests:
   ```bash
   npm test
   ```
5. Run local dev server with Vercel CLI:
   ```bash
   npm run dev
   ```

### Vercel Deployment Instructions
1. Install Vercel CLI or link GitHub repository in Vercel Dashboard.
2. Set Root Directory to `backend` (or deploy from repository root with `backend` output).
3. Configure Environment Variables in Vercel Project Settings:
   - `DATABASE_URL`
   - `DIRECT_URL`
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_CLIENT_EMAIL`
   - `FIREBASE_PRIVATE_KEY`
   - `S3_ENDPOINT` (if using Cloudflare R2 / MinIO)
   - `S3_REGION`
   - `S3_BUCKET_NAME`
   - `S3_ACCESS_KEY_ID`
   - `S3_SECRET_ACCESS_KEY`
4. Apply Prisma migrations to the production PostgreSQL database:
   ```bash
   npm run prisma:deploy
   ```

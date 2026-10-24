-- CreateEnum
CREATE TYPE "NoteKind" AS ENUM ('NOTE', 'LIST');

-- CreateEnum
CREATE TYPE "Importance" AS ENUM ('LOW', 'DEFAULT', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "Visibility" AS ENUM ('DEFAULT', 'PRIVATE', 'SECRET');

-- CreateEnum
CREATE TYPE "BackupStatus" AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

-- CreateTable
CREATE TABLE "users" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "emailVerified" BOOLEAN NOT NULL DEFAULT false,
    "displayName" TEXT,
    "photoUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "lastSyncAt" TIMESTAMP(3),
    "deletionPending" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "categories" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "normalizedName" TEXT NOT NULL,
    "colorHex" TEXT,
    "clientCreatedAt" TIMESTAMP(3) NOT NULL,
    "clientUpdatedAt" TIMESTAMP(3) NOT NULL,
    "serverRevision" BIGINT NOT NULL DEFAULT 0,
    "isDeleted" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "categories_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "notes" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "categoryId" TEXT,
    "kind" "NoteKind" NOT NULL,
    "title" TEXT NOT NULL,
    "body" TEXT NOT NULL DEFAULT '',
    "colorIndex" INTEGER NOT NULL DEFAULT 0,
    "importance" "Importance" NOT NULL DEFAULT 'DEFAULT',
    "visibility" "Visibility" NOT NULL DEFAULT 'DEFAULT',
    "locked" BOOLEAN NOT NULL DEFAULT false,
    "iconKey" TEXT,
    "pictureUri" TEXT,
    "pictureHeroFraming" TEXT,
    "reminderAt" TIMESTAMP(3),
    "endAt" TIMESTAMP(3),
    "isAllDay" BOOLEAN NOT NULL DEFAULT false,
    "reminders" JSONB,
    "recurrence" JSONB,
    "actions" JSONB,
    "pinnedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "trashed" BOOLEAN NOT NULL DEFAULT false,
    "trashedAt" TIMESTAMP(3),
    "clientCreatedAt" TIMESTAMP(3) NOT NULL,
    "clientUpdatedAt" TIMESTAMP(3) NOT NULL,
    "serverRevision" BIGINT NOT NULL DEFAULT 0,
    "isDeleted" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "notes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "checklist_items" (
    "id" TEXT NOT NULL,
    "noteId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "checked" BOOLEAN NOT NULL DEFAULT false,
    "sortOrder" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "parentId" TEXT,
    "depth" INTEGER NOT NULL DEFAULT 0,
    "details" TEXT NOT NULL DEFAULT '',
    "serverRevision" BIGINT NOT NULL DEFAULT 0,
    "isDeleted" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "checklist_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "note_attachments" (
    "id" TEXT NOT NULL,
    "noteId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "uri" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "mimeType" TEXT,
    "sizeBytes" BIGINT,
    "storageKey" TEXT,
    "serverRevision" BIGINT NOT NULL DEFAULT 0,
    "isDeleted" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "note_attachments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_settings" (
    "userId" TEXT NOT NULL,
    "themeSettings" JSONB,
    "reminderSettings" JSONB,
    "defaultNoteSettings" JSONB,
    "interactionSettings" JSONB,
    "viewOptionsSettings" JSONB,
    "clientUpdatedAt" TIMESTAMP(3) NOT NULL,
    "serverRevision" BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT "user_settings_pkey" PRIMARY KEY ("userId")
);

-- CreateTable
CREATE TABLE "device_sync_cursors" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "deviceName" TEXT,
    "appVersion" TEXT,
    "lastSyncedRevision" BIGINT NOT NULL DEFAULT 0,
    "lastSyncedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "device_sync_cursors_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "backups" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "storageKey" TEXT NOT NULL,
    "fileName" TEXT NOT NULL,
    "sizeBytes" BIGINT NOT NULL,
    "sha256Checksum" TEXT NOT NULL,
    "notesCount" INTEGER NOT NULL DEFAULT 0,
    "tasksCount" INTEGER NOT NULL DEFAULT 0,
    "eventsCount" INTEGER NOT NULL DEFAULT 0,
    "categoriesCount" INTEGER NOT NULL DEFAULT 0,
    "schemaVersion" INTEGER NOT NULL DEFAULT 7,
    "isEncrypted" BOOLEAN NOT NULL DEFAULT false,
    "status" "BackupStatus" NOT NULL DEFAULT 'PENDING',
    "verifiedAt" TIMESTAMP(3),
    "verificationError" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "backups_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE INDEX "categories_userId_serverRevision_idx" ON "categories"("userId", "serverRevision");

-- CreateIndex
CREATE INDEX "categories_userId_isDeleted_idx" ON "categories"("userId", "isDeleted");

-- CreateIndex
CREATE UNIQUE INDEX "categories_userId_normalizedName_key" ON "categories"("userId", "normalizedName");

-- CreateIndex
CREATE INDEX "notes_userId_serverRevision_idx" ON "notes"("userId", "serverRevision");

-- CreateIndex
CREATE INDEX "notes_userId_clientUpdatedAt_idx" ON "notes"("userId", "clientUpdatedAt");

-- CreateIndex
CREATE INDEX "notes_userId_isDeleted_idx" ON "notes"("userId", "isDeleted");

-- CreateIndex
CREATE INDEX "notes_userId_archived_trashed_idx" ON "notes"("userId", "archived", "trashed");

-- CreateIndex
CREATE INDEX "checklist_items_noteId_sortOrder_idx" ON "checklist_items"("noteId", "sortOrder");

-- CreateIndex
CREATE INDEX "checklist_items_userId_serverRevision_idx" ON "checklist_items"("userId", "serverRevision");

-- CreateIndex
CREATE INDEX "note_attachments_noteId_idx" ON "note_attachments"("noteId");

-- CreateIndex
CREATE INDEX "note_attachments_userId_serverRevision_idx" ON "note_attachments"("userId", "serverRevision");

-- CreateIndex
CREATE INDEX "device_sync_cursors_userId_lastSyncedAt_idx" ON "device_sync_cursors"("userId", "lastSyncedAt");

-- CreateIndex
CREATE UNIQUE INDEX "device_sync_cursors_userId_deviceId_key" ON "device_sync_cursors"("userId", "deviceId");

-- CreateIndex
CREATE UNIQUE INDEX "backups_storageKey_key" ON "backups"("storageKey");

-- CreateIndex
CREATE INDEX "backups_userId_createdAt_idx" ON "backups"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "backups_userId_status_idx" ON "backups"("userId", "status");

-- AddForeignKey
ALTER TABLE "categories" ADD CONSTRAINT "categories_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "notes" ADD CONSTRAINT "notes_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "notes" ADD CONSTRAINT "notes_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "categories"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "checklist_items" ADD CONSTRAINT "checklist_items_noteId_fkey" FOREIGN KEY ("noteId") REFERENCES "notes"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "note_attachments" ADD CONSTRAINT "note_attachments_noteId_fkey" FOREIGN KEY ("noteId") REFERENCES "notes"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_settings" ADD CONSTRAINT "user_settings_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "device_sync_cursors" ADD CONSTRAINT "device_sync_cursors_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "backups" ADD CONSTRAINT "backups_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;


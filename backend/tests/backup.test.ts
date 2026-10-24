import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createBackupRecord,
  verifyBackupUpload,
  restoreUserBackup,
  getUserBackupById,
  cleanupAbandonedBackups,
} from '../src/services/backupService.js';
import { prisma } from '../src/db/client.js';
import * as storageService from '../src/services/storageService.js';
import { ConflictError, NotFoundError } from '../src/errors/AppError.js';

vi.mock('../src/db/client.js', () => {
  return {
    prisma: {
      backup: {
        create: vi.fn(),
        findUnique: vi.fn(),
        findMany: vi.fn(),
        update: vi.fn(),
      },
    },
  };
});

vi.mock('../src/services/storageService.js', () => {
  return {
    createPresignedUploadUrl: vi.fn().mockResolvedValue('https://s3.example.com/upload-presigned-url'),
    createPresignedDownloadUrl: vi.fn().mockResolvedValue('https://s3.example.com/download-presigned-url'),
    verifyStoredObject: vi.fn(),
    deleteStorageObject: vi.fn().mockResolvedValue(undefined),
  };
});

describe('Backup Service — Security, Verification & Ownership', () => {
  const userId = 'user-alice-123';
  const otherUserId = 'user-bob-999';
  const backupId = '123e4567-e89b-12d3-a456-426614174000';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('createBackupRecord', () => {
    it('creates a PENDING backup record with server-scoped S3 key and presigned URL', async () => {
      (prisma.backup.create as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
      });

      const result = await createBackupRecord(userId, {
        fileName: 'backup.zip',
        sizeBytes: 1024,
        sha256Checksum: 'a'.repeat(64),
        notesCount: 5,
        tasksCount: 2,
        eventsCount: 1,
        categoriesCount: 1,
        schemaVersion: 7,
        isEncrypted: false,
      });

      expect(result.backupId).toBeDefined();
      expect(result.uploadUrl).toBe('https://s3.example.com/upload-presigned-url');
      expect(result.expiresInSeconds).toBe(900);
      expect(prisma.backup.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          userId,
          status: 'PENDING',
          storageKey: expect.stringMatching(new RegExp(`^backups/${userId}/[a-zA-Z0-9-]+\\.zip$`)),
        }),
      });
    });
  });

  describe('verifyBackupUpload — Object Size and Hash Integrity', () => {
    it('marks backup COMPLETED when size and SHA-256 match S3 object', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        storageKey: `backups/${userId}/${backupId}.zip`,
        sha256Checksum: 'a'.repeat(64),
        sizeBytes: BigInt(2048),
        status: 'PENDING',
      });

      vi.spyOn(storageService, 'verifyStoredObject').mockResolvedValue({
        valid: true,
        actualSizeBytes: 2048,
      });

      (prisma.backup.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        status: 'COMPLETED',
        sizeBytes: BigInt(2048),
      });

      const verified = await verifyBackupUpload(userId, backupId);

      expect(verified.status).toBe('COMPLETED');
      expect(prisma.backup.update).toHaveBeenCalledWith({
        where: { id: backupId },
        data: expect.objectContaining({
          status: 'COMPLETED',
          verifiedAt: expect.any(Date),
          verificationError: null,
        }),
      });
    });

    it('marks backup FAILED and throws ConflictError when size or checksum fails', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        storageKey: `backups/${userId}/${backupId}.zip`,
        sha256Checksum: 'a'.repeat(64),
        sizeBytes: BigInt(2048),
        status: 'PENDING',
      });

      vi.spyOn(storageService, 'verifyStoredObject').mockResolvedValue({
        valid: false,
        error: 'Checksum mismatch: expected aaaa but computed bbbb',
      });

      await expect(verifyBackupUpload(userId, backupId)).rejects.toThrow(ConflictError);

      expect(prisma.backup.update).toHaveBeenCalledWith({
        where: { id: backupId },
        data: expect.objectContaining({
          status: 'FAILED',
          verificationError: expect.stringContaining('Checksum mismatch'),
        }),
      });
    });

    it('prevents cross-user verification attempt (IDOR protection)', async () => {
      // Backup belongs to Bob
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId: otherUserId,
      });

      // Alice tries to verify Bob's backup
      await expect(verifyBackupUpload(userId, backupId)).rejects.toThrow(NotFoundError);
    });
  });

  describe('restoreUserBackup — Verification State & Authorization', () => {
    it('refuses to restore an unverified PENDING backup', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        status: 'PENDING',
      });

      await expect(restoreUserBackup(userId, backupId)).rejects.toThrow(ConflictError);
    });

    it('refuses to restore a FAILED backup', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        status: 'FAILED',
      });

      await expect(restoreUserBackup(userId, backupId)).rejects.toThrow(ConflictError);
    });

    it('refuses cross-user restore requests', async () => {
      // Bob owns backup
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId: otherUserId,
        status: 'COMPLETED',
      });

      // Alice tries to restore
      await expect(restoreUserBackup(userId, backupId)).rejects.toThrow(NotFoundError);
    });

    it('successfully generates download URL for verified COMPLETED backup', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        storageKey: `backups/${userId}/${backupId}.zip`,
        sha256Checksum: 'a'.repeat(64),
        sizeBytes: BigInt(2048),
        status: 'COMPLETED',
      });

      const restore = await restoreUserBackup(userId, backupId);

      expect(restore.backupId).toBe(backupId);
      expect(restore.downloadUrl).toBe('https://s3.example.com/download-presigned-url');
      expect(restore.expiresInSeconds).toBe(900);
    });

    it('returns backup details when backup exists and belongs to user', async () => {
      (prisma.backup.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: backupId,
        userId,
        fileName: 'backup.zip',
        sizeBytes: BigInt(2048),
        sha256Checksum: 'a'.repeat(64),
        notesCount: 5,
        tasksCount: 2,
        eventsCount: 1,
        categoriesCount: 1,
        schemaVersion: 7,
        isEncrypted: false,
        status: 'COMPLETED',
        verifiedAt: new Date(),
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const details = await getUserBackupById(userId, backupId);
      expect(details.id).toBe(backupId);
      expect(details.status).toBe('COMPLETED');
    });
  });

  describe('cleanupAbandonedBackups', () => {
    it('finds and marks abandoned PENDING backups as FAILED and deletes S3 files', async () => {
      (prisma.backup.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
        { id: 'abandoned-1', storageKey: 'backups/u1/abandoned-1.zip' },
        { id: 'abandoned-2', storageKey: 'backups/u1/abandoned-2.zip' },
      ]);

      const cleaned = await cleanupAbandonedBackups(userId);

      expect(cleaned).toBe(2);
      expect(storageService.deleteStorageObject).toHaveBeenCalledTimes(2);
      expect(prisma.backup.update).toHaveBeenCalledTimes(2);
    });
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getUserProfile,
  updateUserProfile,
  getUserStats,
  deleteUserAccount,
} from '../src/services/profileService.js';
import { prisma } from '../src/db/client.js';
import * as storageService from '../src/services/storageService.js';
import * as firebaseAdminUtil from '../src/utils/firebaseAdmin.js';
import { NotFoundError, InternalServerError } from '../src/errors/AppError.js';

vi.mock('../src/db/client.js', () => {
  return {
    prisma: {
      user: {
        findUnique: vi.fn(),
        update: vi.fn(),
        delete: vi.fn(),
      },
      note: {
        count: vi.fn(),
      },
      category: {
        count: vi.fn(),
      },
      backup: {
        count: vi.fn(),
        findFirst: vi.fn(),
      },
    },
  };
});

vi.mock('../src/services/storageService.js', () => {
  return {
    deleteAllUserFiles: vi.fn().mockResolvedValue(undefined),
  };
});

vi.mock('../src/utils/firebaseAdmin.js', () => {
  return {
    getFirebaseAdmin: vi.fn(),
  };
});

describe('Profile Service — Account Management & Recoverable Deletion', () => {
  const userId = 'user-alice-123';
  const mockDeleteUser = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (firebaseAdminUtil.getFirebaseAdmin as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      auth: () => ({
        deleteUser: mockDeleteUser,
      }),
    });
  });

  describe('getUserProfile', () => {
    it('returns user profile data when user exists', async () => {
      (prisma.user.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: userId,
        email: 'alice@example.com',
        emailVerified: true,
        displayName: 'Alice',
        photoUrl: 'https://example.com/alice.png',
        createdAt: new Date('2026-10-24T00:00:00.000Z'),
        updatedAt: new Date('2026-10-24T00:00:00.000Z'),
        lastSyncAt: new Date('2026-10-24T00:00:00.000Z'),
      });

      const profile = await getUserProfile(userId);
      expect(profile.id).toBe(userId);
      expect(profile.email).toBe('alice@example.com');
      expect(profile.displayName).toBe('Alice');
    });

    it('throws NotFoundError when user does not exist', async () => {
      (prisma.user.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      await expect(getUserProfile('non-existent')).rejects.toThrow(NotFoundError);
    });
  });

  describe('updateUserProfile', () => {
    it('updates displayName and photoUrl', async () => {
      (prisma.user.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: userId,
        email: 'alice@example.com',
        emailVerified: true,
        displayName: 'Alice In Wonderland',
        photoUrl: 'https://example.com/new-pic.png',
        createdAt: new Date('2026-10-24T00:00:00.000Z'),
        updatedAt: new Date('2026-10-24T01:00:00.000Z'),
        lastSyncAt: null,
      });

      const updated = await updateUserProfile(userId, {
        displayName: 'Alice In Wonderland',
        photoUrl: 'https://example.com/new-pic.png',
      });

      expect(updated.displayName).toBe('Alice In Wonderland');
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: userId },
        data: {
          displayName: 'Alice In Wonderland',
          photoUrl: 'https://example.com/new-pic.png',
        },
      });
    });
  });

  describe('getUserStats', () => {
    it('aggregates counts for notes, tasks, events, categories, and backups', async () => {
      (prisma.note.count as unknown as ReturnType<typeof vi.fn>)
        .mockResolvedValueOnce(15) // notes
        .mockResolvedValueOnce(5) // tasks
        .mockResolvedValueOnce(3); // events
      (prisma.category.count as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(4);
      (prisma.backup.count as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(2);
      (prisma.backup.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        createdAt: new Date('2026-10-24T00:00:00.000Z'),
      });
      (prisma.user.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        lastSyncAt: new Date('2026-10-24T00:00:00.000Z'),
      });

      const stats = await getUserStats(userId);
      expect(stats.notesCount).toBe(15);
      expect(stats.tasksCount).toBe(5);
      expect(stats.eventsCount).toBe(3);
      expect(stats.categoriesCount).toBe(4);
      expect(stats.backupsCount).toBe(2);
      expect(stats.lastBackupAt).toBe('2026-10-24T00:00:00.000Z');
    });
  });

  describe('deleteUserAccount — Recoverable Workflow', () => {
    it('marks user deletionPending, purges S3, deletes Firebase user, and cascades PostgreSQL row', async () => {
      mockDeleteUser.mockResolvedValue(undefined);
      (prisma.user.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({});
      (prisma.user.delete as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({});

      await deleteUserAccount(userId);

      // Step 1: Mark deletionPending
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: userId },
        data: expect.objectContaining({
          deletionPending: true,
          deletedAt: expect.any(Date),
        }),
      });

      // Step 2: S3 cleanup
      expect(storageService.deleteAllUserFiles).toHaveBeenCalledWith(userId);

      // Step 3: Firebase Auth deletion
      expect(mockDeleteUser).toHaveBeenCalledWith(userId);

      // Step 4: PostgreSQL purge
      expect(prisma.user.delete).toHaveBeenCalledWith({
        where: { id: userId },
      });
    });

    it('recovers and continues when Firebase user is already deleted (auth/user-not-found)', async () => {
      mockDeleteUser.mockRejectedValue({ code: 'auth/user-not-found' });
      (prisma.user.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({});
      (prisma.user.delete as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({});

      await deleteUserAccount(userId);

      // Should complete PostgreSQL purge even if already deleted in Firebase
      expect(prisma.user.delete).toHaveBeenCalledWith({
        where: { id: userId },
      });
    });

    it('halts and throws InternalServerError if S3 deletion fails, preserving DB record for retry', async () => {
      (prisma.user.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({});
      vi.spyOn(storageService, 'deleteAllUserFiles').mockRejectedValue(new Error('S3 connection timeout'));

      await expect(deleteUserAccount(userId)).rejects.toThrow(InternalServerError);

      // DB record is marked deletionPending, but NOT permanently deleted yet so user can retry
      expect(prisma.user.delete).not.toHaveBeenCalled();
      expect(mockDeleteUser).not.toHaveBeenCalled();
    });
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { pushSyncChanges, getSyncChanges } from '../src/services/syncService.js';
import { prisma } from '../src/db/client.js';

vi.mock('../src/db/client.js', () => {
  return {
    prisma: {
      $transaction: vi.fn(),
      category: {
        findUnique: vi.fn(),
        upsert: vi.fn(),
        findMany: vi.fn(),
      },
      note: {
        findUnique: vi.fn(),
        upsert: vi.fn(),
        findMany: vi.fn(),
      },
      checklistItem: {
        upsert: vi.fn(),
      },
      noteAttachment: {
        upsert: vi.fn(),
      },
      userSettings: {
        upsert: vi.fn(),
        findFirst: vi.fn(),
      },
      deviceSyncCursor: {
        upsert: vi.fn(),
      },
      user: {
        update: vi.fn(),
      },
    },
  };
});

describe('Sync Service — Correctness & Conflict Resolution', () => {
  const userId = 'user-alice-123';
  const otherUserId = 'user-bob-999';

  beforeEach(() => {
    vi.clearAllMocks();
    (prisma.$transaction as unknown as ReturnType<typeof vi.fn>).mockImplementation(async (callback) => {
      return await callback(prisma);
    });
  });

  describe('pushSyncChanges', () => {
    it('applies valid categories, notes, and settings atomically', async () => {
      (prisma.category.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        deviceName: 'Pixel 8',
        categories: [
          {
            id: '123e4567-e89b-12d3-a456-426614174000',
            name: 'Work',
            normalizedName: 'work',
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            categoryId: '123e4567-e89b-12d3-a456-426614174000',
            kind: 'NOTE',
            title: 'Project Plan',
            body: 'Details here',
            colorIndex: 0,
            importance: 'HIGH',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
        settings: {
          clientUpdatedAt: '2026-10-24T00:00:00.000Z',
        },
      });

      expect(result.appliedCount.categories).toBe(1);
      expect(result.appliedCount.notes).toBe(1);
      expect(result.appliedCount.settings).toBe(1);
      expect(result.conflicts).toHaveLength(0);
      expect(prisma.deviceSyncCursor.upsert).toHaveBeenCalledTimes(1);
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: userId },
        data: { lastSyncAt: expect.any(Date) },
      });
    });

    it('rejects cross-user updates with explicit ownership conflict', async () => {
      // Simulate existing note belonging to another user
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '123e4567-e89b-12d3-a456-426614174001',
        userId: otherUserId,
        serverRevision: BigInt(500),
        clientUpdatedAt: new Date('2026-10-23T00:00:00.000Z'),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Hacked Note',
            body: 'Trying to overwrite another user note',
            colorIndex: 0,
            importance: 'DEFAULT',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(0);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].reason).toContain('Cross-user ownership conflict');
      expect(prisma.note.upsert).not.toHaveBeenCalled();
    });

    it('rejects note linking to a category belonging to another user', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      // Category exists but belongs to Bob
      (prisma.category.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '123e4567-e89b-12d3-a456-426614174999',
        userId: otherUserId,
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            categoryId: '123e4567-e89b-12d3-a456-426614174999', // Bob's category
            kind: 'NOTE',
            title: 'Sneaky Note',
            body: '',
            colorIndex: 0,
            importance: 'DEFAULT',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(0);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].reason).toContain('Invalid category reference');
    });

    it('detects and flags stale updates where server has newer modification', async () => {
      // Existing note was updated at 12:00:00
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '123e4567-e89b-12d3-a456-426614174001',
        userId,
        serverRevision: BigInt(800),
        clientUpdatedAt: new Date('2026-10-24T12:00:00.000Z'),
      });

      // Client sends an older modification at 11:00:00
      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Stale Note',
            body: '',
            colorIndex: 0,
            importance: 'DEFAULT',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T10:00:00.000Z',
            clientUpdatedAt: '2026-10-24T11:00:00.000Z',
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(0);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].reason).toContain('Stale update');
      expect(prisma.note.upsert).not.toHaveBeenCalled();
    });

    it('creates deletion tombstones with isDeleted: true and deletedAt timestamp', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '123e4567-e89b-12d3-a456-426614174001',
        userId,
        serverRevision: BigInt(100),
        clientUpdatedAt: new Date('2026-10-24T08:00:00.000Z'),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Deleted Note',
            body: '',
            colorIndex: 0,
            importance: 'DEFAULT',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T08:00:00.000Z',
            clientUpdatedAt: '2026-10-24T14:00:00.000Z',
            isDeleted: true,
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(1);
      expect(prisma.note.upsert).toHaveBeenCalledWith(
        expect.objectContaining({
          update: expect.objectContaining({
            isDeleted: true,
            deletedAt: expect.any(Date),
          }),
        })
      );
    });
  });

  describe('getSyncChanges — Incremental Pull & Pagination', () => {
    it('returns changes with updated server revision watermark and hasMore flag', async () => {
      (prisma.category.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
        {
          id: 'cat-1',
          name: 'Personal',
          normalizedName: 'personal',
          colorHex: '#fff',
          clientCreatedAt: new Date('2026-10-24T00:00:00.000Z'),
          clientUpdatedAt: new Date('2026-10-24T00:00:00.000Z'),
          serverRevision: BigInt(200),
          isDeleted: false,
        },
      ]);

      (prisma.note.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
        {
          id: 'note-1',
          categoryId: 'cat-1',
          kind: 'NOTE',
          title: 'Title',
          body: 'Body',
          colorIndex: 0,
          importance: 'DEFAULT',
          visibility: 'DEFAULT',
          locked: false,
          isAllDay: false,
          archived: false,
          trashed: false,
          clientCreatedAt: new Date('2026-10-24T00:00:00.000Z'),
          clientUpdatedAt: new Date('2026-10-24T00:00:00.000Z'),
          serverRevision: BigInt(350),
          isDeleted: false,
          checklistItems: [],
          attachments: [],
        },
      ]);

      (prisma.userSettings.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const changes = await getSyncChanges(userId, BigInt(100), 10);

      expect(changes.categories).toHaveLength(1);
      expect(changes.notes).toHaveLength(1);
      expect(changes.currentServerRevision).toBe('350');
      expect(changes.hasMore).toBe(false);
    });

    it('sets hasMore to true when result count reaches query limit', async () => {
      const mockNotes = Array.from({ length: 5 }, (_, i) => ({
        id: `note-${i}`,
        categoryId: null,
        kind: 'NOTE',
        title: `Note ${i}`,
        body: '',
        colorIndex: 0,
        importance: 'DEFAULT',
        visibility: 'DEFAULT',
        locked: false,
        isAllDay: false,
        archived: false,
        trashed: false,
        clientCreatedAt: new Date(),
        clientUpdatedAt: new Date(),
        serverRevision: BigInt(100 + i),
        isDeleted: false,
        checklistItems: [],
        attachments: [],
      }));

      (prisma.category.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([]);
      (prisma.note.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockNotes);
      (prisma.userSettings.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const changes = await getSyncChanges(userId, BigInt(0), 5);
      expect(changes.notes).toHaveLength(5);
      expect(changes.hasMore).toBe(true);
    });
  });
});

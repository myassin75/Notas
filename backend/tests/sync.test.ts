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
        findUnique: vi.fn(),
      },
      noteAttachment: {
        upsert: vi.fn(),
        findUnique: vi.fn(),
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

    // ---------------------------------------------------------------------------
    // Audit Phase 1 regressions (AUD-02, AUD-03, AUD-05, AUD-07)
    // ---------------------------------------------------------------------------
    it('does not overwrite another user’s checklist item provided inside an own note (AUD-02)', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.checklistItem.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '22222222-2222-2222-2222-222222222222',
        userId: otherUserId,
        serverRevision: BigInt(700),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Own Note',
            body: 'Attacker embeds Bob’s checklist item id',
            colorIndex: 0,
            importance: 'DEFAULT',
            visibility: 'DEFAULT',
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
            checklistItems: [
              {
                id: '22222222-2222-2222-2222-222222222222',
                text: 'malicious overwrite',
                checked: true,
                sortOrder: 0,
                depth: 0,
                details: '',
              },
            ],
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(1);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].reason).toContain('Checklist item');
      expect(result.conflicts[0].reason).toContain('belongs to another user');
      expect(prisma.checklistItem.upsert).not.toHaveBeenCalled();
    });

    it('does not overwrite another user’s attachment provided inside an own note (AUD-02)', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '33333333-3333-3333-3333-333333333333',
        userId: otherUserId,
        serverRevision: BigInt(800),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Own Note',
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
            attachments: [
              {
                id: '33333333-3333-3333-3333-333333333333',
                uri: 'file:///steal',
                displayName: 'stolen.json',
              },
            ],
          },
        ],
      });

      expect(result.appliedCount.notes).toBe(1);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].reason).toContain('Attachment');
      expect(result.conflicts[0].reason).toContain('belongs to another user');
      expect(prisma.noteAttachment.upsert).not.toHaveBeenCalled();
    });

    it('still applies nested entities that belong to the authenticated user (AUD-02 happy path)', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.checklistItem.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '44444444-4444-4444-4444-444444444444',
        userId,
        serverRevision: BigInt(50),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Own Note',
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
            checklistItems: [
              {
                id: '44444444-4444-4444-4444-444444444444',
                text: 'legitimate update',
                checked: false,
                sortOrder: 1,
                depth: 0,
                details: '',
              },
            ],
          },
        ],
      });

      expect(result.conflicts).toHaveLength(0);
      expect(prisma.checklistItem.upsert).toHaveBeenCalledTimes(1);
    });

    it('rejects note linking to an in-batch category that failed cross-user validation (AUD-03)', async () => {
      // The batch contains Bob's category; the category loop rejects it with a conflict.
      (prisma.category.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: '55555555-5555-5555-5555-555555555555',
        userId: otherUserId,
        serverRevision: BigInt(5),
        clientUpdatedAt: new Date('2026-10-20T00:00:00.000Z'),
      });

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        categories: [
          {
            id: '55555555-5555-5555-5555-555555555555',
            name: 'Stolen',
            normalizedName: 'stolen',
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            categoryId: '55555555-5555-5555-5555-555555555555',
            kind: 'NOTE',
            title: 'Relay Note',
            body: 'Links to a conflicted in-batch category',
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

      expect(result.conflicts).toHaveLength(2);
      expect(result.conflicts.some((c) => c.entityType === 'category')).toBe(true);
      expect(result.conflicts.some((c) => c.reason.includes('Invalid category reference'))).toBe(true);
      expect(result.appliedCount.categories).toBe(0);
      expect(result.appliedCount.notes).toBe(0);
      expect(prisma.note.upsert).not.toHaveBeenCalled();
    });

    it('links a note to an in-batch category that was validated by the same user (AUD-03 happy path)', async () => {
      (prisma.category.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const result = await pushSyncChanges(userId, {
        deviceId: 'device-1',
        categories: [
          {
            id: '123e4567-e89b-12d3-a456-426614174000',
            name: 'Fresh',
            normalizedName: 'fresh',
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            categoryId: '123e4567-e89b-12d3-a456-426614174000',
            kind: 'NOTE',
            title: 'Linked Note',
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

      expect(result.appliedCount.categories).toBe(1);
      expect(result.appliedCount.notes).toBe(1);
      expect(result.conflicts).toHaveLength(0);
      expect(prisma.note.upsert).toHaveBeenCalledWith(
        expect.objectContaining({
          create: expect.objectContaining({ categoryId: '123e4567-e89b-12d3-a456-426614174000' }),
        })
      );
    });

    it('allocates unique revisions to every applied record within a push (AUD-05)', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.checklistItem.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Multi-record Note',
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
            checklistItems: [
              {
                id: '44444444-4444-4444-4444-444444444444',
                text: 'item',
                checked: false,
                sortOrder: 0,
                depth: 0,
                details: '',
              },
            ],
            attachments: [
              {
                id: '33333333-3333-3333-3333-333333333333',
                uri: 'file:///a.bin',
                displayName: 'a.bin',
              },
            ],
          },
        ],
      });

      const revisions = [
        (prisma.note.upsert as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0].update
          .serverRevision as bigint,
        (prisma.checklistItem.upsert as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0].update
          .serverRevision as bigint,
        (prisma.noteAttachment.upsert as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0].update
          .serverRevision as bigint,
      ];

      expect(new Set(revisions).size).toBe(revisions.length);
    });

    it('keeps revisions strictly monotonic across pushes in the same millisecond (AUD-05)', async () => {
      const nowSpy = vi.spyOn(Date, 'now');
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);
      (prisma.category.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const base = { deviceId: 'device-1' } as const;
      const makePayload = (title: string) => ({
        ...base,
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE' as const,
            title,
            body: '',
            colorIndex: 0,
            importance: 'DEFAULT' as const,
            visibility: 'DEFAULT' as const,
            locked: false,
            isAllDay: false,
            archived: false,
            trashed: false,
            clientCreatedAt: '2026-10-24T00:00:00.000Z',
            clientUpdatedAt: '2026-10-24T00:00:00.000Z',
          },
        ],
      });

      const first = await pushSyncChanges(userId, makePayload('First'));
      const second = await pushSyncChanges(userId, makePayload('Second'));

      expect(second.serverRevision).not.toBe(first.serverRevision);
      expect(BigInt(second.serverRevision) > BigInt(first.serverRevision)).toBe(true);
      nowSpy.mockRestore();
    });

    it('runs the push inside a transaction with an explicit timeout (AUD-07)', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      await pushSyncChanges(userId, {
        deviceId: 'device-1',
        notes: [
          {
            id: '123e4567-e89b-12d3-a456-426614174001',
            kind: 'NOTE',
            title: 'Timeout Note',
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

      expect(prisma.$transaction).toHaveBeenCalledWith(
        expect.any(Function),
        expect.objectContaining({ timeout: 45_000, maxWait: 10_000 })
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

    it('caps the cursor at the truncated list’s ceiling even when another list returns a higher max (AUD-06)', async () => {
      // limit=2: categories fill the page (truncated) while notes fit within it.
      // Advancing past the category ceiling would skip the not-yet-pulled categories.
      const mockCategories = [200n, 250n].map((rev, i) => ({
        id: `cat-${i}`,
        name: `Cat ${i}`,
        normalizedName: `cat-${i}`,
        colorHex: '#fff',
        clientCreatedAt: new Date('2026-10-24T00:00:00.000Z'),
        clientUpdatedAt: new Date('2026-10-24T00:00:00.000Z'),
        serverRevision: rev,
        isDeleted: false,
      }));
      const mockNotes = [350n].map((rev, i) => ({
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
        clientCreatedAt: new Date('2026-10-24T00:00:00.000Z'),
        clientUpdatedAt: new Date('2026-10-24T00:00:00.000Z'),
        serverRevision: rev,
        isDeleted: false,
        checklistItems: [],
        attachments: [],
      }));

      (prisma.category.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockCategories);
      (prisma.note.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockNotes);
      (prisma.userSettings.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const changes = await getSyncChanges(userId, BigInt(0), 2);
      expect(changes.hasMore).toBe(true);
      // Cursor must stop at 250 (category ceiling), never 350 (global max).
      expect(changes.currentServerRevision).toBe('250');
    });

    it('uses the lowest ceiling when both models are truncated (AUD-06)', async () => {
      const mockCategories = [
        { id: 'cat-0', name: 'A', normalizedName: 'a', colorHex: '#fff', clientCreatedAt: new Date(), clientUpdatedAt: new Date(), serverRevision: 300n, isDeleted: false },
      ];
      const mockNotes = [
        { id: 'note-0', categoryId: 'cat-0', kind: 'NOTE', title: 't', colorIndex: 0, importance: 'DEFAULT', visibility: 'DEFAULT', locked: false, isAllDay: false, archived: false, trashed: false, clientCreatedAt: new Date(), clientUpdatedAt: new Date(), serverRevision: 350n, isDeleted: false, checklistItems: [], attachments: [], body: '' },
      ];

      (prisma.category.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockCategories);
      (prisma.note.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockNotes);
      (prisma.userSettings.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const changes = await getSyncChanges(userId, BigInt(0), 1);
      expect(changes.hasMore).toBe(true);
      expect(changes.currentServerRevision).toBe('300');
    });

    it('advances the cursor to the overall max when no list is truncated (AUD-06 sanity)', async () => {
      (prisma.category.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([]);
      (prisma.note.findMany as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
        { id: 'note-0', categoryId: null, kind: 'NOTE', title: 't', body: '', colorIndex: 0, importance: 'DEFAULT', visibility: 'DEFAULT', locked: false, isAllDay: false, archived: false, trashed: false, clientCreatedAt: new Date(), clientUpdatedAt: new Date(), serverRevision: 900n, isDeleted: false, checklistItems: [], attachments: [] },
      ]);
      (prisma.userSettings.findFirst as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(null);

      const changes = await getSyncChanges(userId, BigInt(0), 10);
      expect(changes.hasMore).toBe(false);
      expect(changes.currentServerRevision).toBe('900');
    });
  });
});

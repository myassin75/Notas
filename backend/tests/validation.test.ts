import { describe, it, expect } from 'vitest';
import { updateProfileSchema } from '../src/validation/profileSchemas.js';
import {
  categorySyncSchema,
  noteSyncSchema,
  syncPushSchema,
  syncChangesQuerySchema,
} from '../src/validation/syncSchemas.js';
import { createBackupSchema, backupIdParamSchema } from '../src/validation/backupSchemas.js';

describe('Validation Schemas', () => {
  describe('Profile Validation', () => {
    it('accepts valid profile updates', () => {
      const valid = { displayName: 'John Doe', photoUrl: 'https://example.com/avatar.png' };
      const res = updateProfileSchema.safeParse(valid);
      expect(res.success).toBe(true);
    });

    it('rejects invalid photo URLs', () => {
      const invalid = { photoUrl: 'not-a-valid-url' };
      const res = updateProfileSchema.safeParse(invalid);
      expect(res.success).toBe(false);
    });

    it('rejects overly long display names', () => {
      const invalid = { displayName: 'a'.repeat(101) };
      const res = updateProfileSchema.safeParse(invalid);
      expect(res.success).toBe(false);
    });
  });

  describe('Sync Validation', () => {
    it('validates a correct CategorySyncDto', () => {
      const cat = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        name: 'Personal',
        normalizedName: 'personal',
        colorHex: '#3B82F6',
        clientCreatedAt: '2026-10-24T00:00:00.000Z',
        clientUpdatedAt: '2026-10-24T00:00:00.000Z',
      };
      const res = categorySyncSchema.safeParse(cat);
      expect(res.success).toBe(true);
    });

    it('rejects invalid UUIDs for categories', () => {
      const cat = {
        id: 'not-a-uuid',
        name: 'Personal',
        normalizedName: 'personal',
        clientCreatedAt: '2026-10-24T00:00:00.000Z',
        clientUpdatedAt: '2026-10-24T00:00:00.000Z',
      };
      const res = categorySyncSchema.safeParse(cat);
      expect(res.success).toBe(false);
    });

    it('validates a correct NoteSyncDto with checklist items', () => {
      const note = {
        id: '123e4567-e89b-12d3-a456-426614174001',
        kind: 'LIST',
        title: 'Groceries',
        body: '',
        colorIndex: 1,
        importance: 'HIGH',
        visibility: 'DEFAULT',
        locked: false,
        isAllDay: false,
        archived: false,
        trashed: false,
        clientCreatedAt: '2026-10-24T00:00:00.000Z',
        clientUpdatedAt: '2026-10-24T00:00:00.000Z',
        checklistItems: [
          {
            id: '123e4567-e89b-12d3-a456-426614174002',
            text: 'Apples',
            checked: true,
            sortOrder: 1.0,
            depth: 0,
            details: 'Granny Smith',
          },
        ],
      };
      const res = noteSyncSchema.safeParse(note);
      expect(res.success).toBe(true);
    });

    it('validates a full sync push payload', () => {
      const payload = {
        deviceId: 'device-abc-123',
        deviceName: 'Pixel 8 Pro',
        appVersion: '1.4.0',
        categories: [],
        notes: [],
        settings: {
          clientUpdatedAt: '2026-10-24T00:00:00.000Z',
        },
      };
      const res = syncPushSchema.safeParse(payload);
      expect(res.success).toBe(true);
    });

    it('validates syncChangesQuerySchema with defaults and BigInt conversion', () => {
      const res = syncChangesQuerySchema.safeParse({ sinceRevision: '1730000000000', limit: '50' });
      expect(res.success).toBe(true);
      if (res.success) {
        expect(res.data.sinceRevision).toBe(BigInt('1730000000000'));
        expect(res.data.limit).toBe(50);
      }
    });
  });

  describe('Backup Validation', () => {
    it('validates a valid CreateBackupRequest', () => {
      const backup = {
        fileName: 'notas-backup-20261024.zip',
        sizeBytes: 1048576,
        sha256Checksum: 'a'.repeat(64),
        notesCount: 15,
        tasksCount: 4,
        eventsCount: 2,
        categoriesCount: 3,
        schemaVersion: 7,
        isEncrypted: false,
      };
      const res = createBackupSchema.safeParse(backup);
      expect(res.success).toBe(true);
    });

    it('rejects invalid sha256 checksum format', () => {
      const invalid = {
        fileName: 'test.zip',
        sizeBytes: 100,
        sha256Checksum: 'invalid-hash',
        notesCount: 1,
        tasksCount: 0,
        eventsCount: 0,
        categoriesCount: 0,
        schemaVersion: 7,
      };
      const res = createBackupSchema.safeParse(invalid);
      expect(res.success).toBe(false);
    });

    it('validates backupIdParamSchema', () => {
      expect(backupIdParamSchema.safeParse({ id: '123e4567-e89b-12d3-a456-426614174000' }).success).toBe(true);
      expect(backupIdParamSchema.safeParse({ id: 'invalid-id' }).success).toBe(false);
    });
  });
});

import { z } from 'zod';

export const categorySyncSchema = z.object({
  id: z.string().uuid('Category ID must be a valid UUID'),
  name: z.string().min(1, 'Category name is required').max(100),
  normalizedName: z.string().min(1).max(100),
  colorHex: z.string().regex(/^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/, 'Invalid hex color').nullable().optional(),
  clientCreatedAt: z.string().datetime(),
  clientUpdatedAt: z.string().datetime(),
  isDeleted: z.boolean().optional(),
});

export const checklistItemSyncSchema = z.object({
  id: z.string().uuid('Checklist item ID must be a valid UUID'),
  text: z.string().max(1000),
  checked: z.boolean().default(false),
  sortOrder: z.number().default(0),
  parentId: z.string().uuid().nullable().optional(),
  depth: z.number().int().min(0).max(5).default(0),
  details: z.string().max(2000).default(''),
  isDeleted: z.boolean().optional(),
});

export const attachmentSyncSchema = z.object({
  id: z.string().uuid('Attachment ID must be a valid UUID'),
  uri: z.string().min(1).max(1000),
  displayName: z.string().min(1).max(255),
  mimeType: z.string().max(100).nullable().optional(),
  sizeBytes: z.union([z.number(), z.string()]).nullable().optional(),
  storageKey: z.string().max(500).nullable().optional(),
  isDeleted: z.boolean().optional(),
});

export const noteSyncSchema = z.object({
  id: z.string().uuid('Note ID must be a valid UUID'),
  categoryId: z.string().uuid().nullable().optional(),
  kind: z.enum(['NOTE', 'LIST']),
  title: z.string().max(500),
  body: z.string().max(100000).default(''),
  colorIndex: z.number().int().min(0).max(20).default(0),
  importance: z.enum(['LOW', 'DEFAULT', 'HIGH', 'CRITICAL']).default('DEFAULT'),
  visibility: z.enum(['DEFAULT', 'PRIVATE', 'SECRET']).default('DEFAULT'),
  locked: z.boolean().default(false),
  iconKey: z.string().max(50).nullable().optional(),
  pictureUri: z.string().max(1000).nullable().optional(),
  pictureHeroFraming: z.string().max(50).nullable().optional(),
  reminderAt: z.string().datetime().nullable().optional(),
  endAt: z.string().datetime().nullable().optional(),
  isAllDay: z.boolean().default(false),
  reminders: z.array(z.record(z.unknown())).optional(),
  recurrence: z.record(z.unknown()).nullable().optional(),
  actions: z.array(z.record(z.unknown())).optional(),
  pinnedAt: z.string().datetime().nullable().optional(),
  completedAt: z.string().datetime().nullable().optional(),
  archived: z.boolean().default(false),
  trashed: z.boolean().default(false),
  trashedAt: z.string().datetime().nullable().optional(),
  clientCreatedAt: z.string().datetime(),
  clientUpdatedAt: z.string().datetime(),
  isDeleted: z.boolean().optional(),
  checklistItems: z.array(checklistItemSyncSchema).optional(),
  attachments: z.array(attachmentSyncSchema).optional(),
});

export const userSettingsSyncSchema = z.object({
  themeSettings: z.record(z.unknown()).nullable().optional(),
  reminderSettings: z.record(z.unknown()).nullable().optional(),
  defaultNoteSettings: z.record(z.unknown()).nullable().optional(),
  interactionSettings: z.record(z.unknown()).nullable().optional(),
  viewOptionsSettings: z.record(z.unknown()).nullable().optional(),
  clientUpdatedAt: z.string().datetime(),
});

export const syncPushSchema = z.object({
  deviceId: z.string().min(1).max(100),
  deviceName: z.string().max(100).optional(),
  appVersion: z.string().max(50).optional(),
  categories: z.array(categorySyncSchema).max(500).optional(),
  notes: z.array(noteSyncSchema).max(500).optional(),
  settings: userSettingsSyncSchema.optional(),
});

export const syncChangesQuerySchema = z.object({
  sinceRevision: z
    .string()
    .optional()
    .default('0')
    .transform((val) => {
      try {
        return BigInt(val);
      } catch {
        return BigInt(0);
      }
    }),
  limit: z
    .string()
    .optional()
    .default('100')
    .transform((val) => Math.min(Math.max(1, parseInt(val, 10) || 100), 500)),
});

export type SyncPushInput = z.infer<typeof syncPushSchema>;
export type SyncChangesQueryInput = z.infer<typeof syncChangesQuerySchema>;

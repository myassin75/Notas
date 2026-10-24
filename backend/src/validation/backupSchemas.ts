import { z } from 'zod';

export const createBackupSchema = z.object({
  fileName: z.string().min(1).max(255),
  sizeBytes: z.number().int().positive().max(100 * 1024 * 1024, 'Backup size cannot exceed 100MB'),
  sha256Checksum: z.string().regex(/^[a-fA-F0-9]{64}$/, 'Invalid SHA256 checksum format'),
  notesCount: z.number().int().min(0),
  tasksCount: z.number().int().min(0),
  eventsCount: z.number().int().min(0),
  categoriesCount: z.number().int().min(0),
  schemaVersion: z.number().int().min(1).max(100),
  isEncrypted: z.boolean().default(false),
});

export const backupIdParamSchema = z.object({
  id: z.string().uuid('Backup ID must be a valid UUID'),
});

export type CreateBackupInput = z.infer<typeof createBackupSchema>;

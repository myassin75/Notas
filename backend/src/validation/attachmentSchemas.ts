import { z } from 'zod';

export const requestAttachmentUploadSchema = z.object({
  attachmentId: z.string().uuid('Attachment ID must be a valid UUID'),
  noteId: z.string().uuid('Note ID must be a valid UUID').optional(),
  displayName: z.string().min(1).max(255),
  mimeType: z.string().regex(/^[a-zA-Z0-9_\-\.\+]+\/[a-zA-Z0-9_\-\.\+]+$/, 'Invalid MIME type format'),
  sizeBytes: z.number().int().positive().max(50 * 1024 * 1024, 'Attachment cannot exceed 50MB'),
});

export const attachmentIdParamSchema = z.object({
  id: z.string().uuid('Attachment ID must be a valid UUID'),
});

export type RequestAttachmentUploadInput = z.infer<typeof requestAttachmentUploadSchema>;

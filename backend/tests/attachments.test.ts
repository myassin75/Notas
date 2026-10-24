import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getAttachmentUploadUrl,
  verifyAttachmentUpload,
  getAttachmentDownloadUrl,
  deleteAttachmentStorage,
} from '../src/services/attachmentStorageService.js';
import { prisma } from '../src/db/client.js';
import * as storageService from '../src/services/storageService.js';
import { ConflictError, NotFoundError, ForbiddenError } from '../src/errors/AppError.js';

vi.mock('../src/db/client.js', () => {
  return {
    prisma: {
      note: {
        findUnique: vi.fn(),
      },
      noteAttachment: {
        findUnique: vi.fn(),
        update: vi.fn(),
      },
    },
  };
});

vi.mock('../src/services/storageService.js', () => {
  return {
    createPresignedUploadUrl: vi.fn().mockResolvedValue('https://s3.example.com/attachment-upload-url'),
    createPresignedDownloadUrl: vi.fn().mockResolvedValue('https://s3.example.com/attachment-download-url'),
    headStorageObject: vi.fn(),
    deleteStorageObject: vi.fn().mockResolvedValue(undefined),
  };
});

describe('Attachment Storage Service — Ownership, Security & Presigned URLs', () => {
  const userId = 'user-alice-123';
  const otherUserId = 'user-bob-999';
  const attachmentId = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';
  const noteId = 'f47ac10b-58cc-4372-a567-0e02b2c3d479';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getAttachmentUploadUrl', () => {
    it('generates a presigned upload URL scoped to user boundary', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: noteId,
        userId,
      });

      const result = await getAttachmentUploadUrl(userId, {
        attachmentId,
        noteId,
        displayName: 'photo.jpg',
        mimeType: 'image/jpeg',
        sizeBytes: 1024 * 1024,
      });

      expect(result.attachmentId).toBe(attachmentId);
      expect(result.uploadUrl).toBe('https://s3.example.com/attachment-upload-url');
      expect(result.storageKey).toBe(`attachments/${userId}/${attachmentId}`);
      expect(result.expiresInSeconds).toBe(900);
      expect(storageService.createPresignedUploadUrl).toHaveBeenCalledWith(
        `attachments/${userId}/${attachmentId}`,
        'image/jpeg',
        900
      );
    });

    it('rejects upload URL generation if note belongs to another user', async () => {
      (prisma.note.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: noteId,
        userId: otherUserId, // Different user
      });

      await expect(
        getAttachmentUploadUrl(userId, {
          attachmentId,
          noteId,
          displayName: 'photo.jpg',
          mimeType: 'image/jpeg',
          sizeBytes: 1024,
        })
      ).rejects.toThrow(ForbiddenError);
    });
  });

  describe('verifyAttachmentUpload', () => {
    it('marks attachment AVAILABLE when object exists in S3', async () => {
      (storageService.headStorageObject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ContentLength: 2048,
      });
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId,
      });
      (prisma.noteAttachment.update as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId,
      });

      const result = await verifyAttachmentUpload(userId, attachmentId);

      expect(result.status).toBe('AVAILABLE');
      expect(result.sizeBytes).toBe('2048');
      expect(result.storageKey).toBe(`attachments/${userId}/${attachmentId}`);
      expect(prisma.noteAttachment.update).toHaveBeenCalledWith({
        where: { id: attachmentId },
        data: {
          storageKey: `attachments/${userId}/${attachmentId}`,
          sizeBytes: BigInt(2048),
        },
      });
    });

    it('rejects verification if S3 object has zero size or does not exist', async () => {
      (storageService.headStorageObject as unknown as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('NotFound')
      );

      await expect(verifyAttachmentUpload(userId, attachmentId)).rejects.toThrow(ConflictError);
    });

    it('rejects verification if attachment record belongs to another user', async () => {
      (storageService.headStorageObject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ContentLength: 2048,
      });
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId: otherUserId,
      });

      await expect(verifyAttachmentUpload(userId, attachmentId)).rejects.toThrow(NotFoundError);
    });
  });

  describe('getAttachmentDownloadUrl', () => {
    it('generates presigned download URL for authenticated owner', async () => {
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId,
        mimeType: 'image/png',
        displayName: 'diagram.png',
      });
      (storageService.headStorageObject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ContentLength: 4096,
      });

      const result = await getAttachmentDownloadUrl(userId, attachmentId);

      expect(result.attachmentId).toBe(attachmentId);
      expect(result.downloadUrl).toBe('https://s3.example.com/attachment-download-url');
      expect(result.mimeType).toBe('image/png');
      expect(result.displayName).toBe('diagram.png');
      expect(storageService.createPresignedDownloadUrl).toHaveBeenCalledWith(
        `attachments/${userId}/${attachmentId}`,
        900
      );
    });

    it('rejects download URL request if attachment belongs to another user', async () => {
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId: otherUserId,
      });

      await expect(getAttachmentDownloadUrl(userId, attachmentId)).rejects.toThrow(NotFoundError);
    });

    it('rejects download URL request if object is missing in storage', async () => {
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId,
      });
      (storageService.headStorageObject as unknown as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('NoSuchKey')
      );

      await expect(getAttachmentDownloadUrl(userId, attachmentId)).rejects.toThrow(NotFoundError);
    });
  });

  describe('deleteAttachmentStorage', () => {
    it('deletes storage object for authorized user', async () => {
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId,
      });

      await deleteAttachmentStorage(userId, attachmentId);

      expect(storageService.deleteStorageObject).toHaveBeenCalledWith(
        `attachments/${userId}/${attachmentId}`
      );
      expect(prisma.noteAttachment.update).toHaveBeenCalledWith({
        where: { id: attachmentId },
        data: { storageKey: null },
      });
    });

    it('rejects deletion if attachment belongs to another user', async () => {
      (prisma.noteAttachment.findUnique as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        id: attachmentId,
        userId: otherUserId,
      });

      await expect(deleteAttachmentStorage(userId, attachmentId)).rejects.toThrow(NotFoundError);
    });
  });
});

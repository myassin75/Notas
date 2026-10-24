import { prisma } from '../db/client.js';
import { NotFoundError, ConflictError, ForbiddenError } from '../errors/AppError.js';
import {
  createPresignedUploadUrl,
  createPresignedDownloadUrl,
  headStorageObject,
  deleteStorageObject,
} from './storageService.js';
import type { RequestAttachmentUploadInput } from '../validation/attachmentSchemas.js';
import type {
  AttachmentUploadUrlResponseDto,
  AttachmentVerifyResponseDto,
  AttachmentDownloadUrlResponseDto,
} from '../types/index.js';

export async function getAttachmentUploadUrl(
  userId: string,
  input: RequestAttachmentUploadInput
): Promise<AttachmentUploadUrlResponseDto> {
  // If noteId is supplied, verify note ownership
  if (input.noteId) {
    const note = await prisma.note.findUnique({
      where: { id: input.noteId },
    });
    if (note && note.userId !== userId) {
      throw new ForbiddenError('You do not have permission to upload attachments for this note');
    }
  }

  // Storage key is strictly scoped to the authenticated user's boundary
  const storageKey = `attachments/${userId}/${input.attachmentId}`;
  const expiresInSeconds = 900; // 15 minutes

  const uploadUrl = await createPresignedUploadUrl(storageKey, input.mimeType, expiresInSeconds);

  return {
    attachmentId: input.attachmentId,
    uploadUrl,
    storageKey,
    expiresInSeconds,
  };
}

export async function verifyAttachmentUpload(
  userId: string,
  attachmentId: string
): Promise<AttachmentVerifyResponseDto> {
  const storageKey = `attachments/${userId}/${attachmentId}`;

  try {
    const head = await headStorageObject(storageKey);
    const actualSizeBytes = head.ContentLength ?? 0;

    if (actualSizeBytes <= 0) {
      throw new ConflictError('Uploaded attachment has zero size');
    }

    // If attachment record exists in PostgreSQL, verify user ownership and update
    const existing = await prisma.noteAttachment.findUnique({
      where: { id: attachmentId },
    });

    if (existing) {
      if (existing.userId !== userId) {
        throw new NotFoundError(`Attachment with ID ${attachmentId} not found`);
      }

      await prisma.noteAttachment.update({
        where: { id: attachmentId },
        data: {
          storageKey,
          sizeBytes: BigInt(actualSizeBytes),
        },
      });
    }

    return {
      attachmentId,
      storageKey,
      sizeBytes: actualSizeBytes.toString(),
      status: 'AVAILABLE',
      verifiedAt: new Date().toISOString(),
    };
  } catch (error: unknown) {
    if (error instanceof NotFoundError || error instanceof ConflictError) {
      throw error;
    }
    const message = error instanceof Error ? error.message : 'Attachment verification failed';
    throw new ConflictError(`Attachment storage verification failed: ${message}`);
  }
}

export async function getAttachmentDownloadUrl(
  userId: string,
  attachmentId: string
): Promise<AttachmentDownloadUrlResponseDto> {
  const storageKey = `attachments/${userId}/${attachmentId}`;

  // If record exists in DB, ensure it belongs to the authenticated user
  const record = await prisma.noteAttachment.findUnique({
    where: { id: attachmentId },
  });

  if (record && record.userId !== userId) {
    throw new NotFoundError(`Attachment with ID ${attachmentId} not found`);
  }

  // Ensure object exists in S3
  try {
    await headStorageObject(storageKey);
  } catch {
    throw new NotFoundError(`Attachment object with ID ${attachmentId} not found in storage`);
  }

  const expiresInSeconds = 900; // 15 minutes
  const downloadUrl = await createPresignedDownloadUrl(storageKey, expiresInSeconds);

  return {
    attachmentId,
    downloadUrl,
    expiresInSeconds,
    mimeType: record?.mimeType ?? null,
    displayName: record?.displayName ?? null,
  };
}

export async function deleteAttachmentStorage(
  userId: string,
  attachmentId: string
): Promise<void> {
  const storageKey = `attachments/${userId}/${attachmentId}`;

  const record = await prisma.noteAttachment.findUnique({
    where: { id: attachmentId },
  });

  if (record && record.userId !== userId) {
    throw new NotFoundError(`Attachment with ID ${attachmentId} not found`);
  }

  try {
    await deleteStorageObject(storageKey);
  } catch {
    // S3 object may already have been deleted
  }

  if (record) {
    await prisma.noteAttachment.update({
      where: { id: attachmentId },
      data: {
        storageKey: null,
      },
    });
  }
}

import type { VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import {
  requestAttachmentUploadSchema,
  attachmentIdParamSchema,
} from '../../validation/attachmentSchemas.js';
import {
  getAttachmentUploadUrl,
  verifyAttachmentUpload,
  getAttachmentDownloadUrl,
  deleteAttachmentStorage,
} from '../../services/attachmentStorageService.js';
import { BadRequestError } from '../../errors/AppError.js';

export async function handleGetAttachmentUploadUrl(
  req: AuthenticatedRequest,
  res: VercelResponse
) {
  const parseResult = requestAttachmentUploadSchema.safeParse(req.body);
  if (!parseResult.success) {
    throw new BadRequestError('Validation failed on attachment upload URL request', parseResult.error.format());
  }

  const result = await getAttachmentUploadUrl(req.user.uid, parseResult.data);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleVerifyAttachment(
  req: AuthenticatedRequest,
  res: VercelResponse,
  attachmentId: string
) {
  const paramResult = attachmentIdParamSchema.safeParse({ id: attachmentId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid attachment ID UUID format');
  }

  const result = await verifyAttachmentUpload(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleGetAttachmentDownloadUrl(
  req: AuthenticatedRequest,
  res: VercelResponse,
  attachmentId: string
) {
  const paramResult = attachmentIdParamSchema.safeParse({ id: attachmentId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid attachment ID UUID format');
  }

  const result = await getAttachmentDownloadUrl(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleDeleteAttachment(
  req: AuthenticatedRequest,
  res: VercelResponse,
  attachmentId: string
) {
  const paramResult = attachmentIdParamSchema.safeParse({ id: attachmentId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid attachment ID UUID format');
  }

  await deleteAttachmentStorage(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: { attachmentId: paramResult.data.id, deleted: true },
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

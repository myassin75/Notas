import type { VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import { createBackupSchema, backupIdParamSchema } from '../../validation/backupSchemas.js';
import {
  createBackupRecord,
  listUserBackups,
  getUserBackupById,
  restoreUserBackup,
  verifyBackupUpload,
} from '../../services/backupService.js';
import { BadRequestError } from '../../errors/AppError.js';

export async function handleCreateBackup(req: AuthenticatedRequest, res: VercelResponse) {
  const parseResult = createBackupSchema.safeParse(req.body);
  if (!parseResult.success) {
    throw new BadRequestError('Validation failed on create backup payload', parseResult.error.format());
  }

  const result = await createBackupRecord(req.user.uid, parseResult.data);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(201).json(response);
}

export async function handleListBackups(req: AuthenticatedRequest, res: VercelResponse) {
  const backups = await listUserBackups(req.user.uid);
  const response: ApiResponse = {
    success: true,
    data: backups,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleGetBackup(
  req: AuthenticatedRequest,
  res: VercelResponse,
  backupId: string
) {
  const paramResult = backupIdParamSchema.safeParse({ id: backupId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid backup ID UUID format');
  }

  const backup = await getUserBackupById(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: backup,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleVerifyBackup(
  req: AuthenticatedRequest,
  res: VercelResponse,
  backupId: string
) {
  const paramResult = backupIdParamSchema.safeParse({ id: backupId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid backup ID UUID format');
  }

  const result = await verifyBackupUpload(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleRestoreBackup(
  req: AuthenticatedRequest,
  res: VercelResponse,
  backupId: string
) {
  const paramResult = backupIdParamSchema.safeParse({ id: backupId });
  if (!paramResult.success) {
    throw new BadRequestError('Invalid backup ID UUID format');
  }

  const result = await restoreUserBackup(req.user.uid, paramResult.data.id);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

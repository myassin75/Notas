import type { VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import { updateProfileSchema } from '../../validation/profileSchemas.js';
import {
  getUserProfile,
  updateUserProfile,
  getUserStats,
  deleteUserAccount,
} from '../../services/profileService.js';
import { BadRequestError } from '../../errors/AppError.js';

export async function handleGetMe(req: AuthenticatedRequest, res: VercelResponse) {
  const profile = await getUserProfile(req.user.uid);
  const response: ApiResponse = {
    success: true,
    data: profile,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handlePatchMe(req: AuthenticatedRequest, res: VercelResponse) {
  const parseResult = updateProfileSchema.safeParse(req.body);
  if (!parseResult.success) {
    throw new BadRequestError('Validation failed on update profile payload', parseResult.error.format());
  }

  const updatedProfile = await updateUserProfile(req.user.uid, parseResult.data);
  const response: ApiResponse = {
    success: true,
    data: updatedProfile,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleGetStats(req: AuthenticatedRequest, res: VercelResponse) {
  const stats = await getUserStats(req.user.uid);
  const response: ApiResponse = {
    success: true,
    data: stats,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleDeleteMe(req: AuthenticatedRequest, res: VercelResponse) {
  await deleteUserAccount(req.user.uid);
  const response: ApiResponse = {
    success: true,
    data: { message: 'User account and all associated cloud data deleted permanently.' },
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

import type { VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import { syncPushSchema, syncChangesQuerySchema } from '../../validation/syncSchemas.js';
import { pushSyncChanges, getSyncChanges } from '../../services/syncService.js';
import { BadRequestError } from '../../errors/AppError.js';

export async function handleSyncPush(req: AuthenticatedRequest, res: VercelResponse) {
  const parseResult = syncPushSchema.safeParse(req.body);
  if (!parseResult.success) {
    throw new BadRequestError('Validation failed on sync push payload', parseResult.error.format());
  }

  const result = await pushSyncChanges(req.user.uid, parseResult.data);
  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

export async function handleSyncChanges(req: AuthenticatedRequest, res: VercelResponse) {
  const queryResult = syncChangesQuerySchema.safeParse(req.query);
  if (!queryResult.success) {
    throw new BadRequestError('Validation failed on sync changes query parameters', queryResult.error.format());
  }

  const { sinceRevision, limit } = queryResult.data;
  const result = await getSyncChanges(req.user.uid, sinceRevision, limit);

  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };
  return res.status(200).json(response);
}

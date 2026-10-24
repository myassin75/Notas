import type { VercelRequest, VercelResponse } from '@vercel/node';
import { authenticate } from '../middleware/auth.js';
import { rateLimit } from '../middleware/rateLimit.js';
import { formatErrorResponse, NotFoundError, BadRequestError, HttpCode } from '../errors/AppError.js';
import { logger } from '../utils/logger.js';
import { checkDatabaseHealth } from '../db/client.js';
import type { AuthenticatedRequest } from '../types/index.js';

// Route handlers
import { handleGetMe, handlePatchMe, handleGetStats, handleDeleteMe } from './routes/me.js';
import { handleSyncPush, handleSyncChanges } from './routes/sync.js';
import {
  handleCreateBackup,
  handleListBackups,
  handleGetBackup,
  handleRestoreBackup,
  handleVerifyBackup,
} from './routes/backups.js';
import {
  handleGetAttachmentUploadUrl,
  handleVerifyAttachment,
  handleGetAttachmentDownloadUrl,
  handleDeleteAttachment,
} from './routes/attachments.js';

const rateLimiter = rateLimit({ windowMs: 60 * 1000, maxRequests: 120 });

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const startTime = Date.now();

  // 1. Production CORS configuration
  const origin = req.headers.origin;
  const allowedOrigins = process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',').map((o) => o.trim())
    : ['*'];

  if (origin && (allowedOrigins.includes('*') || allowedOrigins.includes(origin))) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
  } else if (allowedOrigins.includes('*')) {
    res.setHeader('Access-Control-Allow-Origin', '*');
  }

  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization'
  );

  // Handle preflight OPTIONS request
  if (req.method === 'OPTIONS') {
    res.status(204).end();
    return;
  }

  // 2. Parse clean path
  const rawPath = (req.query.path as string) || req.url?.replace(/^\/api\/v1\/?/, '') || '';
  const cleanPath = rawPath.split('?')[0].replace(/^\/+|\/+$/g, '');
  const method = req.method?.toUpperCase();

  try {
    // 3. Payload size guard
    const maxPayloadBytes = parseInt(process.env.MAX_PAYLOAD_SIZE_BYTES || '4194304', 10);
    const contentLength = req.headers['content-length'];
    if (contentLength && parseInt(contentLength, 10) > maxPayloadBytes) {
      throw new BadRequestError(
        `Request payload exceeds maximum allowed size of ${Math.round(maxPayloadBytes / (1024 * 1024))}MB`,
        undefined,
        'PAYLOAD_TOO_LARGE'
      );
    }

    // 4. Public Health Check Endpoint
    if (cleanPath === 'health' && method === 'GET') {
      const dbHealthy = await checkDatabaseHealth();
      return res.status(dbHealthy ? HttpCode.OK : HttpCode.SERVICE_UNAVAILABLE).json({
        status: dbHealthy ? 'healthy' : 'degraded',
        database: dbHealthy ? 'connected' : 'disconnected',
        timestamp: new Date().toISOString(),
      });
    }

    // 5. Distributed / In-memory Rate Limiting
    await rateLimiter(req, res);

    // 6. Authentication with Token Revocation Check for Sensitive Operations
    const isRevocationSensitive = cleanPath === 'me' && method === 'DELETE';
    await authenticate(req, res, { checkRevoked: isRevocationSensitive });
    const authReq = req as AuthenticatedRequest;

    logger.debug(`Incoming API request: ${method} /${cleanPath}`, {
      userId: authReq.user.uid,
      method,
      path: cleanPath,
    });

    // --------------------------------------------------------------------------------------------
    // Route Dispatcher
    // --------------------------------------------------------------------------------------------

    // 1. Profile Endpoints (/api/v1/me)
    if (cleanPath === 'me') {
      if (method === 'GET') return await handleGetMe(authReq, res);
      if (method === 'PATCH') return await handlePatchMe(authReq, res);
      if (method === 'DELETE') return await handleDeleteMe(authReq, res);
    }

    if (cleanPath === 'me/stats' && method === 'GET') {
      return await handleGetStats(authReq, res);
    }

    // 2. Sync Endpoints (/api/v1/sync)
    if (cleanPath === 'sync/push' && method === 'POST') {
      return await handleSyncPush(authReq, res);
    }

    if (cleanPath === 'sync/changes' && method === 'GET') {
      return await handleSyncChanges(authReq, res);
    }

    // 3. Backup Endpoints (/api/v1/backups)
    if (cleanPath === 'backups') {
      if (method === 'POST') return await handleCreateBackup(authReq, res);
      if (method === 'GET') return await handleListBackups(authReq, res);
    }

    // Match /backups/:id, /backups/:id/restore, or /backups/:id/verify
    const backupMatch = cleanPath.match(/^backups\/([a-zA-Z0-9-]+)(\/(restore|verify))?$/);
    if (backupMatch) {
      const backupId = backupMatch[1];
      const action = backupMatch[3];

      if (action === 'restore' && method === 'POST') {
        return await handleRestoreBackup(authReq, res, backupId);
      }

      if (action === 'verify' && method === 'POST') {
        return await handleVerifyBackup(authReq, res, backupId);
      }

      if (!action && method === 'GET') {
        return await handleGetBackup(authReq, res, backupId);
      }
    }

    // 4. Attachment Endpoints (/api/v1/attachments)
    if (cleanPath === 'attachments/upload-url' && method === 'POST') {
      return await handleGetAttachmentUploadUrl(authReq, res);
    }

    const attachmentMatch = cleanPath.match(/^attachments\/([a-zA-Z0-9-]+)(\/(verify|download-url))?$/);
    if (attachmentMatch) {
      const attachmentId = attachmentMatch[1];
      const action = attachmentMatch[3];

      if (action === 'verify' && method === 'POST') {
        return await handleVerifyAttachment(authReq, res, attachmentId);
      }

      if (action === 'download-url' && method === 'GET') {
        return await handleGetAttachmentDownloadUrl(authReq, res, attachmentId);
      }

      if (!action && method === 'DELETE') {
        return await handleDeleteAttachment(authReq, res, attachmentId);
      }
    }

    // No matching route
    throw new NotFoundError(`Endpoint '${method} /api/v1/${cleanPath}' does not exist`);
  } catch (error) {
    logger.error(`API Error handling ${method} /${cleanPath}`, error);
    const { statusCode, body } = formatErrorResponse(error);
    return res.status(statusCode).json(body);
  } finally {
    const durationMs = Date.now() - startTime;
    logger.debug(`Request completed in ${durationMs}ms`, { method, path: cleanPath, durationMs });
  }
}

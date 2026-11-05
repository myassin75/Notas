import type { VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import { dispatchCustomVerificationEmail } from '../../services/emailService.js';

export async function handleSendVerificationEmail(req: AuthenticatedRequest, res: VercelResponse) {
  const result = await dispatchCustomVerificationEmail({
    uid: req.user.uid,
    email: req.user.email,
    name: req.user.name,
    emailVerified: req.user.emailVerified,
  });

  const response: ApiResponse = {
    success: true,
    data: result,
    meta: { timestamp: new Date().toISOString() },
  };

  return res.status(200).json(response);
}


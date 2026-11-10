import type { VercelRequest, VercelResponse } from '@vercel/node';
import type { AuthenticatedRequest, ApiResponse } from '../../types/index.js';
import { requestOtpWithCooldown, verifyOtpTransaction } from '../../services/otpService.js';
import { createOutboxEvent } from '../../services/outboxService.js';
import { prisma } from '../../db/client.js';
import { getFirebaseAdmin } from '../../utils/firebaseAdmin.js';
import { BadRequestError, ConflictError, TooManyRequestsError, HttpCode } from '../../errors/AppError.js';
import { OtpPurpose } from '@prisma/client';
import { logger } from '../../utils/logger.js';
import { maskEmail } from '../../utils/htmlEscape.js';

/**
 * POST /api/v1/auth/otp/send-verification
 * Authenticated: Sends a 6-digit numeric verification code to the authenticated user's email.
 */
export async function handleSendOtpVerification(req: AuthenticatedRequest, res: VercelResponse) {
  const { email, uid, emailVerified } = req.user;

  if (emailVerified) {
    return res.status(HttpCode.OK).json({
      success: true,
      message: 'Email is already verified.',
      alreadyVerified: true,
      meta: { timestamp: new Date().toISOString() },
    });
  }

  if (!email) {
    throw new BadRequestError('User account does not have an associated email address.');
  }

  const result = await requestOtpWithCooldown(email, OtpPurpose.EMAIL_VERIFICATION, uid);

  const response: ApiResponse = {
    success: true,
    data: {
      message: 'Verification code sent.',
      resendAvailableAt: result.resendAvailableAt.toISOString(),
      expiresAt: result.expiresAt.toISOString(),
    },
    meta: { timestamp: new Date().toISOString() },
  };

  return res.status(HttpCode.OK).json(response);
}

/**
 * POST /api/v1/auth/otp/verify-code
 * Authenticated: Validates entered 6-digit code, burns OTP, updates DB to VERIFICATION_SYNC_PENDING,
 * and immediately attempts synchronous Firebase Admin SDK claim synchronization.
 */
export async function handleVerifyOtpCode(req: AuthenticatedRequest, res: VercelResponse) {
  const { code } = req.body || {};
  const { email, uid } = req.user;

  if (!code || typeof code !== 'string' || !/^\d{6}$/.test(code.trim())) {
    throw new BadRequestError('Please provide a valid 6-digit numeric verification code.', undefined, 'INVALID_CODE_FORMAT');
  }

  const normalizedCode = code.trim();

  // 1. Check idempotency: If user is already verified in DB, return 200 OK
  const currentUser = await prisma.user.findUnique({ where: { id: uid } });
  if (currentUser?.emailVerificationStatus === 'VERIFIED') {
    return res.status(HttpCode.OK).json({
      success: true,
      data: {
        status: 'VERIFIED',
        message: 'Email is already verified.',
        alreadyVerified: true,
      },
      meta: { timestamp: new Date().toISOString() },
    });
  }

  // 2. Atomic verification & OTP burn via row-level locking (SELECT FOR UPDATE)
  const verifyResult = await verifyOtpTransaction(email, OtpPurpose.EMAIL_VERIFICATION, normalizedCode);

  switch (verifyResult.status) {
    case 'INVALID_CODE':
      throw new BadRequestError(
        `Invalid verification code. ${verifyResult.attemptsLeft} attempts remaining.`,
        undefined,
        'INVALID_CODE'
      );
    case 'ATTEMPTS_EXHAUSTED':
      throw new BadRequestError(
        'Verification code has been invalidated due to too many failed attempts. Please request a new code.',
        undefined,
        'ATTEMPTS_EXHAUSTED'
      );
    case 'EXPIRED_OR_NOT_FOUND':
      throw new BadRequestError(
        'Verification code has expired or does not exist. Please request a new code.',
        undefined,
        'EXPIRED_CODE'
      );
    case 'NOT_ACTIVE':
      throw new BadRequestError(
        'Verification code is still being dispatched. Please check your inbox in a moment.',
        undefined,
        'CODE_NOT_ACTIVE'
      );
    case 'ALREADY_CONSUMED':
      throw new ConflictError(
        'Verification code was already used.',
        undefined,
        'CODE_ALREADY_USED'
      );
    case 'SUCCESS':
      break;
  }

  // 3. OTP verified! Transition DB to VERIFICATION_SYNC_PENDING & create durable Outbox event
  await prisma.$transaction(async (tx) => {
    await tx.user.update({
      where: { id: uid },
      data: {
        emailVerificationStatus: 'VERIFICATION_SYNC_PENDING',
      },
    });

    await createOutboxEvent(
      uid,
      'SYNC_FIREBASE_EMAIL_VERIFIED',
      { emailVerified: true },
      tx
    );
  });

  // 4. Inline synchronous attempt to update Firebase Admin claim
  try {
    const adminApp = getFirebaseAdmin();
    await adminApp.auth().updateUser(uid, { emailVerified: true });

    // Synchronous success: transition DB to VERIFIED & complete outbox event
    await prisma.$transaction(async (tx) => {
      await tx.user.update({
        where: { id: uid },
        data: {
          emailVerificationStatus: 'VERIFIED',
          emailVerified: true,
        },
      });

      await tx.outboxEvent.updateMany({
        where: {
          aggregateId: uid,
          eventType: 'SYNC_FIREBASE_EMAIL_VERIFIED',
          status: 'PENDING',
        },
        data: {
          status: 'COMPLETED',
          updatedAt: new Date(),
        },
      });
    });

    logger.info('Email verification completed synchronously', {
      userId: uid,
      email: maskEmail(email),
    });

    return res.status(HttpCode.OK).json({
      success: true,
      data: {
        status: 'VERIFIED',
        message: 'Email verified successfully.',
      },
      meta: { timestamp: new Date().toISOString() },
    });
  } catch (fbError: unknown) {
    const errorMessage = fbError instanceof Error ? fbError.message : String(fbError);
    logger.warn('Firebase Admin claim sync delayed; outbox worker will retry', {
      userId: uid,
      error: errorMessage,
    });

    // Firebase update failed or timed out: DO NOT report final success!
    // Return HTTP 202 Accepted with VERIFICATION_SYNC_PENDING
    return res.status(202).json({
      success: true,
      data: {
        status: 'VERIFICATION_SYNC_PENDING',
        message: 'Verification code accepted. Synchronizing authentication session...',
      },
      meta: { timestamp: new Date().toISOString() },
    });
  }
}

/**
 * POST /api/v1/auth/otp/request-password-reset
 * Public: Anti-enumeration password reset OTP request.
 */
export async function handleRequestPasswordReset(req: VercelRequest, res: VercelResponse) {
  const { email } = req.body || {};

  if (!email || typeof email !== 'string' || !email.includes('@')) {
    throw new BadRequestError('Please provide a valid email address.');
  }

  const normalizedEmail = email.trim().toLowerCase();

  try {
    // Check if user exists in Firebase Admin SDK
    const adminApp = getFirebaseAdmin();
    const userRecord = await adminApp.auth().getUserByEmail(normalizedEmail).catch(() => null);

    if (userRecord) {
      // Dispatches OTP via Gmail SMTP and stores HMAC
      await requestOtpWithCooldown(normalizedEmail, OtpPurpose.PASSWORD_RESET, userRecord.uid);
    } else {
      // Anti-enumeration: simulate realistic delay
      await new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 200));
    }
  } catch (err: unknown) {
    if (err instanceof TooManyRequestsError) {
      throw err;
    }
    const errorMessage = err instanceof Error ? err.message : String(err);
    logger.warn('Password reset request encountered non-fatal error', {
      email: maskEmail(normalizedEmail),
      error: errorMessage,
    });
  }

  // Always return identical success response to prevent account enumeration
  return res.status(HttpCode.OK).json({
    success: true,
    data: {
      message: 'If an account exists with this email, a 6-digit password reset code has been sent.',
    },
    meta: { timestamp: new Date().toISOString() },
  });
}

/**
 * POST /api/v1/auth/otp/reset-password
 * Public: Verifies password reset OTP, updates password via Firebase Admin, and revokes active sessions.
 */
export async function handleResetPassword(req: VercelRequest, res: VercelResponse) {
  const { email, code, newPassword } = req.body || {};

  if (!email || typeof email !== 'string' || !email.includes('@')) {
    throw new BadRequestError('Please provide a valid email address.');
  }
  if (!code || typeof code !== 'string' || !/^\d{6}$/.test(code.trim())) {
    throw new BadRequestError('Please provide a valid 6-digit numeric verification code.');
  }
  if (!newPassword || typeof newPassword !== 'string' || newPassword.length < 8) {
    throw new BadRequestError('Password must be at least 8 characters long.');
  }

  const normalizedEmail = email.trim().toLowerCase();
  const normalizedCode = code.trim();

  // 1. Verify OTP atomically
  const verifyResult = await verifyOtpTransaction(normalizedEmail, OtpPurpose.PASSWORD_RESET, normalizedCode);

  switch (verifyResult.status) {
    case 'INVALID_CODE':
      throw new BadRequestError(
        `Invalid verification code. ${verifyResult.attemptsLeft} attempts remaining.`,
        undefined,
        'INVALID_CODE'
      );
    case 'ATTEMPTS_EXHAUSTED':
      throw new BadRequestError(
        'Verification code has been invalidated due to too many failed attempts. Please request a new code.',
        undefined,
        'ATTEMPTS_EXHAUSTED'
      );
    case 'EXPIRED_OR_NOT_FOUND':
      throw new BadRequestError(
        'Verification code has expired or does not exist. Please request a new code.',
        undefined,
        'EXPIRED_CODE'
      );
    case 'NOT_ACTIVE':
      throw new BadRequestError(
        'Verification code is still being dispatched. Please check your inbox.',
        undefined,
        'CODE_NOT_ACTIVE'
      );
    case 'ALREADY_CONSUMED':
      throw new ConflictError(
        'Verification code was already used.',
        undefined,
        'CODE_ALREADY_USED'
      );
    case 'SUCCESS':
      break;
  }

  // 2. Fetch Firebase user and update password
  const adminApp = getFirebaseAdmin();
  const fbUser = await adminApp.auth().getUserByEmail(normalizedEmail);

  await adminApp.auth().updateUser(fbUser.uid, {
    password: newPassword,
  });

  // 3. Revoke all existing refresh tokens for security
  await adminApp.auth().revokeRefreshTokens(fbUser.uid);

  logger.info('Password successfully reset via OTP; refresh tokens revoked', {
    userId: fbUser.uid,
    email: maskEmail(normalizedEmail),
  });

  return res.status(HttpCode.OK).json({
    success: true,
    data: {
      message: 'Password has been reset successfully. Please log in with your new password.',
    },
    meta: { timestamp: new Date().toISOString() },
  });
}

/**
 * GET /api/v1/auth/otp/status
 * Authenticated: Returns the current verification status, self-healing from Firebase if needed.
 */
export async function handleGetOtpStatus(req: AuthenticatedRequest, res: VercelResponse) {
  const { uid, emailVerified: tokenEmailVerified } = req.user;

  let user = await prisma.user.findUnique({ where: { id: uid } });

  // Self-heal: if token has email_verified=true (e.g. Google Sign-In) but DB is not VERIFIED
  if (tokenEmailVerified && user?.emailVerificationStatus !== 'VERIFIED') {
    user = await prisma.user.update({
      where: { id: uid },
      data: {
        emailVerified: true,
        emailVerificationStatus: 'VERIFIED',
      },
    });
  }

  const isFullyVerified = user?.emailVerificationStatus === 'VERIFIED' && tokenEmailVerified;

  return res.status(HttpCode.OK).json({
    success: true,
    data: {
      status: user?.emailVerificationStatus || 'PENDING_VERIFICATION',
      emailVerified: isFullyVerified,
    },
    meta: { timestamp: new Date().toISOString() },
  });
}


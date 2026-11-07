import crypto from 'node:crypto';
import { prisma } from '../db/client.js';
import { OtpPurpose, OtpStatus } from '@prisma/client';
import { sendOtpEmail } from './smtpService.js';
import { TooManyRequestsError, InternalServerError } from '../errors/AppError.js';
import { logger } from '../utils/logger.js';
import { maskEmail } from '../utils/htmlEscape.js';

const OTP_SECRET = process.env.OTP_SECRET || 'dev_secret_32_bytes_super_secure_value!';
const OTP_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
const OTP_RESEND_COOLDOWN_MS = 60 * 1000; // 60 seconds

export type VerifyOtpResult =
  | { status: 'SUCCESS'; otpId: string; userId?: string | null }
  | { status: 'INVALID_CODE'; attemptsLeft: number }
  | { status: 'ATTEMPTS_EXHAUSTED' }
  | { status: 'EXPIRED_OR_NOT_FOUND' }
  | { status: 'NOT_ACTIVE'; currentStatus: OtpStatus }
  | { status: 'ALREADY_CONSUMED' };

export interface RequestOtpResult {
  success: boolean;
  resendAvailableAt: Date;
  expiresAt: Date;
}

/**
 * Computes a deterministic HMAC-SHA256 hash of the normalized email and 6-digit code.
 */
export function computeOtpHmac(code: string, email: string): string {
  return crypto
    .createHmac('sha256', OTP_SECRET)
    .update(`${email.trim().toLowerCase()}:${code}`)
    .digest('hex');
}

/**
 * Generates a cryptographically secure 6-digit numeric OTP.
 */
export function generateNumericOtp(): string {
  return crypto.randomInt(100000, 1000000).toString();
}

/**
 * Requests and dispatches an OTP with durable state management, advisory locking, and cooldown enforcement.
 */
export async function requestOtpWithCooldown(
  email: string,
  purpose: OtpPurpose,
  userId?: string | null
): Promise<RequestOtpResult> {
  const normalizedEmail = email.trim().toLowerCase();
  const rawCode = generateNumericOtp();
  const codeHash = computeOtpHmac(rawCode, normalizedEmail);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + OTP_EXPIRY_MS);
  const resendAvailableAt = new Date(now.getTime() + OTP_RESEND_COOLDOWN_MS);

  // 1. Transactional check & reserve with PostgreSQL advisory lock
  const otpRecord = await prisma.$transaction(
    async (tx) => {
      // Advisory transaction lock on (email:purpose) prevents duplicate initial inserts
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`${normalizedEmail}:${purpose}`}))`;

      // Check existing active or pending OTP for cooldown
      const existingRows = await tx.$queryRaw<Array<{ id: string; resendAvailableAt: Date; status: OtpStatus }>>`
        SELECT id, "resendAvailableAt", status
        FROM "email_otps"
        WHERE email = ${normalizedEmail}
          AND purpose = ${purpose}::"OtpPurpose"
          AND status IN ('ACTIVE'::"OtpStatus", 'PENDING_DISPATCH'::"OtpStatus")
        ORDER BY "createdAt" DESC
        LIMIT 1
        FOR UPDATE;
      `;

      if (existingRows.length > 0) {
        const existing = existingRows[0];
        const resendMs = new Date(existing.resendAvailableAt).getTime() - Date.now();
        if (resendMs > 0) {
          const waitSeconds = Math.ceil(resendMs / 1000);
          throw new TooManyRequestsError(
            `Please wait ${waitSeconds} seconds before requesting a new code.`,
            undefined,
            'COOLDOWN_ACTIVE'
          );
        }

        // Cancel previous active/pending records
        await tx.$executeRaw`
          UPDATE "email_otps"
          SET status = 'CANCELLED'::"OtpStatus", "updatedAt" = NOW()
          WHERE id = ${existing.id};
        `;
      }

      // Create new OTP record in PENDING_DISPATCH state
      const created = await tx.emailOtp.create({
        data: {
          email: normalizedEmail,
          userId: userId || null,
          purpose,
          codeHash,
          status: OtpStatus.PENDING_DISPATCH,
          attemptsLeft: 5,
          expiresAt,
          resendAvailableAt,
        },
      });

      return created;
    },
    { timeout: 10000 }
  );

  // 2. Dispatch via SMTP outside the database lock
  try {
    await sendOtpEmail({
      to: normalizedEmail,
      code: rawCode,
      purpose: purpose === OtpPurpose.EMAIL_VERIFICATION ? 'EMAIL_VERIFICATION' : 'PASSWORD_RESET',
      expiresInMinutes: 10,
    });

    // 3. Mark ACTIVE upon confirmed dispatch
    await prisma.emailOtp.update({
      where: { id: otpRecord.id },
      data: {
        status: OtpStatus.ACTIVE,
        dispatchedAt: new Date(),
      },
    });

    logger.info('OTP successfully generated, persisted, and dispatched', {
      email: maskEmail(normalizedEmail),
      purpose,
      otpId: otpRecord.id,
    });

    return {
      success: true,
      resendAvailableAt,
      expiresAt,
    };
  } catch (smtpError: any) {
    // 4. On SMTP failure, transition to FAILED and reset cooldown so user can retry immediately
    await prisma.emailOtp.update({
      where: { id: otpRecord.id },
      data: {
        status: OtpStatus.FAILED,
        resendAvailableAt: new Date(), // Immediate retry allowed
        dispatchError: smtpError?.message || 'SMTP dispatch failed',
      },
    });

    logger.error('Failed to deliver OTP email; cooldown reset', {
      email: maskEmail(normalizedEmail),
      error: smtpError?.message,
    });

    throw new InternalServerError(
      'Failed to send verification email. Please check your address and try again.',
      undefined,
      'EMAIL_DELIVERY_FAILED'
    );
  }
}

/**
 * Atomically verifies an entered OTP using row-level locking (SELECT FOR UPDATE).
 */
export async function verifyOtpTransaction(
  email: string,
  purpose: OtpPurpose,
  enteredCode: string
): Promise<VerifyOtpResult> {
  const normalizedEmail = email.trim().toLowerCase();

  return await prisma.$transaction(
    async (tx) => {
      // 1. Acquire exclusive row lock using SELECT FOR UPDATE on the latest OTP record
      const rows = await tx.$queryRaw<Array<{
        id: string;
        email: string;
        userId: string | null;
        codeHash: string;
        status: OtpStatus;
        attemptsLeft: number;
        expiresAt: Date;
        consumedAt: Date | null;
      }>>`
        SELECT 
          id, email, "userId", "codeHash", status, "attemptsLeft", "expiresAt", "consumedAt"
        FROM "email_otps"
        WHERE email = ${normalizedEmail}
          AND purpose = ${purpose}::"OtpPurpose"
        ORDER BY "createdAt" DESC
        LIMIT 1
        FOR UPDATE;
      `;

      if (rows.length === 0) {
        return { status: 'EXPIRED_OR_NOT_FOUND' };
      }

      const activeOtp = rows[0];

      // 2. Strict terminal state checks
      if (activeOtp.status === OtpStatus.CONSUMED || activeOtp.consumedAt !== null) {
        return { status: 'ALREADY_CONSUMED' };
      }

      if (activeOtp.status === OtpStatus.EXHAUSTED || activeOtp.attemptsLeft <= 0) {
        return { status: 'ATTEMPTS_EXHAUSTED' };
      }

      if (activeOtp.status === OtpStatus.PENDING_DISPATCH) {
        return { status: 'NOT_ACTIVE', currentStatus: activeOtp.status };
      }

      if (activeOtp.status !== OtpStatus.ACTIVE) {
        return { status: 'EXPIRED_OR_NOT_FOUND' };
      }

      // 3. Expiration check
      if (new Date(activeOtp.expiresAt).getTime() <= Date.now()) {
        await tx.$executeRaw`
          UPDATE "email_otps"
          SET status = 'EXPIRED'::"OtpStatus", "updatedAt" = NOW()
          WHERE id = ${activeOtp.id};
        `;
        return { status: 'EXPIRED_OR_NOT_FOUND' };
      }

      // 4. Timing-safe HMAC comparison
      const enteredHash = computeOtpHmac(enteredCode, normalizedEmail);
      const isMatch = crypto.timingSafeEqual(
        Buffer.from(enteredHash, 'hex'),
        Buffer.from(activeOtp.codeHash, 'hex')
      );

      if (!isMatch) {
        const remaining = activeOtp.attemptsLeft - 1;
        if (remaining <= 0) {
          await tx.$executeRaw`
            UPDATE "email_otps"
            SET "attemptsLeft" = 0, status = 'EXHAUSTED'::"OtpStatus", "updatedAt" = NOW()
            WHERE id = ${activeOtp.id};
          `;
          return { status: 'ATTEMPTS_EXHAUSTED' };
        } else {
          await tx.$executeRaw`
            UPDATE "email_otps"
            SET "attemptsLeft" = ${remaining}, "updatedAt" = NOW()
            WHERE id = ${activeOtp.id};
          `;
          return { status: 'INVALID_CODE', attemptsLeft: remaining };
        }
      }

      // 5. Code Matches! Atomically burn and mark CONSUMED
      await tx.$executeRaw`
        UPDATE "email_otps"
        SET 
          status = 'CONSUMED'::"OtpStatus",
          "consumedAt" = NOW(),
          "attemptsLeft" = 0,
          "updatedAt" = NOW()
        WHERE id = ${activeOtp.id};
      `;

      return {
        status: 'SUCCESS',
        otpId: activeOtp.id,
        userId: activeOtp.userId,
      };
    },
    { timeout: 10000 }
  );
}


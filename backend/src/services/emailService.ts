import { getFirebaseAdmin } from '../utils/firebaseAdmin.js';
import { renderVerificationEmail } from './emailTemplate.js';
import { maskEmail } from '../utils/htmlEscape.js';
import { logger } from '../utils/logger.js';
import { BadRequestError, InternalServerError } from '../errors/AppError.js';
import type * as admin from 'firebase-admin';

export interface EmailDispatchResult {
  success: boolean;
  messageId?: string;
  provider: string;
}

interface ResendResponse {
  id?: string;
  message?: string;
  name?: string;
}

/**
 * Generates an authentic Firebase email verification link using the Firebase Admin SDK.
 * Respects Android package configuration and continue URL settings.
 */
export async function generateEmailVerificationLink(email: string): Promise<string> {
  const adminApp = getFirebaseAdmin();
  const continueUrl = process.env.AUTH_ACTION_CONTINUE_URL || 'https://notas-7db14.firebaseapp.com/__/auth/action';

  const actionCodeSettings: admin.auth.ActionCodeSettings = {
    url: continueUrl,
    handleCodeInApp: true,
    android: {
      packageName: 'haddad.three.notas',
      installApp: false,
    },
  };

  try {
    return await adminApp.auth().generateEmailVerificationLink(email, actionCodeSettings);
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    logger.warn('Failed to generate verification link with ActionCodeSettings; falling back to default link generator', {
      error: errorMessage,
    });
    return await adminApp.auth().generateEmailVerificationLink(email);
  }
}

/**
 * Sends a transactional verification email using the configured provider (Resend API or simulated in dev/test).
 */
export async function sendEmail(options: {
  to: string;
  subject: string;
  html: string;
}): Promise<EmailDispatchResult> {
  const { to, subject, html } = options;
  const resendApiKey = process.env.RESEND_API_KEY;

  if (resendApiKey) {
    const fromAddress = process.env.EMAIL_FROM || 'Notas <onboarding@resend.dev>';

    try {
      const response = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${resendApiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          from: fromAddress,
          to: [to],
          subject,
          html,
        }),
      });

      const responseData = (await response.json().catch(() => ({}))) as ResendResponse;

      if (!response.ok) {
        logger.error('Resend API responded with error', {
          status: response.status,
          error: responseData?.message || responseData?.name || 'Unknown Resend error',
        });
        throw new InternalServerError(
          `Failed to deliver email: ${responseData?.message || 'Email provider rejected delivery'}`,
          undefined,
          'EMAIL_DELIVERY_FAILED'
        );
      }

      logger.info('Verification email dispatched via Resend', {
        messageId: responseData.id,
        recipient: maskEmail(to),
      });

      return {
        success: true,
        messageId: responseData.id,
        provider: 'resend',
      };
    } catch (error: unknown) {
      if (error instanceof InternalServerError) throw error;
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to communicate with Resend API', { error: errorMessage });
      throw new InternalServerError('Network error communicating with email delivery service', undefined, 'EMAIL_SERVICE_UNAVAILABLE');
    }
  }

  // Fallback for local development and automated testing
  if (process.env.NODE_ENV === 'test' || process.env.NODE_ENV === 'development') {
    logger.info('Simulated verification email dispatch (RESEND_API_KEY not configured)', {
      recipient: maskEmail(to),
      subject,
    });
    return {
      success: true,
      messageId: 'simulated-dev-message-id',
      provider: 'simulated',
    };
  }

  throw new InternalServerError(
    'No transactional email provider is configured. Please set RESEND_API_KEY in the Vercel project environment variables.',
    undefined,
    'EMAIL_PROVIDER_NOT_CONFIGURED'
  );
}

/**
 * High-level service method orchestrating custom verification email dispatch.
 * 1. Validates user verification status and email presence.
 * 2. Generates genuine Firebase link via Admin SDK.
 * 3. Renders the Notas custom branded responsive HTML template.
 * 4. Dispatches the email through Resend / configured provider.
 */
export async function dispatchCustomVerificationEmail(user: {
  uid: string;
  email: string;
  name?: string | null;
  emailVerified: boolean;
}): Promise<{ message: string; email: string }> {
  if (!user.email || !user.email.trim()) {
    throw new BadRequestError('User does not have an associated email address', undefined, 'NO_EMAIL');
  }

  if (user.emailVerified) {
    throw new BadRequestError('Email address is already verified', undefined, 'EMAIL_ALREADY_VERIFIED');
  }

  // 1. Generate real verification link using Firebase Admin SDK
  const verificationLink = await generateEmailVerificationLink(user.email);

  if (!verificationLink || !verificationLink.startsWith('https://')) {
    throw new InternalServerError('Failed to generate valid Firebase email verification URL', undefined, 'LINK_GENERATION_FAILED');
  }

  // 2. Render branded HTML template
  const html = renderVerificationEmail({
    displayName: user.name,
    email: user.email,
    verificationLink,
  });

  // 3. Dispatch email
  await sendEmail({
    to: user.email,
    subject: 'Verify your email for Notas',
    html,
  });

  return {
    message: 'Verification email sent successfully',
    email: user.email,
  };
}


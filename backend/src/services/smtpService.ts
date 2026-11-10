import nodemailer from 'nodemailer';
import { logger } from '../utils/logger.js';
import { maskEmail } from '../utils/htmlEscape.js';
import { InternalServerError } from '../errors/AppError.js';

export interface SmtpSendResult {
  success: boolean;
  messageId?: string;
  provider: 'gmail_smtp' | 'simulated';
}

/**
 * Creates a Nodemailer transport instance using backend environment variables.
 */
function createSmtpTransport() {
  const host = process.env.SMTP_HOST || 'smtp.gmail.com';
  const port = parseInt(process.env.SMTP_PORT || '587', 10);
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (user && pass) {
    return nodemailer.createTransport({
      host,
      port,
      secure: port === 465,
      auth: {
        user,
        pass,
      },
      tls: {
        rejectUnauthorized: process.env.NODE_ENV === 'production',
      },
    });
  }

  return null;
}

/**
 * Renders modern, responsive HTML email templates for OTP verification and password reset.
 */
export function renderOtpEmailHtml(options: {
  code: string;
  purpose: 'EMAIL_VERIFICATION' | 'PASSWORD_RESET';
  expiresInMinutes?: number;
}): { subject: string; html: string; text: string } {
  const { code, purpose, expiresInMinutes = 10 } = options;
  const isVerification = purpose === 'EMAIL_VERIFICATION';

  const subject = isVerification
    ? `Notas — Your Verification Code is ${code}`
    : `Notas — Password Reset Code: ${code}`;

  const title = isVerification ? 'Verify Your Email Address' : 'Reset Your Password';
  const description = isVerification
    ? 'Welcome to Notas! Enter the 6-digit code below in the app to verify your email and unlock cloud synchronization and backups.'
    : 'A password reset was requested for your Notas account. Enter the 6-digit code below to set a new password.';

  const warning = isVerification
    ? 'If you did not create a Notas account, you can safely ignore this email.'
    : 'If you did not request a password reset, please secure your account immediately.';

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${subject}</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #0d1117;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #c9d1d9;
    }
    .wrapper {
      width: 100%;
      table-layout: fixed;
      background-color: #0d1117;
      padding: 40px 0;
    }
    .main {
      background-color: #161b22;
      margin: 0 auto;
      width: 100%;
      max-width: 520px;
      border-radius: 16px;
      border: 1px solid #30363d;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
      overflow: hidden;
    }
    .header {
      padding: 32px 32px 20px 32px;
      text-align: center;
      border-bottom: 1px solid #21262d;
    }
    .logo-badge {
      display: inline-block;
      width: 48px;
      height: 48px;
      line-height: 48px;
      border-radius: 12px;
      background: linear-gradient(135deg, #238636 0%, #2ea043 100%);
      color: #ffffff;
      font-size: 24px;
      font-weight: 700;
      margin-bottom: 12px;
    }
    .app-title {
      font-size: 20px;
      font-weight: 600;
      color: #f0f6fc;
      margin: 0;
    }
    .content {
      padding: 32px;
    }
    .heading {
      font-size: 22px;
      font-weight: 600;
      color: #f0f6fc;
      margin: 0 0 12px 0;
      text-align: center;
    }
    .lead-text {
      font-size: 14px;
      line-height: 1.6;
      color: #8b949e;
      text-align: center;
      margin: 0 0 28px 0;
    }
    .code-container {
      background-color: #0d1117;
      border: 2px dashed #30363d;
      border-radius: 12px;
      padding: 24px;
      text-align: center;
      margin-bottom: 28px;
    }
    .otp-code {
      font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
      font-size: 38px;
      font-weight: 700;
      letter-spacing: 8px;
      color: #58a6ff;
      margin: 0;
    }
    .expiry-note {
      font-size: 12px;
      color: #8b949e;
      margin-top: 10px;
    }
    .notice {
      background-color: #21262d;
      border-left: 4px solid #f0883e;
      padding: 12px 16px;
      border-radius: 0 8px 8px 0;
      margin-bottom: 24px;
      font-size: 13px;
      color: #c9d1d9;
      line-height: 1.5;
    }
    .footer {
      padding: 20px 32px;
      text-align: center;
      border-top: 1px solid #21262d;
      font-size: 12px;
      color: #6e7681;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <table class="main" cellpadding="0" cellspacing="0" width="100%">
      <tr>
        <td class="header">
          <div class="logo-badge">N</div>
          <h1 class="app-title">Notas</h1>
        </td>
      </tr>
      <tr>
        <td class="content">
          <h2 class="heading">${title}</h2>
          <p class="lead-text">${description}</p>
          <div class="code-container">
            <div class="otp-code">${code}</div>
            <div class="expiry-note">This code expires in ${expiresInMinutes} minutes</div>
          </div>
          <div class="notice">
            ${warning}
          </div>
        </td>
      </tr>
      <tr>
        <td class="footer">
          &copy; ${new Date().getFullYear()} Notas. Strict zero-knowledge privacy.
        </td>
      </tr>
    </table>
  </div>
</body>
</html>`;

  const text = `${title}

Your 6-digit code: ${code}

This code expires in ${expiresInMinutes} minutes.

${warning}
`;

  return { subject, html, text };
}

/**
 * Dispatches an OTP email through Gmail SMTP or simulated dev fallback.
 */
export async function sendOtpEmail(options: {
  to: string;
  code: string;
  purpose: 'EMAIL_VERIFICATION' | 'PASSWORD_RESET';
  expiresInMinutes?: number;
}): Promise<SmtpSendResult> {
  const { to, code, purpose, expiresInMinutes = 10 } = options;
  const transport = createSmtpTransport();
  const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER || 'Notas <no-reply@notas.app>';
  const { subject, html, text } = renderOtpEmailHtml({ code, purpose, expiresInMinutes });

  if (transport) {
    try {
      const info = await transport.sendMail({
        from: fromAddress,
        to,
        subject,
        text,
        html,
      });

      logger.info('OTP email successfully delivered via Gmail SMTP', {
        recipient: maskEmail(to),
        messageId: info.messageId,
        purpose,
      });

      return {
        success: true,
        messageId: info.messageId,
        provider: 'gmail_smtp',
      };
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Gmail SMTP failed to deliver OTP email', {
        recipient: maskEmail(to),
        error: errorMessage,
      });
      throw new InternalServerError(
        'Failed to deliver email through mail service. Please check your address and try again.',
        undefined,
        'EMAIL_DELIVERY_FAILED'
      );
    }
  }

  // Fallback for development/testing when SMTP_USER is not configured
  logger.warn('SMTP credentials not configured; logging OTP to server console (DEV MODE ONLY)', {
    recipient: maskEmail(to),
    code,
    purpose,
  });

  return {
    success: true,
    messageId: `simulated_${Date.now()}`,
    provider: 'simulated',
  };
}


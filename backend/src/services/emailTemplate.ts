import { escapeHtml } from '../utils/htmlEscape.js';

export interface VerificationEmailTemplateOptions {
  displayName?: string | null;
  email: string;
  verificationLink: string;
  logoUrl?: string;
}

/**
 * Renders a responsive, modern HTML email for Notas email verification.
 * Follows table-based email layouts for compatibility across Gmail, Outlook, Apple Mail.
 * Primary Brand Color: #800000 (burgundy/maroon)
 * Accent Brand Color: #D4AF37 (gold)
 */
export function renderVerificationEmail(options: VerificationEmailTemplateOptions): string {
  const { displayName, email, verificationLink } = options;

  const defaultBaseUrl = (process.env.BACKEND_URL || 'https://notas-backend-iota.vercel.app').replace(/\/+$/, '');
  const rawLogoUrl = options.logoUrl || process.env.APP_LOGO_URL || `${defaultBaseUrl}/logo.png`;

  const escapedName = escapeHtml(displayName?.trim());
  const escapedEmail = escapeHtml(email);
  const escapedLink = escapeHtml(verificationLink);
  const escapedLogoUrl = escapeHtml(rawLogoUrl);
  const currentYear = new Date().getFullYear();

  const greeting = escapedName ? `Hello ${escapedName},` : 'Hello,';

  return `<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta name="color-scheme" content="dark" />
  <meta name="supported-color-schemes" content="dark" />
  <title>Verify your email for Notas</title>
</head>
<body style="margin: 0; padding: 0; background-color: #0d0d10; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale; color: #e4e4eb;">
  <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color: #0d0d10; min-height: 100vh; padding: 32px 12px;">
    <tr>
      <td align="center" valign="top">
        <!-- Container (max-width 560px) -->
        <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 560px; background-color: #17171d; border-radius: 16px; border: 1px solid #282834; overflow: hidden; box-shadow: 0 12px 36px rgba(0,0,0,0.45);">
          
          <!-- Brand Top Accent Bar -->
          <tr>
            <td style="height: 4px; background-color: #800000; background: linear-gradient(90deg, #800000 0%, #D4AF37 50%, #800000 100%); line-height: 4px; font-size: 1px;">&nbsp;</td>
          </tr>

          <!-- Header with Notas Logo -->
          <tr>
            <td align="center" style="padding: 36px 24px 20px 24px;">
              <table role="presentation" border="0" cellpadding="0" cellspacing="0">
                <tr>
                  <td align="center">
                    <img src="${escapedLogoUrl}" alt="Notas Logo" width="68" height="68" style="display: block; width: 68px; height: 68px; border-radius: 16px; border: 1px solid #D4AF37;" />
                  </td>
                </tr>
                <tr>
                  <td align="center" style="padding-top: 14px;">
                    <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">Notas</h1>
                  </td>
                </tr>
                <tr>
                  <td align="center" style="padding-top: 4px;">
                    <span style="display: inline-block; color: #D4AF37; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 1.8px;">Mindful Note Taking</span>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Divider -->
          <tr>
            <td style="padding: 0 32px;">
              <div style="height: 1px; background-color: #242430;"></div>
            </td>
          </tr>

          <!-- Main Content -->
          <tr>
            <td style="padding: 28px 32px 32px 32px;">
              <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 20px; font-weight: 600; line-height: 1.3;">${greeting}</h2>
              <p style="margin: 0 0 20px 0; color: #b4b4c4; font-size: 15px; line-height: 1.6;">
                Welcome to <strong style="color: #ffffff;">Notas</strong>. To protect your private notes and activate secure cloud synchronization across your devices, please verify that <span style="color: #D4AF37;">${escapedEmail}</span> belongs to you.
              </p>

              <!-- Call to Action Button -->
              <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="margin: 32px 0;">
                <tr>
                  <td align="center">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0">
                      <tr>
                        <td align="center" bgcolor="#800000" style="border-radius: 8px; border: 1px solid #D4AF37; box-shadow: 0 4px 14px rgba(128, 0, 0, 0.4);">
                          <a href="${escapedLink}" target="_blank" rel="noopener noreferrer" style="display: inline-block; padding: 14px 36px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; font-size: 16px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 8px; letter-spacing: 0.3px;">
                            Verify Email
                          </a>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>

              <!-- Fallback Link Box -->
              <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="margin: 24px 0 0 0; background-color: #121217; border-radius: 10px; border: 1px solid #232330;">
                <tr>
                  <td style="padding: 16px 18px;">
                    <p style="margin: 0 0 8px 0; color: #88889a; font-size: 13px; line-height: 1.4;">
                      Button not working? Copy and paste this URL into your browser:
                    </p>
                    <p style="margin: 0; word-break: break-all; font-size: 12px; line-height: 1.5;">
                      <a href="${escapedLink}" target="_blank" rel="noopener noreferrer" style="color: #D4AF37; text-decoration: underline;">
                        ${escapedLink}
                      </a>
                    </p>
                  </td>
                </tr>
              </table>

              <!-- Safe to Ignore Notice -->
              <p style="margin: 28px 0 0 0; color: #727282; font-size: 13px; line-height: 1.5;">
                If you did not create a Notas account, you can safely disregard this email. No account will be activated or synced without verification.
              </p>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td style="background-color: #121217; padding: 22px 24px; border-top: 1px solid #22222d; text-align: center;">
              <p style="margin: 0 0 6px 0; color: #585868; font-size: 12px;">
                &copy; ${currentYear} Notas. All rights reserved.
              </p>
              <p style="margin: 0; color: #444452; font-size: 11px;">
                Designed with craftsmanship for Android &amp; Cloud
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

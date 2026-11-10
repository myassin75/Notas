import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  generateEmailVerificationLink,
  sendEmail,
  dispatchCustomVerificationEmail,
} from '../src/services/emailService.js';
import { renderVerificationEmail } from '../src/services/emailTemplate.js';
import { escapeHtml, maskEmail } from '../src/utils/htmlEscape.js';
import * as firebaseAdminModule from '../src/utils/firebaseAdmin.js';
import handler from '../src/api/index.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';
import type * as admin from 'firebase-admin';

vi.mock('../src/db/client.js', () => ({
  prisma: {
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue(null),
    },
  },
  checkDatabaseHealth: vi.fn().mockResolvedValue(true),
}));

vi.mock('../src/middleware/auth.js', () => ({
  authenticate: vi.fn().mockImplementation(async (req) => {
    if (!req.user) {
      req.user = { uid: 'test-user', email: 'test@example.com', emailVerified: false };
    }
    return req.user;
  }),
}));

// Mock Firebase Admin
const mockGenerateEmailVerificationLink = vi.fn();
const mockAdminAuth = {
  generateEmailVerificationLink: mockGenerateEmailVerificationLink,
};
const mockFirebaseApp = {
  auth: () => mockAdminAuth,
};

vi.spyOn(firebaseAdminModule, 'getFirebaseAdmin').mockReturnValue(mockFirebaseApp as unknown as admin.app.App);

describe('Email Template & HTML Escaping', () => {
  it('correctly escapes HTML special characters to prevent XSS', () => {
    expect(escapeHtml('<script>alert("xss")</script>')).toBe(
      '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;'
    );
    expect(escapeHtml("Tom & Jerry's")).toBe('Tom &amp; Jerry&#039;s');
    expect(escapeHtml(null)).toBe('');
    expect(escapeHtml(undefined)).toBe('');
  });

  it('masks email addresses for safe logging', () => {
    expect(maskEmail('alex.developer@example.com')).toBe('a***r@example.com');
    expect(maskEmail('me@example.com')).toBe('m***@example.com');
    expect(maskEmail('')).toBe('***');
  });

  it('renders modern responsive HTML with Notas branding, logo, and colors', () => {
    const html = renderVerificationEmail({
      displayName: 'Alex Rivers',
      email: 'alex@example.com',
      verificationLink: 'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=secureCode123',
    });

    // Brand colors
    expect(html).toContain('#800000'); // Maroon
    expect(html).toContain('#D4AF37'); // Gold

    // Application branding & logo
    expect(html).toContain('Notas');
    expect(html).toContain('Mindful Note Taking');
    expect(html).toContain('logo.png');

    // Greeting & user info
    expect(html).toContain('Hello Alex Rivers,');
    expect(html).toContain('alex@example.com');

    // Button & verification link
    expect(html).toContain('Verify Email');
    expect(html).toContain('https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&amp;oobCode=secureCode123');

    // Ignore disclaimer
    expect(html).toContain('safely disregard this email');
  });

  it('handles empty display name gracefully with generic greeting', () => {
    const html = renderVerificationEmail({
      displayName: null,
      email: 'user@example.com',
      verificationLink: 'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=code456',
    });

    expect(html).toContain('Hello,');
    expect(html).not.toContain('Hello null,');
  });

  it('escapes malicious display names in email body', () => {
    const html = renderVerificationEmail({
      displayName: '<img src=x onerror=alert(1)>',
      email: 'hacker@example.com',
      verificationLink: 'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=safe',
    });

    expect(html).not.toContain('<img src=x onerror=alert(1)>');
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
  });
});

describe('Email Service — Verification Link Generation & Dispatch', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.clearAllMocks();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  it('generates genuine verification link via Firebase Admin SDK', async () => {
    mockGenerateEmailVerificationLink.mockResolvedValue(
      'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&apiKey=AIzaSy...&oobCode=adminCode999'
    );

    const link = await generateEmailVerificationLink('alice@example.com');

    expect(mockGenerateEmailVerificationLink).toHaveBeenCalledWith(
      'alice@example.com',
      expect.objectContaining({
        url: expect.stringContaining('/__/auth/action'),
        handleCodeInApp: true,
        android: {
          packageName: 'haddad.three.notas',
          installApp: false,
        },
      })
    );
    expect(link).toContain('adminCode999');
  });

  it('falls back to default link generator if ActionCodeSettings throws', async () => {
    mockGenerateEmailVerificationLink
      .mockRejectedValueOnce(new Error('auth/unauthorized-continue-uri'))
      .mockResolvedValueOnce('https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=fallbackCode');

    const link = await generateEmailVerificationLink('bob@example.com');

    expect(mockGenerateEmailVerificationLink).toHaveBeenCalledTimes(2);
    expect(mockGenerateEmailVerificationLink).toHaveBeenLastCalledWith('bob@example.com');
    expect(link).toBe('https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=fallbackCode');
  });

  it('dispatches email via Resend API when RESEND_API_KEY is configured', async () => {
    process.env.RESEND_API_KEY = 're_test_key_12345';
    process.env.EMAIL_FROM = 'Notas <noreply@notas.app>';

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ id: 'resend_msg_abc123' }),
    });
    vi.stubGlobal('fetch', mockFetch);

    const result = await sendEmail({
      to: 'charlie@example.com',
      subject: 'Verify your email for Notas',
      html: '<p>Click here</p>',
    });

    expect(mockFetch).toHaveBeenCalledWith(
      'https://api.resend.com/emails',
      expect.objectContaining({
        method: 'POST',
        headers: {
          Authorization: 'Bearer re_test_key_12345',
          'Content-Type': 'application/json',
        },
        body: expect.stringContaining('charlie@example.com'),
      })
    );
    expect(result.success).toBe(true);
    expect(result.messageId).toBe('resend_msg_abc123');
    expect(result.provider).toBe('resend');

    vi.unstubAllGlobals();
  });

  it('throws InternalServerError when Resend API returns an error response', async () => {
    process.env.RESEND_API_KEY = 're_test_key_bad';

    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ message: 'Domain not verified' }),
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      sendEmail({
        to: 'david@example.com',
        subject: 'Verify your email for Notas',
        html: '<p>Click here</p>',
      })
    ).rejects.toThrow('Failed to deliver email: Domain not verified');

    vi.unstubAllGlobals();
  });

  it('rejects verification request if user email is already verified', async () => {
    await expect(
      dispatchCustomVerificationEmail({
        uid: 'user-already-verified',
        email: 'verified@example.com',
        name: 'Verified User',
        emailVerified: true,
      })
    ).rejects.toThrow('Email address is already verified');
  });

  it('rejects verification request if user has no email', async () => {
    await expect(
      dispatchCustomVerificationEmail({
        uid: 'user-no-email',
        email: '',
        name: 'Anonymous',
        emailVerified: false,
      })
    ).rejects.toThrow('User does not have an associated email address');
  });

  it('orchestrates complete verification email flow for unverified user', async () => {
    mockGenerateEmailVerificationLink.mockResolvedValue(
      'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=verified123'
    );

    const result = await dispatchCustomVerificationEmail({
      uid: 'user-unverified',
      email: 'newuser@example.com',
      name: 'New User',
      emailVerified: false,
    });

    expect(result.email).toBe('newuser@example.com');
    expect(result.message).toBe('Verification email sent successfully');
  });
});

describe('API Route Integration — POST /api/v1/auth/send-verification-email', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function createMockResponse() {
    const res: Record<string, unknown> = {
      statusCode: 200,
      headers: {},
      body: null,
      ended: false,
    };
    res.setHeader = vi.fn((key: string, val: string) => {
      (res.headers as Record<string, string>)[key] = val;
      return res;
    });
    res.status = vi.fn((code: number) => {
      res.statusCode = code;
      return res;
    });
    res.json = vi.fn((data: unknown) => {
      res.body = data;
      return res;
    });
    return res as unknown as VercelResponse & {
      statusCode: number;
      headers: Record<string, string>;
      body: unknown;
    };
  }

  it('responds with 200 and dispatches custom verification email for authenticated unverified user', async () => {
    mockGenerateEmailVerificationLink.mockResolvedValue(
      'https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=testOob'
    );

    const req = {
      method: 'POST',
      headers: { authorization: 'Bearer mock-valid-token' },
      query: { path: 'auth/send-verification-email' },
      user: {
        uid: 'unverified-uid',
        email: 'unverified@example.com',
        name: 'Test Unverified',
        emailVerified: false,
      },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toMatchObject({
      success: true,
      data: {
        message: 'Verification email sent successfully',
        email: 'unverified@example.com',
      },
    });
  });

  it('responds with 400 when user email is already verified', async () => {
    const req = {
      method: 'POST',
      headers: { authorization: 'Bearer mock-valid-token' },
      query: { path: 'auth/send-verification-email' },
      user: {
        uid: 'already-verified-uid',
        email: 'verified@example.com',
        name: 'Already Verified',
        emailVerified: true,
      },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(res.body).toMatchObject({
      success: false,
      error: expect.objectContaining({
        code: 'EMAIL_ALREADY_VERIFIED',
      }),
    });
  });
});

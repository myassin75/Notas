import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  computeOtpHmac,
  generateNumericOtp,
  requestOtpWithCooldown,
  verifyOtpTransaction,
} from '../src/services/otpService.js';
import { OtpPurpose, OtpStatus } from '@prisma/client';
import handler from '../src/api/index.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';

const { mockPrisma, resetInMemOtps } = vi.hoisted(() => {
  interface InMemOtp {
    id: string;
    email: string;
    userId?: string | null;
    purpose: any;
    codeHash: string;
    status: any;
    attemptsLeft: number;
    expiresAt: Date;
    resendAvailableAt: Date;
    consumedAt: Date | null;
    createdAt: Date;
    updatedAt: Date;
    dispatchedAt?: Date | null;
    dispatchError?: string | null;
  }

  let inMemOtps: InMemOtp[] = [];
  let idCounter = 1;

  function reset() {
    inMemOtps = [];
    idCounter = 1;
  }

  const emailOtp = {
    deleteMany: vi.fn(async ({ where }: { where?: { email?: string } } = {}) => {
      if (where?.email) {
        const norm = where.email.trim().toLowerCase();
        const before = inMemOtps.length;
        inMemOtps = inMemOtps.filter((o) => o.email !== norm);
        return { count: before - inMemOtps.length };
      }
      const count = inMemOtps.length;
      inMemOtps = [];
      return { count };
    }),

    create: vi.fn(async ({ data }: { data: any }) => {
      const now = new Date();
      const record: InMemOtp = {
        id: `otp-${idCounter++}`,
        email: data.email.trim().toLowerCase(),
        userId: data.userId || null,
        purpose: data.purpose,
        codeHash: data.codeHash,
        status: data.status,
        attemptsLeft: data.attemptsLeft ?? 5,
        expiresAt: data.expiresAt instanceof Date ? data.expiresAt : new Date(data.expiresAt),
        resendAvailableAt:
          data.resendAvailableAt instanceof Date
            ? data.resendAvailableAt
            : new Date(data.resendAvailableAt),
        consumedAt: null,
        createdAt: now,
        updatedAt: now,
      };
      inMemOtps.push(record);
      return { ...record };
    }),

    update: vi.fn(async ({ where, data }: { where: { id: string }; data: any }) => {
      const idx = inMemOtps.findIndex((o) => o.id === where.id);
      if (idx === -1) throw new Error(`Record not found: ${where.id}`);
      inMemOtps[idx] = {
        ...inMemOtps[idx],
        ...data,
        updatedAt: new Date(),
      };
      return { ...inMemOtps[idx] };
    }),

    findUnique: vi.fn(async ({ where }: { where: { id: string } }) => {
      const found = inMemOtps.find((o) => o.id === where.id);
      return found ? { ...found } : null;
    }),
  };

  const user = {
    findUnique: vi.fn(async ({ where }: { where: { id?: string; email?: string } }) => {
      return {
        id: where.id || 'test-user-123',
        email: where.email || 'registered@notas.app',
        emailVerified: false,
        emailVerificationStatus: 'UNVERIFIED',
      };
    }),
    update: vi.fn(async ({ where, data }: { where: { id: string }; data: any }) => {
      return {
        id: where.id,
        email: 'registered@notas.app',
        ...data,
      };
    }),
  };

  const outboxEvent = {
    create: vi.fn(async ({ data }: any) => ({ id: 'outbox-1', ...data })),
    updateMany: vi.fn(async () => ({ count: 1 })),
  };

  const $queryRaw = vi.fn(async (strings: any, ...values: any[]) => {
    const rawSql =
      typeof strings === 'string'
        ? strings
        : strings?.raw
          ? strings.raw.join(' ')
          : Array.isArray(strings)
            ? strings.join(' ')
            : '';

    let emailVal = '';
    let purposeVal = '';
    for (const v of values) {
      if (typeof v === 'string') {
        if (v.includes('@')) emailVal = v.trim().toLowerCase();
        if (v === 'EMAIL_VERIFICATION' || v === 'PASSWORD_RESET') purposeVal = v;
      }
    }

    let matches = inMemOtps.filter((o) => {
      if (emailVal && o.email !== emailVal) return false;
      if (purposeVal && o.purpose !== purposeVal) return false;
      return true;
    });

    if (rawSql.includes('ACTIVE') && rawSql.includes('PENDING_DISPATCH')) {
      matches = matches.filter((o) => o.status === 'ACTIVE' || o.status === 'PENDING_DISPATCH');
    }

    matches.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
    return matches.slice(0, 1).map((m) => ({ ...m }));
  });

  const $executeRaw = vi.fn(async (strings: any, ...values: any[]) => {
    const rawSql =
      typeof strings === 'string'
        ? strings
        : strings?.raw
          ? strings.raw.join(' ')
          : Array.isArray(strings)
            ? strings.join(' ')
            : '';

    if (rawSql.includes('UPDATE "email_otps"')) {
      for (const otp of inMemOtps) {
        if (values.includes(otp.id)) {
          if (rawSql.includes("'CANCELLED'")) {
            otp.status = 'CANCELLED';
          } else if (rawSql.includes("'EXPIRED'")) {
            otp.status = 'EXPIRED';
          } else if (rawSql.includes("'EXHAUSTED'")) {
            otp.status = 'EXHAUSTED';
            otp.attemptsLeft = 0;
          } else if (rawSql.includes("'CONSUMED'")) {
            otp.status = 'CONSUMED';
            otp.consumedAt = new Date();
          } else if (rawSql.includes('"attemptsLeft" =')) {
            const numVal = values.find((v: any) => typeof v === 'number');
            if (numVal !== undefined) {
              otp.attemptsLeft = numVal;
            }
          }
          otp.updatedAt = new Date();
        }
      }
    }
    return 1;
  });

  const txMock: any = {
    emailOtp,
    user,
    outboxEvent,
    $queryRaw,
    $executeRaw,
  };

  const $transaction = vi.fn(async (arg: any) => {
    if (typeof arg === 'function') {
      return await arg(txMock);
    }
    if (Array.isArray(arg)) {
      return await Promise.all(arg);
    }
    return arg;
  });

  const mockClient = {
    emailOtp,
    user,
    outboxEvent,
    $queryRaw,
    $executeRaw,
    $transaction,
  };

  return { mockPrisma: mockClient, resetInMemOtps: reset };
});

vi.mock('../src/db/client.js', () => ({
  prisma: mockPrisma,
  checkDatabaseHealth: vi.fn().mockResolvedValue(true),
}));

vi.mock('../src/services/smtpService.js', () => ({
  sendOtpEmail: vi.fn().mockResolvedValue({
    success: true,
    messageId: 'test-smtp-msg-id',
    provider: 'simulated',
  }),
}));

import { prisma } from '../src/db/client.js';

// Mock Firebase Admin SDK for testing
const mockUpdateUser = vi.fn().mockResolvedValue({ uid: 'test-user-123', emailVerified: true });
const mockGetUserByEmail = vi.fn().mockImplementation((email: string) => {
  if (email === 'registered@notas.app') {
    return Promise.resolve({ uid: 'test-user-123', email });
  }
  return Promise.reject(new Error('auth/user-not-found'));
});
const mockRevokeRefreshTokens = vi.fn().mockResolvedValue(undefined);

vi.mock('../src/utils/firebaseAdmin.js', () => ({
  getFirebaseAdmin: () => ({
    auth: () => ({
      updateUser: mockUpdateUser,
      getUserByEmail: mockGetUserByEmail,
      revokeRefreshTokens: mockRevokeRefreshTokens,
    }),
  }),
  verifyFirebaseToken: vi.fn().mockImplementation((token: string) => {
    if (token === 'unverified-token') {
      return Promise.resolve({
        uid: 'test-unverified-uid',
        email: 'unverified@notas.app',
        email_verified: false,
      });
    }
    return Promise.resolve({
      uid: 'test-user-123',
      email: 'registered@notas.app',
      email_verified: true,
    });
  }),
}));

function createMockRequest(options: {
  method: string;
  url: string;
  body?: any;
  headers?: Record<string, string>;
}): VercelRequest {
  const randomIp = `10.0.${Math.floor(Math.random() * 200)}.${Math.floor(Math.random() * 200) + 1}`;
  return {
    method: options.method,
    url: options.url,
    query: { path: options.url.replace(/^\/api\/v1\//, '') },
    headers: {
      'content-type': 'application/json',
      'x-forwarded-for': randomIp,
      authorization: 'Bearer valid-test-token',
      ...(options.headers || {}),
    },
    body: options.body || {},
  } as unknown as VercelRequest;
}

function createMockResponse(): {
  res: VercelResponse;
  statusCode: number;
  data: any;
  headers: Record<string, string>;
} {
  const result = {
    statusCode: 200,
    data: null as any,
    headers: {} as Record<string, string>,
    res: {} as VercelResponse,
  };

  result.res = {
    status(code: number) {
      result.statusCode = code;
      return result.res;
    },
    json(payload: any) {
      result.data = payload;
      return result.res;
    },
    setHeader(name: string, value: string) {
      result.headers[name] = value;
      return result.res;
    },
    end() {
      return result.res;
    },
  } as unknown as VercelResponse;

  return result;
}

describe('OTP Core Service & Concurrency Tests', () => {
  const testEmail = 'otp.test@notas.app';

  beforeEach(async () => {
    resetInMemOtps();
    vi.clearAllMocks();
  });

  it('generates a 6-digit numeric OTP', () => {
    const code = generateNumericOtp();
    expect(code).toMatch(/^\d{6}$/);
    const num = parseInt(code, 10);
    expect(num).toBeGreaterThanOrEqual(100000);
    expect(num).toBeLessThan(1000000);
  });

  it('computes consistent HMAC-SHA256 for case-insensitive email', () => {
    const hash1 = computeOtpHmac('123456', 'USER@notas.app');
    const hash2 = computeOtpHmac('123456', 'user@notas.app');
    expect(hash1).toBe(hash2);
    expect(hash1).toHaveLength(64); // 256 bits = 64 hex chars
  });

  it('enforces 60-second resend cooldown', async () => {
    // First request succeeds
    const first = await requestOtpWithCooldown(testEmail, OtpPurpose.EMAIL_VERIFICATION, 'user-1');
    expect(first.success).toBe(true);

    // Second immediate request throws TooManyRequestsError
    await expect(
      requestOtpWithCooldown(testEmail, OtpPurpose.EMAIL_VERIFICATION, 'user-1')
    ).rejects.toThrow(/Please wait/);
  });

  it('decrements attemptsLeft on invalid code and locks out at 0', async () => {
    const code = '123456';
    const codeHash = computeOtpHmac(code, testEmail);

    const otp = await prisma.emailOtp.create({
      data: {
        email: testEmail,
        purpose: OtpPurpose.EMAIL_VERIFICATION,
        codeHash,
        status: OtpStatus.ACTIVE,
        attemptsLeft: 2,
        expiresAt: new Date(Date.now() + 600000),
        resendAvailableAt: new Date(Date.now() + 60000),
      },
    });

    // 1st invalid attempt
    const res1 = await verifyOtpTransaction(testEmail, OtpPurpose.EMAIL_VERIFICATION, '999999');
    expect(res1.status).toBe('INVALID_CODE');
    if (res1.status === 'INVALID_CODE') {
      expect(res1.attemptsLeft).toBe(1);
    }

    // 2nd invalid attempt -> ATTEMPTS_EXHAUSTED
    const res2 = await verifyOtpTransaction(testEmail, OtpPurpose.EMAIL_VERIFICATION, '888888');
    expect(res2.status).toBe('ATTEMPTS_EXHAUSTED');

    // Check DB state: attemptsLeft = 0, status = EXHAUSTED
    const dbRow = await prisma.emailOtp.findUnique({ where: { id: otp.id } });
    expect(dbRow?.attemptsLeft).toBe(0);
    expect(dbRow?.status).toBe(OtpStatus.EXHAUSTED);
  });

  it('successfully consumes valid code and rejects replay attacks', async () => {
    const code = '654321';
    const codeHash = computeOtpHmac(code, testEmail);

    await prisma.emailOtp.create({
      data: {
        email: testEmail,
        purpose: OtpPurpose.EMAIL_VERIFICATION,
        codeHash,
        status: OtpStatus.ACTIVE,
        attemptsLeft: 5,
        expiresAt: new Date(Date.now() + 600000),
        resendAvailableAt: new Date(Date.now() + 60000),
      },
    });

    // Valid verification
    const res = await verifyOtpTransaction(testEmail, OtpPurpose.EMAIL_VERIFICATION, code);
    expect(res.status).toBe('SUCCESS');

    // Immediate replay attempt with the same valid code must return ALREADY_CONSUMED
    const replay = await verifyOtpTransaction(testEmail, OtpPurpose.EMAIL_VERIFICATION, code);
    expect(replay.status).toBe('ALREADY_CONSUMED');
  });

  it('rejects verification if code is in PENDING_DISPATCH state', async () => {
    const code = '112233';
    const codeHash = computeOtpHmac(code, testEmail);

    await prisma.emailOtp.create({
      data: {
        email: testEmail,
        purpose: OtpPurpose.EMAIL_VERIFICATION,
        codeHash,
        status: OtpStatus.PENDING_DISPATCH, // Not yet ACTIVE
        attemptsLeft: 5,
        expiresAt: new Date(Date.now() + 600000),
        resendAvailableAt: new Date(Date.now() + 60000),
      },
    });

    const res = await verifyOtpTransaction(testEmail, OtpPurpose.EMAIL_VERIFICATION, code);
    expect(res.status).toBe('NOT_ACTIVE');
  });
});

describe('OTP API Routes & Cloud Access Guards', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
  });

  it('POST /api/v1/auth/otp/request-password-reset responds identically for existing and non-existing accounts', async () => {
    // 1. Existing account
    const req1 = createMockRequest({
      method: 'POST',
      url: '/api/v1/auth/otp/request-password-reset',
      body: { email: 'registered@notas.app' },
    });
    const mock1 = createMockResponse();
    await handler(req1, mock1.res);
    expect(mock1.statusCode).toBe(200);
    expect(mock1.data.success).toBe(true);

    // 2. Non-existing account
    const req2 = createMockRequest({
      method: 'POST',
      url: '/api/v1/auth/otp/request-password-reset',
      body: { email: 'nonexistent@notas.app' },
    });
    const mock2 = createMockResponse();
    await handler(req2, mock2.res);
    expect(mock2.statusCode).toBe(200);
    expect(mock2.data.success).toBe(true);

    // Anti-enumeration: Response payloads must be identical
    expect(mock1.data.data.message).toBe(mock2.data.data.message);
  });

  it('guards cloud sync routes: blocks unverified accounts with HTTP 403 EMAIL_NOT_VERIFIED', async () => {
    const req = createMockRequest({
      method: 'POST',
      url: '/api/v1/sync/push',
      body: { categories: [], notes: [], checklistItems: [] },
      headers: { authorization: 'Bearer unverified-token' },
    });
    const mock = createMockResponse();

    await handler(req, mock.res);

    expect(mock.statusCode).toBe(403);
    expect(mock.data.error.code).toBe('EMAIL_NOT_VERIFIED');
  });

  it('guards cloud backup routes: blocks unverified accounts with HTTP 403 EMAIL_NOT_VERIFIED', async () => {
    const req = createMockRequest({
      method: 'GET',
      url: '/api/v1/backups',
      headers: { authorization: 'Bearer unverified-token' },
    });
    const mock = createMockResponse();

    await handler(req, mock.res);

    expect(mock.statusCode).toBe(403);
    expect(mock.data.error.code).toBe('EMAIL_NOT_VERIFIED');
  });
});

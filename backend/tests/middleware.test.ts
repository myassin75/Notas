import { describe, it, expect, vi, beforeEach } from 'vitest';
import { rateLimit } from '../src/middleware/rateLimit.js';
import { authenticate } from '../src/middleware/auth.js';
import { RateLimitError, UnauthorizedError, ForbiddenError, ConflictError } from '../src/errors/AppError.js';
import { verifyFirebaseToken } from '../src/utils/firebaseAdmin.js';
import { prisma } from '../src/db/client.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';
import type { User } from '@prisma/client';
import type * as admin from 'firebase-admin';

function createMockUser(overrides: Partial<User> = {}): User {
  return {
    id: 'test-user-id',
    email: 'test@example.com',
    emailVerified: false,
    emailVerificationStatus: 'PENDING_VERIFICATION',
    displayName: null,
    photoUrl: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    lastSyncAt: null,
    deletionPending: false,
    deletedAt: null,
    ...overrides,
  };
}

function createMockDecodedToken(overrides: Partial<admin.auth.DecodedIdToken> = {}): admin.auth.DecodedIdToken {
  return {
    aud: 'test-project',
    auth_time: 1234567890,
    exp: 1234567890,
    firebase: { identities: {}, sign_in_provider: 'password' },
    iat: 1234567890,
    iss: 'https://securetoken.google.com/test-project',
    sub: 'test-uid',
    uid: 'test-uid',
    ...overrides,
  };
}

const mockGetUser = vi.fn();

vi.mock('../src/utils/firebaseAdmin.js', () => ({
  verifyFirebaseToken: vi.fn(),
  getFirebaseAdmin: vi.fn(() => ({
    auth: () => ({
      getUser: mockGetUser,
    }),
  })),
}));

vi.mock('../src/db/client.js', () => ({
  prisma: {
    user: {
      findUnique: vi.fn(),
      update: vi.fn(),
      create: vi.fn(),
    },
    $executeRaw: vi.fn(),
  },
}));

describe('Middleware', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rate Limiter', () => {
    it('sets rate limit headers on responses', async () => {
      const limiter = rateLimit({ windowMs: 10000, maxRequests: 5, keyPrefix: 'test-limiter' });
      const headers: Record<string, unknown> = {};

      const req = {
        headers: { 'x-forwarded-for': '192.168.1.1' },
      } as unknown as VercelRequest;

      const res = {
        setHeader: (key: string, value: unknown) => {
          headers[key] = value;
        },
      } as unknown as VercelResponse;

      await limiter(req, res);

      expect(headers['X-RateLimit-Limit']).toBe(5);
      expect(headers['X-RateLimit-Remaining']).toBe(4);
      expect(headers['X-RateLimit-Reset']).toBeDefined();
    });

    it('throws RateLimitError when threshold is exceeded', async () => {
      const limiter = rateLimit({ windowMs: 10000, maxRequests: 2, keyPrefix: 'threshold-test' });
      const req = {
        headers: { 'x-forwarded-for': '10.0.0.1' },
      } as unknown as VercelRequest;

      const res = { setHeader: vi.fn() } as unknown as VercelResponse;

      await limiter(req, res); // 1
      await limiter(req, res); // 2
      await expect(limiter(req, res)).rejects.toThrow(RateLimitError); // 3 (exceeded)
    });
  });

  describe('Authentication Middleware', () => {
    it('throws UnauthorizedError if Authorization header is missing', async () => {
      const req = { headers: {} } as VercelRequest;
      const res = {} as VercelResponse;

      await expect(authenticate(req, res)).rejects.toThrow(UnauthorizedError);
    });

    it('throws UnauthorizedError if Authorization header does not start with Bearer', async () => {
      const req = { headers: { authorization: 'Basic xyz' } } as VercelRequest;
      const res = {} as VercelResponse;

      await expect(authenticate(req, res)).rejects.toThrow(UnauthorizedError);
    });

    it('throws UnauthorizedError if token is empty after Bearer prefix', async () => {
      const req = { headers: { authorization: 'Bearer   ' } } as VercelRequest;
      const res = {} as VercelResponse;

      await expect(authenticate(req, res)).rejects.toThrow(UnauthorizedError);
    });

    it('authenticates and updates existing user record when UID matches', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'firebase-uid-1',
          email: 'user@example.com',
          email_verified: true,
          name: 'User One',
          picture: 'https://avatar.com/1.png',
        })
      );

      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'firebase-uid-1',
          email: 'user@example.com',
          emailVerified: true,
          deletionPending: false,
        })
      );

      vi.mocked(prisma.user.update).mockResolvedValueOnce(
        createMockUser({
          id: 'firebase-uid-1',
          email: 'user@example.com',
          emailVerified: true,
          displayName: 'User One',
          photoUrl: 'https://avatar.com/1.png',
          deletionPending: false,
        })
      );

      const user = await authenticate(req, res);

      expect(user.uid).toBe('firebase-uid-1');
      expect(user.email).toBe('user@example.com');
      expect(prisma.user.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'firebase-uid-1' },
          data: expect.objectContaining({ email: 'user@example.com' }),
        })
      );
    });

    it('re-keys existing PostgreSQL user when email matches, email is verified, and old user was deleted in Firebase', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'new-firebase-uid-99',
          email: 'recreated@example.com',
          email_verified: true,
          name: 'Recreated User',
        })
      );

      // Old user in Firebase no longer exists (e.g. deleted and recreated)
      mockGetUser.mockRejectedValueOnce({ code: 'auth/user-not-found' });

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under old UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'old-firebase-uid-11',
          email: 'recreated@example.com',
          deletionPending: false,
        })
      );
      // 3. Raw update succeeds
      vi.mocked(prisma.$executeRaw).mockResolvedValueOnce(1);
      // 4. Return re-keyed record
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'new-firebase-uid-99',
          email: 'recreated@example.com',
          deletionPending: false,
        })
      );

      const user = await authenticate(req, res);

      expect(user.uid).toBe('new-firebase-uid-99');
      expect(prisma.$executeRaw).toHaveBeenCalled();
    });

    it('rejects identity re-keying with ConflictError when incoming email is not verified', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'attacker-uid-66',
          email: 'victim@example.com',
          email_verified: false,
        })
      );

      // 1. Not found by attacker UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under victim UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'victim-uid-11',
          email: 'victim@example.com',
          deletionPending: false,
        })
      );

      await expect(authenticate(req, res)).rejects.toThrow(ConflictError);
      expect(prisma.$executeRaw).not.toHaveBeenCalled();
    });

    it('rejects identity re-keying with ConflictError when old Firebase account is still active under a different identity', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'conflicting-uid-77',
          email: 'legit@example.com',
          email_verified: true,
        })
      );

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under existing UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'existing-uid-88',
          email: 'legit@example.com',
          deletionPending: false,
        })
      );

      // Old user is still active in Firebase!
      mockGetUser.mockResolvedValueOnce({
        uid: 'existing-uid-88',
        email: 'legit@example.com',
      });

      await expect(authenticate(req, res)).rejects.toThrow(ConflictError);
      expect(prisma.$executeRaw).not.toHaveBeenCalled();
    });

    it('rejects authentication with ForbiddenError when existing email record has deletionPending', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'returning-uid-33',
          email: 'pending-del@example.com',
          email_verified: true,
        })
      );

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email but pending deletion
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'old-uid-22',
          email: 'pending-del@example.com',
          deletionPending: true,
        })
      );

      await expect(authenticate(req, res)).rejects.toThrow(ForbiddenError);
    });

    it('creates new user record when neither UID nor email exists in database', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'brand-new-uid',
          email: 'brandnew@example.com',
          email_verified: false,
          name: 'Brand New',
        })
      );

      // Not found by ID, not found by email
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null).mockResolvedValueOnce(null);
      vi.mocked(prisma.user.create).mockResolvedValueOnce(
        createMockUser({
          id: 'brand-new-uid',
          email: 'brandnew@example.com',
          deletionPending: false,
        })
      );

      const user = await authenticate(req, res);

      expect(user.uid).toBe('brand-new-uid');
      expect(prisma.user.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            id: 'brand-new-uid',
            email: 'brandnew@example.com',
          }),
        })
      );
    });

    it('throws ForbiddenError when account has deletionPending and request is not DELETE', async () => {
      const req = {
        method: 'GET',
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue(
        createMockDecodedToken({
          uid: 'deleting-uid',
          email: 'deleting@example.com',
        })
      );

      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(
        createMockUser({
          id: 'deleting-uid',
          email: 'deleting@example.com',
          deletionPending: true,
        })
      );

      vi.mocked(prisma.user.update).mockResolvedValueOnce(
        createMockUser({
          id: 'deleting-uid',
          email: 'deleting@example.com',
          deletionPending: true,
        })
      );

      await expect(authenticate(req, res)).rejects.toThrow(ForbiddenError);
    });
  });
});

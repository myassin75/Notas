import { describe, it, expect, vi, beforeEach } from 'vitest';
import { rateLimit } from '../src/middleware/rateLimit.js';
import { authenticate } from '../src/middleware/auth.js';
import { RateLimitError, UnauthorizedError, ForbiddenError, ConflictError } from '../src/errors/AppError.js';
import { verifyFirebaseToken } from '../src/utils/firebaseAdmin.js';
import { prisma } from '../src/db/client.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';

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

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'firebase-uid-1',
        email: 'user@example.com',
        email_verified: true,
        name: 'User One',
        picture: 'https://avatar.com/1.png',
      } as any);

      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'firebase-uid-1',
        email: 'user@example.com',
        emailVerified: true,
        deletionPending: false,
      } as any);

      vi.mocked(prisma.user.update).mockResolvedValueOnce({
        id: 'firebase-uid-1',
        email: 'user@example.com',
        emailVerified: true,
        displayName: 'User One',
        photoUrl: 'https://avatar.com/1.png',
        deletionPending: false,
      } as any);

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

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'new-firebase-uid-99',
        email: 'recreated@example.com',
        email_verified: true,
        name: 'Recreated User',
      } as any);

      // Old user in Firebase no longer exists (e.g. deleted and recreated)
      mockGetUser.mockRejectedValueOnce({ code: 'auth/user-not-found' });

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under old UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'old-firebase-uid-11',
        email: 'recreated@example.com',
        deletionPending: false,
      } as any);
      // 3. Raw update succeeds
      vi.mocked(prisma.$executeRaw).mockResolvedValueOnce(1 as any);
      // 4. Return re-keyed record
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'new-firebase-uid-99',
        email: 'recreated@example.com',
        deletionPending: false,
      } as any);

      const user = await authenticate(req, res);

      expect(user.uid).toBe('new-firebase-uid-99');
      expect(prisma.$executeRaw).toHaveBeenCalled();
    });

    it('rejects identity re-keying with ConflictError when incoming email is not verified', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'attacker-uid-66',
        email: 'victim@example.com',
        email_verified: false,
      } as any);

      // 1. Not found by attacker UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under victim UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'victim-uid-11',
        email: 'victim@example.com',
        deletionPending: false,
      } as any);

      await expect(authenticate(req, res)).rejects.toThrow(ConflictError);
      expect(prisma.$executeRaw).not.toHaveBeenCalled();
    });

    it('rejects identity re-keying with ConflictError when old Firebase account is still active under a different identity', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'conflicting-uid-77',
        email: 'legit@example.com',
        email_verified: true,
      } as any);

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email under existing UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'existing-uid-88',
        email: 'legit@example.com',
        deletionPending: false,
      } as any);

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

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'returning-uid-33',
        email: 'pending-del@example.com',
        email_verified: true,
      } as any);

      // 1. Not found by new UID
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null);
      // 2. Found by email but pending deletion
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'old-uid-22',
        email: 'pending-del@example.com',
        deletionPending: true,
      } as any);

      await expect(authenticate(req, res)).rejects.toThrow(ForbiddenError);
    });

    it('creates new user record when neither UID nor email exists in database', async () => {
      const req = {
        headers: { authorization: 'Bearer valid-firebase-token' },
      } as unknown as VercelRequest;
      const res = {} as VercelResponse;

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'brand-new-uid',
        email: 'brandnew@example.com',
        email_verified: false,
        name: 'Brand New',
      } as any);

      // Not found by ID, not found by email
      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce(null).mockResolvedValueOnce(null);
      vi.mocked(prisma.user.create).mockResolvedValueOnce({
        id: 'brand-new-uid',
        email: 'brandnew@example.com',
        deletionPending: false,
      } as any);

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

      vi.mocked(verifyFirebaseToken).mockResolvedValue({
        uid: 'deleting-uid',
        email: 'deleting@example.com',
      } as any);

      vi.mocked(prisma.user.findUnique).mockResolvedValueOnce({
        id: 'deleting-uid',
        email: 'deleting@example.com',
        deletionPending: true,
      } as any);

      vi.mocked(prisma.user.update).mockResolvedValueOnce({
        id: 'deleting-uid',
        email: 'deleting@example.com',
        deletionPending: true,
      } as any);

      await expect(authenticate(req, res)).rejects.toThrow(ForbiddenError);
    });
  });
});

import { describe, it, expect, vi } from 'vitest';
import { rateLimit } from '../src/middleware/rateLimit.js';
import { authenticate } from '../src/middleware/auth.js';
import { RateLimitError, UnauthorizedError } from '../src/errors/AppError.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';

describe('Middleware', () => {
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
  });
});

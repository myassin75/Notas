import { describe, it, expect, vi, beforeEach } from 'vitest';
import handler from '../src/api/index.js';
import * as clientModule from '../src/db/client.js';
import type { VercelRequest, VercelResponse } from '@vercel/node';

vi.mock('../src/db/client.js', () => {
  return {
    prisma: {},
    checkDatabaseHealth: vi.fn(),
  };
});

vi.mock('../src/middleware/auth.js', () => {
  return {
    authenticate: vi.fn().mockImplementation((req) => {
      req.user = { uid: 'test-user', email: 'test@example.com', emailVerified: true };
      return req.user;
    }),
  };
});

describe('API Router — Security, CORS, Health & Payload Guard', () => {
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

    res.end = vi.fn(() => {
      res.ended = true;
      return res;
    });

    return res as unknown as VercelResponse & {
      statusCode: number;
      headers: Record<string, string>;
      body: unknown;
      ended: boolean;
    };
  }

  it('handles CORS preflight OPTIONS request with 204 No Content', async () => {
    const req = {
      method: 'OPTIONS',
      headers: { origin: 'https://app.notas.com' },
      query: {},
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(204);
    expect(res.ended).toBe(true);
    expect(res.headers['Access-Control-Allow-Methods']).toContain('GET,OPTIONS,PATCH,DELETE,POST,PUT');
  });

  it('responds with 200 Healthy on public GET /api/v1/health when database is connected', async () => {
    vi.spyOn(clientModule, 'checkDatabaseHealth').mockResolvedValue(true);

    const req = {
      method: 'GET',
      headers: {},
      query: { path: 'health' },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toMatchObject({
      status: 'healthy',
      database: 'connected',
    });
  });

  it('handles public health check when query path contains redundant api/v1/health', async () => {
    vi.spyOn(clientModule, 'checkDatabaseHealth').mockResolvedValue(true);

    const req = {
      method: 'GET',
      headers: {},
      query: { path: 'api/v1/health' },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toMatchObject({
      status: 'healthy',
      database: 'connected',
    });
  });

  it('handles public health check when req.url is /health without query param', async () => {
    vi.spyOn(clientModule, 'checkDatabaseHealth').mockResolvedValue(true);

    const req = {
      method: 'GET',
      headers: {},
      url: '/health',
      query: {},
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toMatchObject({
      status: 'healthy',
      database: 'connected',
    });
  });

  it('responds with 503 Degraded on public GET /api/v1/health when database is down', async () => {
    vi.spyOn(clientModule, 'checkDatabaseHealth').mockResolvedValue(false);

    const req = {
      method: 'GET',
      headers: {},
      query: { path: 'health' },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(503);
    expect(res.body).toMatchObject({
      status: 'degraded',
      database: 'disconnected',
    });
  });

  it('rejects oversized request payloads exceeding 4MB with 400 Bad Request', async () => {
    const req = {
      method: 'POST',
      headers: {
        'content-length': '5000000', // ~5MB
      },
      query: { path: 'sync/push' },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(res.body).toMatchObject({
      success: false,
      error: expect.objectContaining({
        code: 'PAYLOAD_TOO_LARGE',
      }),
    });
  });

  it('returns 404 Not Found for non-existent endpoints', async () => {
    const req = {
      method: 'GET',
      headers: {
        authorization: 'Bearer valid-test-token',
      },
      query: { path: 'non-existent-route' },
    } as unknown as VercelRequest;

    const res = createMockResponse();

    await handler(req, res);

    expect(res.statusCode).toBe(404);
    expect(res.body).toMatchObject({
      success: false,
      error: expect.objectContaining({
        code: 'NOT_FOUND',
      }),
    });
  });
});

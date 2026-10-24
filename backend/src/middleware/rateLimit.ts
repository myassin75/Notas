import type { VercelRequest, VercelResponse } from '@vercel/node';
import { RateLimitError } from '../errors/AppError.js';
import { logger } from '../utils/logger.js';

interface RateLimitRecord {
  count: number;
  resetTime: number;
}

// In-memory fallback store
const inMemoryStore = new Map<string, RateLimitRecord>();

setInterval(() => {
  const now = Date.now();
  for (const [key, record] of inMemoryStore.entries()) {
    if (now > record.resetTime) {
      inMemoryStore.delete(key);
    }
  }
}, 60000).unref();

/**
 * Shared distributed rate limiting via Upstash Redis REST API if configured,
 * otherwise falls back gracefully to in-memory sliding window.
 */
async function checkDistributedRateLimit(
  key: string,
  windowMs: number,
  maxRequests: number
): Promise<{ count: number; remaining: number; resetSeconds: number }> {
  const upstashUrl = process.env.UPSTASH_REDIS_REST_URL;
  const upstashToken = process.env.UPSTASH_REDIS_REST_TOKEN;

  if (upstashUrl && upstashToken) {
    try {
      const windowSeconds = Math.ceil(windowMs / 1000);
      // Atomic INCR + EXPIRE via Upstash pipeline
      const pipelineUrl = `${upstashUrl}/pipeline`;
      const response = await fetch(pipelineUrl, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${upstashToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify([
          ['INCR', key],
          ['EXPIRE', key, windowSeconds, 'NX'],
          ['TTL', key],
        ]),
      });

      if (response.ok) {
        const results = (await response.json()) as Array<{ result: number }>;
        const count = results[0]?.result || 1;
        const ttl = results[2]?.result || windowSeconds;
        const remaining = Math.max(0, maxRequests - count);

        return {
          count,
          remaining,
          resetSeconds: ttl > 0 ? ttl : windowSeconds,
        };
      }
    } catch (error) {
      logger.warn('Distributed rate limit check failed; falling back to in-memory', { error });
    }
  }

  // Fallback to in-memory store
  const now = Date.now();
  let record = inMemoryStore.get(key);

  if (!record || now > record.resetTime) {
    record = {
      count: 1,
      resetTime: now + windowMs,
    };
    inMemoryStore.set(key, record);
  } else {
    record.count += 1;
  }

  const remaining = Math.max(0, maxRequests - record.count);
  const resetSeconds = Math.ceil((record.resetTime - now) / 1000);

  return {
    count: record.count,
    remaining,
    resetSeconds,
  };
}

export function rateLimit(options: {
  windowMs?: number;
  maxRequests?: number;
  keyPrefix?: string;
}) {
  const windowMs = options.windowMs ?? 60 * 1000;
  const maxRequests = options.maxRequests ?? 60;
  const prefix = options.keyPrefix ?? 'global';

  return async (req: VercelRequest, res: VercelResponse): Promise<void> => {
    const user = (req as unknown as { user?: { uid: string } }).user;
    const clientIp =
      (req.headers['x-forwarded-for'] as string)?.split(',')[0]?.trim() ||
      req.socket?.remoteAddress ||
      'unknown-client';

    const identifier = `ratelimit:${prefix}:${user?.uid || clientIp}`;

    const { count, remaining, resetSeconds } = await checkDistributedRateLimit(
      identifier,
      windowMs,
      maxRequests
    );

    res.setHeader('X-RateLimit-Limit', maxRequests);
    res.setHeader('X-RateLimit-Remaining', remaining);
    res.setHeader('X-RateLimit-Reset', resetSeconds);

    if (count > maxRequests) {
      throw new RateLimitError(`Rate limit exceeded. Try again in ${resetSeconds} seconds.`);
    }
  };
}

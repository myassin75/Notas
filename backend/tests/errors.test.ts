import { describe, it, expect } from 'vitest';
import {
  AppError,
  BadRequestError,
  UnauthorizedError,
  ForbiddenError,
  NotFoundError,
  RateLimitError,
  formatErrorResponse,
  HttpCode,
} from '../src/errors/AppError.js';

describe('Error Handling', () => {
  it('instantiates AppError with correct defaults', () => {
    const error = new AppError({ message: 'Custom failure' });
    expect(error.message).toBe('Custom failure');
    expect(error.statusCode).toBe(HttpCode.INTERNAL_SERVER_ERROR);
    expect(error.code).toBe('INTERNAL_SERVER_ERROR');
    expect(error.isOperational).toBe(true);
  });

  it('instantiates specific subclasses with expected HTTP codes', () => {
    const badRequest = new BadRequestError('Bad input', { field: 'name' });
    expect(badRequest.statusCode).toBe(HttpCode.BAD_REQUEST);
    expect(badRequest.code).toBe('BAD_REQUEST');
    expect(badRequest.details).toEqual({ field: 'name' });

    const unauthorized = new UnauthorizedError();
    expect(unauthorized.statusCode).toBe(HttpCode.UNAUTHORIZED);

    const forbidden = new ForbiddenError();
    expect(forbidden.statusCode).toBe(HttpCode.FORBIDDEN);

    const notFound = new NotFoundError();
    expect(notFound.statusCode).toBe(HttpCode.NOT_FOUND);

    const rateLimit = new RateLimitError();
    expect(rateLimit.statusCode).toBe(HttpCode.TOO_MANY_REQUESTS);
  });

  it('formats operational error responses predictably', () => {
    const err = new BadRequestError('Invalid parameter', { id: 'Must be UUID' });
    const formatted = formatErrorResponse(err);

    expect(formatted.statusCode).toBe(400);
    expect(formatted.body.success).toBe(false);
    expect(formatted.body.error.code).toBe('BAD_REQUEST');
    expect(formatted.body.error.message).toBe('Invalid parameter');
    expect(formatted.body.error.details).toEqual({ id: 'Must be UUID' });
    expect(formatted.body.meta.timestamp).toBeDefined();
  });

  it('formats unhandled exceptions safely', () => {
    const standardError = new Error('Database connection timeout');
    const formatted = formatErrorResponse(standardError);

    expect(formatted.statusCode).toBe(500);
    expect(formatted.body.success).toBe(false);
    expect(formatted.body.error.code).toBe('INTERNAL_SERVER_ERROR');
    expect(formatted.body.meta.timestamp).toBeDefined();
  });
});

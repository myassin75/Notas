export enum HttpCode {
  OK = 200,
  CREATED = 201,
  NO_CONTENT = 204,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  CONFLICT = 409,
  UNPROCESSABLE_ENTITY = 422,
  TOO_MANY_REQUESTS = 429,
  INTERNAL_SERVER_ERROR = 500,
  SERVICE_UNAVAILABLE = 503,
}

export class AppError extends Error {
  public readonly statusCode: HttpCode;
  public readonly code: string;
  public readonly details?: unknown;
  public readonly isOperational: boolean;

  constructor(options: {
    message: string;
    statusCode?: HttpCode;
    code?: string;
    details?: unknown;
    isOperational?: boolean;
  }) {
    super(options.message);
    this.name = this.constructor.name;
    this.statusCode = options.statusCode ?? HttpCode.INTERNAL_SERVER_ERROR;
    this.code = options.code ?? 'INTERNAL_SERVER_ERROR';
    this.details = options.details;
    this.isOperational = options.isOperational ?? true;

    Error.captureStackTrace(this, this.constructor);
  }
}

export class BadRequestError extends AppError {
  constructor(message = 'Bad Request', details?: unknown, code = 'BAD_REQUEST') {
    super({ message, statusCode: HttpCode.BAD_REQUEST, code, details });
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'Unauthorized: Missing or invalid authentication token', details?: unknown, code = 'UNAUTHORIZED') {
    super({ message, statusCode: HttpCode.UNAUTHORIZED, code, details });
  }
}

export class ForbiddenError extends AppError {
  constructor(message = 'Forbidden: Access denied to this resource', details?: unknown, code = 'FORBIDDEN') {
    super({ message, statusCode: HttpCode.FORBIDDEN, code, details });
  }
}

export class NotFoundError extends AppError {
  constructor(message = 'Resource not found', details?: unknown, code = 'NOT_FOUND') {
    super({ message, statusCode: HttpCode.NOT_FOUND, code, details });
  }
}

export class ConflictError extends AppError {
  constructor(message = 'Conflict: Resource state conflict', details?: unknown, code = 'CONFLICT') {
    super({ message, statusCode: HttpCode.CONFLICT, code, details });
  }
}

export class RateLimitError extends AppError {
  constructor(message = 'Too many requests. Please slow down.', details?: unknown, code = 'RATE_LIMIT_EXCEEDED') {
    super({ message, statusCode: HttpCode.TOO_MANY_REQUESTS, code, details });
  }
}

export class TooManyRequestsError extends RateLimitError {
  constructor(message = 'Too many requests. Please slow down.', details?: unknown, code = 'TOO_MANY_REQUESTS') {
    super(message, details, code);
  }
}

export class InternalServerError extends AppError {
  constructor(message = 'Internal server error occurred', details?: unknown, code = 'INTERNAL_SERVER_ERROR') {
    super({ message, statusCode: HttpCode.INTERNAL_SERVER_ERROR, code, details, isOperational: false });
  }
}

export function formatErrorResponse(err: unknown) {
  if (err instanceof AppError) {
    return {
      statusCode: err.statusCode,
      body: {
        success: false,
        error: {
          code: err.code,
          message: err.message,
          ...(err.details ? { details: err.details } : {}),
        },
        meta: {
          timestamp: new Date().toISOString(),
        },
      },
    };
  }

  // Unhandled standard Error or unknown rejection
  const message = err instanceof Error ? err.message : 'An unexpected error occurred';
  return {
    statusCode: HttpCode.INTERNAL_SERVER_ERROR,
    body: {
      success: false,
      error: {
        code: 'INTERNAL_SERVER_ERROR',
        message: process.env.NODE_ENV === 'production' ? 'An unexpected internal error occurred' : message,
      },
      meta: {
        timestamp: new Date().toISOString(),
      },
    },
  };
}

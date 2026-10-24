export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogContext {
  userId?: string;
  requestId?: string;
  path?: string;
  method?: string;
  durationMs?: number;
  [key: string]: unknown;
}

class Logger {
  private isProduction = process.env.NODE_ENV === 'production';

  private sanitize(data: unknown): unknown {
    if (!data || typeof data !== 'object') return data;
    if (Array.isArray(data)) return data.map((item) => this.sanitize(item));

    const sanitized: Record<string, unknown> = {};
    const sensitiveKeys = ['authorization', 'password', 'token', 'secret', 'privatekey', 'bearer'];

    for (const [key, value] of Object.entries(data as Record<string, unknown>)) {
      if (sensitiveKeys.some((s) => key.toLowerCase().includes(s))) {
        sanitized[key] = '[REDACTED]';
      } else if (typeof value === 'object' && value !== null) {
        sanitized[key] = this.sanitize(value);
      } else {
        sanitized[key] = value;
      }
    }
    return sanitized;
  }

  private write(level: LogLevel, message: string, context?: LogContext) {
    const entry = {
      level,
      timestamp: new Date().toISOString(),
      message,
      ...(context ? { context: this.sanitize(context) } : {}),
    };

    if (this.isProduction) {
      // In production (Vercel), log JSON for structured log capture
      console[level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'log'](JSON.stringify(entry));
    } else {
      // In development, pretty print
      const prefix = `[${entry.timestamp}] [${level.toUpperCase()}]`;
      const meta = context ? `\n  ${JSON.stringify(this.sanitize(context), null, 2)}` : '';
      console[level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'log'](`${prefix} ${message}${meta}`);
    }
  }

  debug(message: string, context?: LogContext) {
    if (!this.isProduction) {
      this.write('debug', message, context);
    }
  }

  info(message: string, context?: LogContext) {
    this.write('info', message, context);
  }

  warn(message: string, context?: LogContext) {
    this.write('warn', message, context);
  }

  error(message: string, error?: unknown, context?: LogContext) {
    const errorDetails =
      error instanceof Error
        ? {
            name: error.name,
            message: error.message,
            stack: this.isProduction ? undefined : error.stack,
          }
        : error;

    this.write('error', message, {
      ...context,
      error: errorDetails,
    });
  }
}

export const logger = new Logger();

import { prisma } from '../db/client.js';
import { getFirebaseAdmin } from '../utils/firebaseAdmin.js';
import { OutboxStatus, Prisma } from '@prisma/client';
import { logger } from '../utils/logger.js';

export interface OutboxSyncResult {
  processed: number;
  succeeded: number;
  failed: number;
  deadLettered: number;
}

/**
 * Creates a durable OutboxEvent inside an existing or new database transaction.
 */
export async function createOutboxEvent(
  aggregateId: string,
  eventType: string,
  payload: Record<string, any>,
  tx?: Prisma.TransactionClient
): Promise<void> {
  const client = tx || prisma;

  await client.outboxEvent.create({
    data: {
      aggregateId,
      eventType,
      payload,
      status: OutboxStatus.PENDING,
      attempts: 0,
      maxAttempts: 5,
      nextRetryAt: new Date(),
    },
  });
}

/**
 * Idempotently processes pending OutboxEvents with exponential backoff and dead-letter handling.
 */
export async function processOutboxQueue(batchSize = 20): Promise<OutboxSyncResult> {
  const result: OutboxSyncResult = {
    processed: 0,
    succeeded: 0,
    failed: 0,
    deadLettered: 0,
  };

  const adminApp = getFirebaseAdmin();

  // 1. Fetch pending events ready for retry with row lock
  const events = await prisma.$queryRaw<Array<{
    id: string;
    aggregateId: string;
    eventType: string;
    payload: any;
    attempts: number;
    maxAttempts: number;
  }>>`
    SELECT id, "aggregateId", "eventType", payload, attempts, "maxAttempts"
    FROM "outbox_events"
    WHERE status = 'PENDING'::"OutboxStatus"
      AND "nextRetryAt" <= NOW()
    ORDER BY "createdAt" ASC
    LIMIT ${batchSize}
    FOR UPDATE SKIP LOCKED;
  `;

  if (events.length === 0) {
    return result;
  }

  result.processed = events.length;

  for (const event of events) {
    try {
      if (event.eventType === 'SYNC_FIREBASE_EMAIL_VERIFIED') {
        // Idempotently update Firebase emailVerified claim
        await adminApp.auth().updateUser(event.aggregateId, { emailVerified: true });

        // Update database user status and complete outbox event
        await prisma.$transaction([
          prisma.user.update({
            where: { id: event.aggregateId },
            data: {
              emailVerificationStatus: 'VERIFIED',
              emailVerified: true,
            },
          }),
          prisma.outboxEvent.update({
            where: { id: event.id },
            data: {
              status: OutboxStatus.COMPLETED,
              updatedAt: new Date(),
            },
          }),
        ]);

        result.succeeded++;
        logger.info('Outbox claim sync completed successfully', {
          eventId: event.id,
          userId: event.aggregateId,
        });
      } else {
        logger.warn('Unknown outbox event type encountered', { eventType: event.eventType });
        await prisma.outboxEvent.update({
          where: { id: event.id },
          data: { status: OutboxStatus.COMPLETED },
        });
        result.succeeded++;
      }
    } catch (error: any) {
      result.failed++;
      const nextAttempts = event.attempts + 1;

      if (nextAttempts >= event.maxAttempts) {
        // Transition to DEAD_LETTER after 5 failed attempts
        await prisma.outboxEvent.update({
          where: { id: event.id },
          data: {
            status: OutboxStatus.DEAD_LETTER,
            attempts: nextAttempts,
            lastError: error?.message || 'Exceeded max retry attempts',
            updatedAt: new Date(),
          },
        });
        result.deadLettered++;

        logger.error('[OUTBOX_DEAD_LETTER] Event permanently failed and moved to dead-letter queue', {
          eventId: event.id,
          aggregateId: event.aggregateId,
          attempts: nextAttempts,
          error: error?.message,
        });
      } else {
        // Exponential backoff: 5s, 10s, 20s, 40s... + jitter
        const backoffSeconds = Math.min(1800, 5 * Math.pow(2, nextAttempts)) + Math.random() * 2;
        const nextRetryAt = new Date(Date.now() + backoffSeconds * 1000);

        await prisma.outboxEvent.update({
          where: { id: event.id },
          data: {
            attempts: nextAttempts,
            nextRetryAt,
            lastError: error?.message || 'Retry attempt failed',
            updatedAt: new Date(),
          },
        });

        logger.warn('Outbox event failed; scheduled for retry', {
          eventId: event.id,
          attempts: nextAttempts,
          nextRetryAt,
          error: error?.message,
        });
      }
    }
  }

  return result;
}


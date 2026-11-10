import { prisma } from '../db/client.js';
import type { SyncPushInput } from '../validation/syncSchemas.js';
import type {
  CategorySyncDto,
  NoteSyncDto,
  ChecklistItemSyncDto,
  AttachmentSyncDto,
  SyncPushResponseDto,
  SyncChangesResponseDto,
} from '../types/index.js';
import type { Prisma } from '@prisma/client';

// -----------------------------------------------------------------------------------------
// Revision sequencing (audit AUD-05)
//
// `serverRevision` doubles as the pull cursor: `getSyncChanges` returns rows with
// `serverRevision > sinceRevision`, so two DIFFERENT records may never share the same
// revision value — otherwise pulling a batch that advanced the cursor to that shared
// value would silently hide every other record stamped with it.
//
// A plain `Date.now()` is only unique per millisecond and was shared by every record of
// a push. We keep wall time as the base but:
//   1. multiplex it into sub-ticks,
//   2. enforce strict per-process monotonicity via `lastIssuedRevision`, so a busy
//      burst simply keeps incrementing past the wall clock instead of colliding.
// Note: cross-process (Vercel lambda instance) collisions remain theoretically possible;
// the definitive fix is a database sequence — tracked as a follow-up in the audit report.
// -----------------------------------------------------------------------------------------
const SUB_TICKS_PER_MILLISECOND = 1_000n;
let lastIssuedRevision = 0n;

function nextRevision(): bigint {
  const nowTicks = BigInt(Date.now()) * SUB_TICKS_PER_MILLISECOND;
  // Strictly increasing per process, never equal to any revision issued before.
  lastIssuedRevision = nowTicks > lastIssuedRevision ? nowTicks : lastIssuedRevision + 1n;
  return lastIssuedRevision;
}

/** Allocates a fresh revision for each applied record (never reused inside a push). */
function nextRecordRevision(): bigint {
  lastIssuedRevision += 1n;
  return lastIssuedRevision;
}

export async function pushSyncChanges(
  userId: string,
  input: SyncPushInput
): Promise<SyncPushResponseDto> {
  const currentRevision = nextRevision();
  const conflicts: SyncPushResponseDto['conflicts'] = [];
  let appliedCategories = 0;
  let appliedNotes = 0;
  let appliedSettings = 0;

  // Category IDs successfully applied in this same batch. Audit AUD-03 previously trusted
  // mere membership in the batch, which let a note link to a category that had just been
  // rejected as cross-user/stale.
  const validatedCategoryIds = new Set<string>();

  // Transaction bounds (audit AUD-07): an interactive transaction defaults to a 5s timeout,
  // but a full push can legitimately run 500 categories + 500 notes + nested records of
  // sequential round-trips. Raise the pool-wait and interactive timeout so legal max-size
  // pushes do not die with P2028.
  const TRANSACTION_OPTIONS = { timeout: 45_000, maxWait: 10_000 };

  await prisma.$transaction(async (tx) => {
    // --------------------------------------------------------------------------------------------
    // 1. Process Categories
    // --------------------------------------------------------------------------------------------
    if (input.categories && input.categories.length > 0) {
      for (const cat of input.categories) {
        const existing = await tx.category.findUnique({
          where: { id: cat.id },
        });

        // Cross-user ownership check
        if (existing && existing.userId !== userId) {
          conflicts.push({
            entityType: 'category',
            id: cat.id,
            serverRevision: existing.serverRevision.toString(),
            reason: 'Cross-user ownership conflict: Category belongs to another user',
          });
          continue;
        }

        // Stale update check
        if (existing) {
          const clientTime = new Date(cat.clientUpdatedAt).getTime();
          const serverTime = existing.clientUpdatedAt.getTime();

          if (clientTime < serverTime) {
            conflicts.push({
              entityType: 'category',
              id: cat.id,
              serverRevision: existing.serverRevision.toString(),
              reason: 'Stale update: Server has newer category modification',
            });
            continue;
          }
        }

        const isDel = Boolean(cat.isDeleted);
        await tx.category.upsert({
          where: { id: cat.id },
          update: {
            name: cat.name,
            normalizedName: cat.normalizedName,
            colorHex: cat.colorHex ?? null,
            clientCreatedAt: new Date(cat.clientCreatedAt),
            clientUpdatedAt: new Date(cat.clientUpdatedAt),
            isDeleted: isDel,
            deletedAt: isDel ? new Date() : null,
            serverRevision: nextRecordRevision(),
          },
          create: {
            id: cat.id,
            userId,
            name: cat.name,
            normalizedName: cat.normalizedName,
            colorHex: cat.colorHex ?? null,
            clientCreatedAt: new Date(cat.clientCreatedAt),
            clientUpdatedAt: new Date(cat.clientUpdatedAt),
            isDeleted: isDel,
            deletedAt: isDel ? new Date() : null,
            serverRevision: nextRecordRevision(),
          },
        });
        validatedCategoryIds.add(cat.id);
        appliedCategories++;
      }
    }

    // --------------------------------------------------------------------------------------------
    // 2. Process Notes (and nested checklist items / attachments)
    // --------------------------------------------------------------------------------------------
    if (input.notes && input.notes.length > 0) {
      for (const note of input.notes) {
        const existing = await tx.note.findUnique({
          where: { id: note.id },
        });

        // Cross-user ownership check
        if (existing && existing.userId !== userId) {
          conflicts.push({
            entityType: 'note',
            id: note.id,
            serverRevision: existing.serverRevision.toString(),
            reason: 'Cross-user ownership conflict: Note belongs to another user',
          });
          continue;
        }

        // Validate category relationship
        if (note.categoryId) {
          // Only skip the DB lookup when the category was actually APPLIED by this user in
          // this same batch (audit AUD-03). Conflicted/stale categories must fall through to
          // the ownership check below.
          const isCategoryValidatedInBatch = validatedCategoryIds.has(note.categoryId);
          if (!isCategoryValidatedInBatch) {
            const validCategory = await tx.category.findUnique({
              where: { id: note.categoryId },
            });

            if (!validCategory || validCategory.userId !== userId) {
              conflicts.push({
                entityType: 'note',
                id: note.id,
                serverRevision: existing ? existing.serverRevision.toString() : '0',
                reason: `Invalid category reference: Category ${note.categoryId} does not exist or belongs to another user`,
              });
              continue;
            }
          }
        }

        // Stale update check
        if (existing) {
          const clientTime = new Date(note.clientUpdatedAt).getTime();
          const serverTime = existing.clientUpdatedAt.getTime();

          if (clientTime < serverTime) {
            conflicts.push({
              entityType: 'note',
              id: note.id,
              serverRevision: existing.serverRevision.toString(),
              reason: 'Stale update: Server has newer note modification',
            });
            continue;
          }
        }

        const isDel = Boolean(note.isDeleted);
        await tx.note.upsert({
          where: { id: note.id },
          update: {
            categoryId: note.categoryId ?? null,
            kind: note.kind,
            title: note.title,
            body: note.body,
            colorIndex: note.colorIndex,
            importance: note.importance,
            visibility: note.visibility,
            locked: note.locked,
            iconKey: note.iconKey ?? null,
            pictureUri: note.pictureUri ?? null,
            pictureHeroFraming: note.pictureHeroFraming ?? null,
            reminderAt: note.reminderAt ? new Date(note.reminderAt) : null,
            endAt: note.endAt ? new Date(note.endAt) : null,
            isAllDay: note.isAllDay,
            reminders: (note.reminders as Prisma.InputJsonValue) ?? undefined,
            recurrence: (note.recurrence as Prisma.InputJsonValue) ?? undefined,
            actions: (note.actions as Prisma.InputJsonValue) ?? undefined,
            pinnedAt: note.pinnedAt ? new Date(note.pinnedAt) : null,
            completedAt: note.completedAt ? new Date(note.completedAt) : null,
            archived: note.archived,
            trashed: note.trashed,
            trashedAt: note.trashedAt ? new Date(note.trashedAt) : null,
            clientCreatedAt: new Date(note.clientCreatedAt),
            clientUpdatedAt: new Date(note.clientUpdatedAt),
            isDeleted: isDel,
            deletedAt: isDel ? new Date() : null,
            serverRevision: nextRecordRevision(),
          },
          create: {
            id: note.id,
            userId,
            categoryId: note.categoryId ?? null,
            kind: note.kind,
            title: note.title,
            body: note.body,
            colorIndex: note.colorIndex,
            importance: note.importance,
            visibility: note.visibility,
            locked: note.locked,
            iconKey: note.iconKey ?? null,
            pictureUri: note.pictureUri ?? null,
            pictureHeroFraming: note.pictureHeroFraming ?? null,
            reminderAt: note.reminderAt ? new Date(note.reminderAt) : null,
            endAt: note.endAt ? new Date(note.endAt) : null,
            isAllDay: note.isAllDay,
            reminders: (note.reminders as Prisma.InputJsonValue) ?? undefined,
            recurrence: (note.recurrence as Prisma.InputJsonValue) ?? undefined,
            actions: (note.actions as Prisma.InputJsonValue) ?? undefined,
            pinnedAt: note.pinnedAt ? new Date(note.pinnedAt) : null,
            completedAt: note.completedAt ? new Date(note.completedAt) : null,
            archived: note.archived,
            trashed: note.trashed,
            trashedAt: note.trashedAt ? new Date(note.trashedAt) : null,
            clientCreatedAt: new Date(note.clientCreatedAt),
            clientUpdatedAt: new Date(note.clientUpdatedAt),
            isDeleted: isDel,
            deletedAt: isDel ? new Date() : null,
            serverRevision: currentRevision,
          },
        });

        // Nested Checklist Items (audit AUD-02: upserts must never touch another user's row)
        if (note.checklistItems && note.checklistItems.length > 0) {
          for (const item of note.checklistItems) {
            const itemExisting = await tx.checklistItem.findUnique({
              where: { id: item.id },
            });

            const itemDel = Boolean(item.isDeleted);
            if (itemExisting && itemExisting.userId !== userId) {
              conflicts.push({
                entityType: 'note',
                id: note.id,
                serverRevision: itemExisting.serverRevision.toString(),
                reason: `Cross-user ownership conflict: Checklist item ${item.id} belongs to another user`,
              });
              continue;
            }

            await tx.checklistItem.upsert({
              where: { id: item.id },
              update: {
                text: item.text,
                checked: item.checked,
                sortOrder: item.sortOrder,
                parentId: item.parentId ?? null,
                depth: item.depth ?? 0,
                details: item.details ?? '',
                isDeleted: itemDel,
                deletedAt: itemDel ? new Date() : null,
                serverRevision: nextRecordRevision(),
              },
              create: {
                id: item.id,
                noteId: note.id,
                userId,
                text: item.text,
                checked: item.checked,
                sortOrder: item.sortOrder,
                parentId: item.parentId ?? null,
                depth: item.depth ?? 0,
                details: item.details ?? '',
                isDeleted: itemDel,
                deletedAt: itemDel ? new Date() : null,
                serverRevision: nextRecordRevision(),
              },
            });
          }
        }

        // Nested Attachments (audit AUD-02: upserts must never touch another user's row)
        if (note.attachments && note.attachments.length > 0) {
          for (const att of note.attachments) {
            const attExisting = await tx.noteAttachment.findUnique({
              where: { id: att.id },
            });

            const attDel = Boolean(att.isDeleted);
            if (attExisting && attExisting.userId !== userId) {
              conflicts.push({
                entityType: 'note',
                id: note.id,
                serverRevision: attExisting.serverRevision.toString(),
                reason: `Cross-user ownership conflict: Attachment ${att.id} belongs to another user`,
              });
              continue;
            }

            await tx.noteAttachment.upsert({
              where: { id: att.id },
              update: {
                uri: att.uri,
                displayName: att.displayName,
                mimeType: att.mimeType ?? null,
                sizeBytes: att.sizeBytes ? BigInt(att.sizeBytes) : null,
                storageKey: att.storageKey ?? null,
                isDeleted: attDel,
                deletedAt: attDel ? new Date() : null,
                serverRevision: nextRecordRevision(),
              },
              create: {
                id: att.id,
                noteId: note.id,
                userId,
                uri: att.uri,
                displayName: att.displayName,
                mimeType: att.mimeType ?? null,
                sizeBytes: att.sizeBytes ? BigInt(att.sizeBytes) : null,
                storageKey: att.storageKey ?? null,
                isDeleted: attDel,
                deletedAt: attDel ? new Date() : null,
                serverRevision: nextRecordRevision(),
              },
            });
          }
        }

        appliedNotes++;
      }
    }

    // --------------------------------------------------------------------------------------------
    // 3. Process User Settings
    // --------------------------------------------------------------------------------------------
    if (input.settings) {
      await tx.userSettings.upsert({
        where: { userId },
        update: {
          themeSettings: (input.settings.themeSettings as Prisma.InputJsonValue) ?? undefined,
          reminderSettings: (input.settings.reminderSettings as Prisma.InputJsonValue) ?? undefined,
          defaultNoteSettings: (input.settings.defaultNoteSettings as Prisma.InputJsonValue) ?? undefined,
          interactionSettings: (input.settings.interactionSettings as Prisma.InputJsonValue) ?? undefined,
          viewOptionsSettings: (input.settings.viewOptionsSettings as Prisma.InputJsonValue) ?? undefined,
          clientUpdatedAt: new Date(input.settings.clientUpdatedAt),
          serverRevision: nextRecordRevision(),
        },
        create: {
          userId,
          themeSettings: (input.settings.themeSettings as Prisma.InputJsonValue) ?? undefined,
          reminderSettings: (input.settings.reminderSettings as Prisma.InputJsonValue) ?? undefined,
          defaultNoteSettings: (input.settings.defaultNoteSettings as Prisma.InputJsonValue) ?? undefined,
          interactionSettings: (input.settings.interactionSettings as Prisma.InputJsonValue) ?? undefined,
          viewOptionsSettings: (input.settings.viewOptionsSettings as Prisma.InputJsonValue) ?? undefined,
          clientUpdatedAt: new Date(input.settings.clientUpdatedAt),
          serverRevision: nextRecordRevision(),
        },
      });
      appliedSettings++;
    }

    // --------------------------------------------------------------------------------------------
    // 4. Update Device Cursor & User Last Sync
    // --------------------------------------------------------------------------------------------
    await tx.deviceSyncCursor.upsert({
      where: {
        userId_deviceId: {
          userId,
          deviceId: input.deviceId,
        },
      },
      update: {
        deviceName: input.deviceName ?? undefined,
        appVersion: input.appVersion ?? undefined,
        lastSyncedRevision: currentRevision,
        lastSyncedAt: new Date(),
      },
      create: {
        userId,
        deviceId: input.deviceId,
        deviceName: input.deviceName,
        appVersion: input.appVersion,
        lastSyncedRevision: currentRevision,
        lastSyncedAt: new Date(),
      },
    });

    await tx.user.update({
      where: { id: userId },
      data: { lastSyncAt: new Date() },
    });
  }, TRANSACTION_OPTIONS);

  return {
    serverRevision: currentRevision.toString(),
    appliedCount: {
      categories: appliedCategories,
      notes: appliedNotes,
      settings: appliedSettings,
    },
    conflicts,
  };
}

export async function getSyncChanges(
  userId: string,
  sinceRevision: bigint,
  limit: number
): Promise<SyncChangesResponseDto> {
  const [categories, notes, settings] = await Promise.all([
    prisma.category.findMany({
      where: {
        userId,
        serverRevision: { gt: sinceRevision },
      },
      orderBy: { serverRevision: 'asc' },
      take: limit,
    }),
    prisma.note.findMany({
      where: {
        userId,
        serverRevision: { gt: sinceRevision },
      },
      include: {
        checklistItems: true,
        attachments: true,
      },
      orderBy: { serverRevision: 'asc' },
      take: limit,
    }),
    prisma.userSettings.findFirst({
      where: {
        userId,
        serverRevision: { gt: sinceRevision },
      },
    }),
  ]);

  // Watermark safety (audit AUD-06): the cursor must never advance past a model whose
  // result set was truncated, or rows between that model's truncation point and the
  // global maximum revision would be silently skipped. Only advance to the minimum
  // max-revision among truncated models; fully-returned models impose no cap.
  // Ties across models are also avoided since every record now gets a unique revision.
  const categoriesTruncated = categories.length >= limit;
  const notesTruncated = notes.length >= limit;

  let maxRevision = sinceRevision;
  for (const c of categories) {
    if (c.serverRevision > maxRevision) maxRevision = c.serverRevision;
  }
  for (const n of notes) {
    if (n.serverRevision > maxRevision) maxRevision = n.serverRevision;
  }
  if (settings && settings.serverRevision > maxRevision) {
    maxRevision = settings.serverRevision;
  }

  const hasMore = categoriesTruncated || notesTruncated;
  if (hasMore) {
    // Advance only up to the lowest truncation ceiling; anything above it is pulled next page.
    const safeCeilings: bigint[] = [];
    if (categoriesTruncated && categories.length > 0) {
      safeCeilings.push(categories[categories.length - 1].serverRevision);
    }
    if (notesTruncated && notes.length > 0) {
      safeCeilings.push(notes[notes.length - 1].serverRevision);
    }
    if (safeCeilings.length > 0) {
      maxRevision = safeCeilings.reduce((min, v) => (v < min ? v : min));
    } else {
      // Truncation detected on an empty result set (possible only if limit <= 0, which the
      // schema forbids) — fall back to the global watermark but keep hasMore as computed.
      maxRevision = sinceRevision;
    }
  }

  const mappedCategories: CategorySyncDto[] = categories.map((c) => ({
    id: c.id,
    name: c.name,
    normalizedName: c.normalizedName,
    colorHex: c.colorHex,
    clientCreatedAt: c.clientCreatedAt.toISOString(),
    clientUpdatedAt: c.clientUpdatedAt.toISOString(),
    isDeleted: c.isDeleted,
  }));

  const mappedNotes: NoteSyncDto[] = notes.map((n) => ({
    id: n.id,
    categoryId: n.categoryId,
    kind: n.kind,
    title: n.title,
    body: n.body,
    colorIndex: n.colorIndex,
    importance: n.importance,
    visibility: n.visibility,
    locked: n.locked,
    iconKey: n.iconKey,
    pictureUri: n.pictureUri,
    pictureHeroFraming: n.pictureHeroFraming,
    reminderAt: n.reminderAt?.toISOString() ?? null,
    endAt: n.endAt?.toISOString() ?? null,
    isAllDay: n.isAllDay,
    reminders: n.reminders ? (n.reminders as unknown[]) : undefined,
    recurrence: n.recurrence ? (n.recurrence as Record<string, unknown>) : null,
    actions: n.actions ? (n.actions as unknown[]) : undefined,
    pinnedAt: n.pinnedAt?.toISOString() ?? null,
    completedAt: n.completedAt?.toISOString() ?? null,
    archived: n.archived,
    trashed: n.trashed,
    trashedAt: n.trashedAt?.toISOString() ?? null,
    clientCreatedAt: n.clientCreatedAt.toISOString(),
    clientUpdatedAt: n.clientUpdatedAt.toISOString(),
    isDeleted: n.isDeleted,
    checklistItems: n.checklistItems.map(
      (item): ChecklistItemSyncDto => ({
        id: item.id,
        text: item.text,
        checked: item.checked,
        sortOrder: item.sortOrder,
        parentId: item.parentId,
        depth: item.depth,
        details: item.details,
        isDeleted: item.isDeleted,
      })
    ),
    attachments: n.attachments.map(
      (att): AttachmentSyncDto => ({
        id: att.id,
        uri: att.uri,
        displayName: att.displayName,
        mimeType: att.mimeType,
        sizeBytes: att.sizeBytes ? att.sizeBytes.toString() : null,
        storageKey: att.storageKey,
        isDeleted: att.isDeleted,
      })
    ),
  }));

  return {
    currentServerRevision: maxRevision.toString(),
    categories: mappedCategories,
    notes: mappedNotes,
    settings: settings
      ? {
          themeSettings: settings.themeSettings as Record<string, unknown> | null,
          reminderSettings: settings.reminderSettings as Record<string, unknown> | null,
          defaultNoteSettings: settings.defaultNoteSettings as Record<string, unknown> | null,
          interactionSettings: settings.interactionSettings as Record<string, unknown> | null,
          viewOptionsSettings: settings.viewOptionsSettings as Record<string, unknown> | null,
          clientUpdatedAt: settings.clientUpdatedAt.toISOString(),
        }
      : null,
    hasMore,
  };
}

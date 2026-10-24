import { prisma } from '../db/client.js';
import { NotFoundError, InternalServerError } from '../errors/AppError.js';
import { getFirebaseAdmin } from '../utils/firebaseAdmin.js';
import { deleteAllUserFiles } from './storageService.js';
import { logger } from '../utils/logger.js';
import type { UserProfileDto, UserStatsDto, UpdateProfileRequestDto } from '../types/index.js';

export async function getUserProfile(userId: string): Promise<UserProfileDto> {
  const user = await prisma.user.findUnique({
    where: { id: userId },
  });

  if (!user) {
    throw new NotFoundError('User profile not found in database');
  }

  return {
    id: user.id,
    email: user.email,
    emailVerified: user.emailVerified,
    displayName: user.displayName,
    photoUrl: user.photoUrl,
    createdAt: user.createdAt.toISOString(),
    updatedAt: user.updatedAt.toISOString(),
    lastSyncAt: user.lastSyncAt ? user.lastSyncAt.toISOString() : null,
  };
}

export async function updateUserProfile(
  userId: string,
  data: UpdateProfileRequestDto
): Promise<UserProfileDto> {
  const updatedUser = await prisma.user.update({
    where: { id: userId },
    data: {
      displayName: data.displayName !== undefined ? data.displayName : undefined,
      photoUrl: data.photoUrl !== undefined ? data.photoUrl : undefined,
    },
  });

  return {
    id: updatedUser.id,
    email: updatedUser.email,
    emailVerified: updatedUser.emailVerified,
    displayName: updatedUser.displayName,
    photoUrl: updatedUser.photoUrl,
    createdAt: updatedUser.createdAt.toISOString(),
    updatedAt: updatedUser.updatedAt.toISOString(),
    lastSyncAt: updatedUser.lastSyncAt ? updatedUser.lastSyncAt.toISOString() : null,
  };
}

export async function getUserStats(userId: string): Promise<UserStatsDto> {
  const [notesCount, tasksCount, eventsCount, categoriesCount, backupsCount, lastBackup, user] =
    await Promise.all([
      prisma.note.count({
        where: { userId, isDeleted: false, kind: 'NOTE' },
      }),
      prisma.note.count({
        where: { userId, isDeleted: false, kind: 'LIST' },
      }),
      prisma.note.count({
        where: { userId, isDeleted: false, endAt: { not: null } },
      }),
      prisma.category.count({
        where: { userId, isDeleted: false },
      }),
      prisma.backup.count({
        where: { userId },
      }),
      prisma.backup.findFirst({
        where: { userId },
        orderBy: { createdAt: 'desc' },
      }),
      prisma.user.findUnique({
        where: { id: userId },
        select: { lastSyncAt: true },
      }),
    ]);

  return {
    notesCount,
    tasksCount,
    eventsCount,
    categoriesCount,
    backupsCount,
    lastBackupAt: lastBackup?.createdAt.toISOString() ?? null,
    lastSyncAt: user?.lastSyncAt?.toISOString() ?? null,
  };
}

/**
 * Executes a resilient, recoverable account deletion workflow.
 * 1. Mark user deletionPending: true in PostgreSQL.
 * 2. Delete all S3 storage backup files.
 * 3. Delete Firebase Authentication user record.
 * 4. Permanently delete PostgreSQL user record and all cascaded relations.
 */
export async function deleteUserAccount(userId: string): Promise<void> {
  logger.info('Initiating resilient account deletion workflow', { userId });

  // 1. Mark deletion in progress
  await prisma.user.update({
    where: { id: userId },
    data: {
      deletionPending: true,
      deletedAt: new Date(),
    },
  });

  // 2. Delete all private backup objects from S3
  try {
    await deleteAllUserFiles(userId);
  } catch (error) {
    logger.error('Failed to delete user S3 backup files during account deletion', error, { userId });
    throw new InternalServerError('Failed to purge cloud storage backups. Please retry account deletion.');
  }

  // 3. Delete Firebase Authentication user
  try {
    const adminApp = getFirebaseAdmin();
    await adminApp.auth().deleteUser(userId);
    logger.info('Deleted Firebase Authentication user record', { userId });
  } catch (error: unknown) {
    const code = (error as { code?: string })?.code;
    // If user is already deleted in Firebase, continue
    if (code !== 'auth/user-not-found') {
      logger.error('Failed to delete Firebase Authentication user record', error, { userId });
      throw new InternalServerError(
        'Failed to delete user identity from authentication provider. Please retry account deletion.'
      );
    }
  }

  // 4. Permanently cascade-delete all PostgreSQL rows
  try {
    await prisma.user.delete({
      where: { id: userId },
    });
    logger.info('Successfully purged PostgreSQL user data and completed account deletion', { userId });
  } catch (error) {
    logger.error('Failed to delete PostgreSQL user record', error, { userId });
    throw new InternalServerError('Failed to purge user database records. Please retry account deletion.');
  }
}

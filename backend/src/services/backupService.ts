import { randomUUID } from 'node:crypto';
import { prisma } from '../db/client.js';
import { NotFoundError, ConflictError } from '../errors/AppError.js';
import {
  createPresignedUploadUrl,
  createPresignedDownloadUrl,
  verifyStoredObject,
  deleteStorageObject,
} from './storageService.js';
import type { CreateBackupInput } from '../validation/backupSchemas.js';
import type {
  CreateBackupResponseDto,
  BackupItemDto,
  RestoreBackupResponseDto,
  VerifyBackupResponseDto,
} from '../types/index.js';

export async function createBackupRecord(
  userId: string,
  input: CreateBackupInput
): Promise<CreateBackupResponseDto> {
  const expiresInSeconds = 900; // 15 minutes
  const backupId = randomUUID();
  const storageKey = `backups/${userId}/${backupId}.zip`;

  await prisma.backup.create({
    data: {
      id: backupId,
      userId,
      storageKey,
      fileName: input.fileName,
      sizeBytes: BigInt(input.sizeBytes),
      sha256Checksum: input.sha256Checksum.toLowerCase(),
      notesCount: input.notesCount,
      tasksCount: input.tasksCount,
      eventsCount: input.eventsCount,
      categoriesCount: input.categoriesCount,
      schemaVersion: input.schemaVersion,
      isEncrypted: input.isEncrypted,
      status: 'PENDING',
    },
  });

  const uploadUrl = await createPresignedUploadUrl(storageKey, 'application/zip', expiresInSeconds);

  return {
    backupId,
    uploadUrl,
    expiresInSeconds,
  };
}

export async function verifyBackupUpload(
  userId: string,
  backupId: string
): Promise<VerifyBackupResponseDto> {
  const backup = await prisma.backup.findUnique({
    where: { id: backupId },
  });

  if (!backup || backup.userId !== userId) {
    throw new NotFoundError(`Backup with ID ${backupId} not found`);
  }

  if (backup.status === 'COMPLETED') {
    return {
      backupId: backup.id,
      status: 'COMPLETED',
      verifiedAt: backup.verifiedAt ? backup.verifiedAt.toISOString() : backup.updatedAt.toISOString(),
      actualSizeBytes: backup.sizeBytes.toString(),
      message: 'Backup is already verified and marked COMPLETED.',
    };
  }

  // Verify against actual stored S3 object
  const verification = await verifyStoredObject(
    backup.storageKey,
    backup.sha256Checksum,
    backup.sizeBytes
  );

  if (!verification.valid) {
    const errorMsg = verification.error || 'Backup verification failed';
    await prisma.backup.update({
      where: { id: backup.id },
      data: {
        status: 'FAILED',
        verificationError: errorMsg,
      },
    });
    throw new ConflictError(errorMsg);
  }

  const now = new Date();
  const updated = await prisma.backup.update({
    where: { id: backup.id },
    data: {
      status: 'COMPLETED',
      verifiedAt: now,
      verificationError: null,
    },
  });

  return {
    backupId: updated.id,
    status: 'COMPLETED',
    verifiedAt: now.toISOString(),
    actualSizeBytes: updated.sizeBytes.toString(),
    message: 'Backup uploaded and verified successfully.',
  };
}

export async function listUserBackups(userId: string): Promise<BackupItemDto[]> {
  const backups = await prisma.backup.findMany({
    where: { userId },
    orderBy: { createdAt: 'desc' },
  });

  return backups.map((b) => ({
    id: b.id,
    fileName: b.fileName,
    sizeBytes: b.sizeBytes.toString(),
    sha256Checksum: b.sha256Checksum,
    notesCount: b.notesCount,
    tasksCount: b.tasksCount,
    eventsCount: b.eventsCount,
    categoriesCount: b.categoriesCount,
    schemaVersion: b.schemaVersion,
    isEncrypted: b.isEncrypted,
    status: b.status,
    verifiedAt: b.verifiedAt ? b.verifiedAt.toISOString() : null,
    verificationError: b.verificationError,
    createdAt: b.createdAt.toISOString(),
    updatedAt: b.updatedAt.toISOString(),
  }));
}

export async function getUserBackupById(userId: string, backupId: string): Promise<BackupItemDto> {
  const backup = await prisma.backup.findUnique({
    where: { id: backupId },
  });

  if (!backup || backup.userId !== userId) {
    throw new NotFoundError(`Backup with ID ${backupId} not found`);
  }

  return {
    id: backup.id,
    fileName: backup.fileName,
    sizeBytes: backup.sizeBytes.toString(),
    sha256Checksum: backup.sha256Checksum,
    notesCount: backup.notesCount,
    tasksCount: backup.tasksCount,
    eventsCount: backup.eventsCount,
    categoriesCount: backup.categoriesCount,
    schemaVersion: backup.schemaVersion,
    isEncrypted: backup.isEncrypted,
    status: backup.status,
    verifiedAt: backup.verifiedAt ? backup.verifiedAt.toISOString() : null,
    verificationError: backup.verificationError,
    createdAt: backup.createdAt.toISOString(),
    updatedAt: backup.updatedAt.toISOString(),
  };
}

export async function restoreUserBackup(
  userId: string,
  backupId: string
): Promise<RestoreBackupResponseDto> {
  const backup = await prisma.backup.findUnique({
    where: { id: backupId },
  });

  if (!backup || backup.userId !== userId) {
    throw new NotFoundError(`Backup with ID ${backupId} not found`);
  }

  // Prevent restore of missing, incomplete, or invalid backups
  if (backup.status !== 'COMPLETED') {
    throw new ConflictError(
      `Cannot restore backup with status '${backup.status}'. Only COMPLETED and verified backups can be restored.`
    );
  }

  const expiresInSeconds = 900; // 15 minutes
  const downloadUrl = await createPresignedDownloadUrl(backup.storageKey, expiresInSeconds);

  return {
    backupId: backup.id,
    downloadUrl,
    expiresInSeconds,
    sha256Checksum: backup.sha256Checksum,
    sizeBytes: backup.sizeBytes.toString(),
  };
}

export async function cleanupAbandonedBackups(userId: string): Promise<number> {
  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

  const abandoned = await prisma.backup.findMany({
    where: {
      userId,
      status: 'PENDING',
      createdAt: { lt: oneDayAgo },
    },
  });

  for (const b of abandoned) {
    try {
      await deleteStorageObject(b.storageKey);
    } catch {
      // Ignore if S3 object was never uploaded
    }

    await prisma.backup.update({
      where: { id: b.id },
      data: {
        status: 'FAILED',
        verificationError: 'Pending upload abandoned or expired after 24 hours.',
      },
    });
  }

  return abandoned.length;
}

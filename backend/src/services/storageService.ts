import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
  ListObjectsV2Command,
  DeleteObjectsCommand,
  HeadObjectCommand,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { createHash } from 'node:crypto';
import type { Readable } from 'node:stream';
import { logger } from '../utils/logger.js';

let s3ClientInstance: S3Client | null = null;

function getS3Client(): S3Client {
  if (s3ClientInstance) return s3ClientInstance;

  const endpoint = process.env.S3_ENDPOINT || undefined;
  const region = process.env.S3_REGION || 'eu-central-1';
  const accessKeyId = process.env.S3_ACCESS_KEY_ID || 'dummy-access-key';
  const secretAccessKey = process.env.S3_SECRET_ACCESS_KEY || 'dummy-secret-key';
  const forcePathStyle = process.env.S3_FORCE_PATH_STYLE === 'true';

  s3ClientInstance = new S3Client({
    region,
    endpoint,
    credentials: {
      accessKeyId,
      secretAccessKey,
    },
    forcePathStyle,
  });

  return s3ClientInstance;
}

export function getBucketName(): string {
  return process.env.S3_BUCKET_NAME || 'notas-user-backups';
}

export async function createPresignedUploadUrl(
  storageKey: string,
  contentType = 'application/zip',
  expiresInSeconds = 900 // 15 minutes
): Promise<string> {
  const client = getS3Client();
  const bucket = getBucketName();

  const command = new PutObjectCommand({
    Bucket: bucket,
    Key: storageKey,
    ContentType: contentType,
  });

  return await getSignedUrl(client, command, { expiresIn: expiresInSeconds });
}

export async function createPresignedDownloadUrl(
  storageKey: string,
  expiresInSeconds = 900 // 15 minutes
): Promise<string> {
  const client = getS3Client();
  const bucket = getBucketName();

  const command = new GetObjectCommand({
    Bucket: bucket,
    Key: storageKey,
  });

  return await getSignedUrl(client, command, { expiresIn: expiresInSeconds });
}

export async function headStorageObject(storageKey: string) {
  const client = getS3Client();
  const bucket = getBucketName();

  const command = new HeadObjectCommand({
    Bucket: bucket,
    Key: storageKey,
  });

  return await client.send(command);
}

export async function computeS3ObjectSha256(storageKey: string): Promise<{ hashHex: string; bytesRead: number }> {
  const client = getS3Client();
  const bucket = getBucketName();

  const command = new GetObjectCommand({
    Bucket: bucket,
    Key: storageKey,
  });

  const response = await client.send(command);
  const stream = response.Body as Readable;

  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    let bytesRead = 0;

    stream.on('data', (chunk: Buffer) => {
      bytesRead += chunk.length;
      hash.update(chunk);
    });

    stream.on('end', () => {
      resolve({ hashHex: hash.digest('hex'), bytesRead });
    });

    stream.on('error', (err) => {
      reject(err);
    });
  });
}

export async function verifyStoredObject(
  storageKey: string,
  expectedSha256: string,
  expectedSizeBytes: number | bigint
): Promise<{ valid: boolean; actualSizeBytes?: number; error?: string }> {
  try {
    const head = await headStorageObject(storageKey);
    const actualLength = head.ContentLength ?? 0;

    if (BigInt(actualLength) !== BigInt(expectedSizeBytes)) {
      return {
        valid: false,
        actualSizeBytes: actualLength,
        error: `Size mismatch: claimed ${expectedSizeBytes} bytes, but actual object is ${actualLength} bytes`,
      };
    }

    const { hashHex, bytesRead } = await computeS3ObjectSha256(storageKey);
    if (hashHex.toLowerCase() !== expectedSha256.toLowerCase()) {
      return {
        valid: false,
        actualSizeBytes: bytesRead,
        error: `Checksum mismatch: expected SHA256 ${expectedSha256}, but computed ${hashHex}`,
      };
    }

    return {
      valid: true,
      actualSizeBytes: bytesRead,
    };
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Storage object verification failed';
    return {
      valid: false,
      error: `Object not found or verification error: ${message}`,
    };
  }
}

export async function deleteStorageObject(storageKey: string): Promise<void> {
  const client = getS3Client();
  const bucket = getBucketName();

  try {
    const command = new DeleteObjectCommand({
      Bucket: bucket,
      Key: storageKey,
    });
    await client.send(command);
  } catch (error) {
    logger.error('Failed to delete S3 storage object', error, { storageKey });
    throw error;
  }
}

export async function deleteAllUserFiles(userId: string): Promise<void> {
  const client = getS3Client();
  const bucket = getBucketName();
  const prefix = `backups/${userId}/`;

  try {
    const listCommand = new ListObjectsV2Command({
      Bucket: bucket,
      Prefix: prefix,
    });

    const listed = await client.send(listCommand);
    if (!listed.Contents || listed.Contents.length === 0) return;

    const objectsToDelete = listed.Contents.map((item) => ({ Key: item.Key! }));
    const deleteCommand = new DeleteObjectsCommand({
      Bucket: bucket,
      Delete: { Objects: objectsToDelete },
    });

    await client.send(deleteCommand);
    logger.info('Deleted all S3 user backup files', { userId, count: objectsToDelete.length });
  } catch (error) {
    logger.error('Failed to clean up user S3 backups', error, { userId });
    throw error;
  }
}

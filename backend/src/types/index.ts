import type { VercelRequest } from '@vercel/node';

export interface AuthenticatedUser {
  uid: string;
  email: string;
  emailVerified: boolean;
  name?: string;
  picture?: string;
}

export interface AuthenticatedRequest extends VercelRequest {
  user: AuthenticatedUser;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
  meta?: {
    timestamp: string;
    requestId?: string;
  };
}

export interface UserProfileDto {
  id: string;
  email: string;
  emailVerified: boolean;
  displayName: string | null;
  photoUrl: string | null;
  createdAt: string;
  updatedAt: string;
  lastSyncAt: string | null;
}

export interface UserStatsDto {
  notesCount: number;
  tasksCount: number;
  eventsCount: number;
  categoriesCount: number;
  backupsCount: number;
  lastBackupAt: string | null;
  lastSyncAt: string | null;
}

export interface UpdateProfileRequestDto {
  displayName?: string | null;
  photoUrl?: string | null;
}

// ----------------------------------------------------------------------------------------------
// Sync DTOs
// ----------------------------------------------------------------------------------------------

export interface CategorySyncDto {
  id: string; // UUID
  name: string;
  normalizedName: string;
  colorHex?: string | null;
  clientCreatedAt: string;
  clientUpdatedAt: string;
  isDeleted?: boolean;
}

export interface ChecklistItemSyncDto {
  id: string; // UUID
  text: string;
  checked: boolean;
  sortOrder: number;
  parentId?: string | null;
  depth?: number;
  details?: string;
  isDeleted?: boolean;
}

export interface AttachmentSyncDto {
  id: string; // UUID
  uri: string;
  displayName: string;
  mimeType?: string | null;
  sizeBytes?: number | string | null;
  storageKey?: string | null;
  isDeleted?: boolean;
}

export interface NoteSyncDto {
  id: string; // UUID
  categoryId?: string | null;
  kind: 'NOTE' | 'LIST';
  title: string;
  body: string;
  colorIndex: number;
  importance: 'LOW' | 'DEFAULT' | 'HIGH' | 'CRITICAL';
  visibility: 'DEFAULT' | 'PRIVATE' | 'SECRET';
  locked: boolean;
  iconKey?: string | null;
  pictureUri?: string | null;
  pictureHeroFraming?: string | null;
  reminderAt?: string | null;
  endAt?: string | null;
  isAllDay: boolean;
  reminders?: unknown[];
  recurrence?: Record<string, unknown> | null;
  actions?: unknown[];
  pinnedAt?: string | null;
  completedAt?: string | null;
  archived: boolean;
  trashed: boolean;
  trashedAt?: string | null;
  clientCreatedAt: string;
  clientUpdatedAt: string;
  isDeleted?: boolean;
  checklistItems?: ChecklistItemSyncDto[];
  attachments?: AttachmentSyncDto[];
}

export interface UserSettingsSyncDto {
  themeSettings?: Record<string, unknown> | null;
  reminderSettings?: Record<string, unknown> | null;
  defaultNoteSettings?: Record<string, unknown> | null;
  interactionSettings?: Record<string, unknown> | null;
  viewOptionsSettings?: Record<string, unknown> | null;
  clientUpdatedAt: string;
}

export interface SyncPushRequestDto {
  deviceId: string;
  deviceName?: string;
  appVersion?: string;
  categories?: CategorySyncDto[];
  notes?: NoteSyncDto[];
  settings?: UserSettingsSyncDto;
}

export interface SyncPushResponseDto {
  serverRevision: string;
  appliedCount: {
    categories: number;
    notes: number;
    settings: number;
  };
  conflicts: Array<{
    entityType: 'category' | 'note';
    id: string;
    serverRevision: string;
    reason: string;
  }>;
}

export interface SyncChangesResponseDto {
  currentServerRevision: string;
  categories: CategorySyncDto[];
  notes: NoteSyncDto[];
  settings?: UserSettingsSyncDto | null;
  hasMore: boolean;
}

// ----------------------------------------------------------------------------------------------
// Backup DTOs
// ----------------------------------------------------------------------------------------------

export interface CreateBackupRequestDto {
  fileName: string;
  sizeBytes: number;
  sha256Checksum: string;
  notesCount: number;
  tasksCount: number;
  eventsCount: number;
  categoriesCount: number;
  schemaVersion: number;
  isEncrypted: boolean;
}

export interface CreateBackupResponseDto {
  backupId: string;
  uploadUrl: string;
  expiresInSeconds: number;
}

export interface BackupItemDto {
  id: string;
  fileName: string;
  sizeBytes: string;
  sha256Checksum: string;
  notesCount: number;
  tasksCount: number;
  eventsCount: number;
  categoriesCount: number;
  schemaVersion: number;
  isEncrypted: boolean;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  verifiedAt?: string | null;
  verificationError?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface VerifyBackupResponseDto {
  backupId: string;
  status: 'COMPLETED' | 'FAILED';
  verifiedAt: string;
  actualSizeBytes: string;
  message: string;
}

export interface RestoreBackupResponseDto {
  backupId: string;
  downloadUrl: string;
  expiresInSeconds: number;
  sha256Checksum: string;
  sizeBytes: string;
}

// ----------------------------------------------------------------------------------------------
// Attachment DTOs
// ----------------------------------------------------------------------------------------------

export interface AttachmentUploadUrlRequestDto {
  attachmentId: string;
  noteId?: string;
  displayName: string;
  mimeType: string;
  sizeBytes: number;
}

export interface AttachmentUploadUrlResponseDto {
  attachmentId: string;
  uploadUrl: string;
  storageKey: string;
  expiresInSeconds: number;
}

export interface AttachmentVerifyResponseDto {
  attachmentId: string;
  storageKey: string;
  sizeBytes: string;
  status: 'AVAILABLE' | 'FAILED';
  verifiedAt: string;
}

export interface AttachmentDownloadUrlResponseDto {
  attachmentId: string;
  downloadUrl: string;
  expiresInSeconds: number;
  mimeType?: string | null;
  displayName?: string | null;
}


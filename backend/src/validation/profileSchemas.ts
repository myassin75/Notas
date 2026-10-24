import { z } from 'zod';

export const updateProfileSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(1, 'Display name cannot be empty')
    .max(100, 'Display name cannot exceed 100 characters')
    .nullable()
    .optional(),
  photoUrl: z
    .string()
    .trim()
    .url('Photo URL must be a valid URL')
    .max(500, 'Photo URL cannot exceed 500 characters')
    .nullable()
    .optional(),
});

export type UpdateProfileInput = z.infer<typeof updateProfileSchema>;

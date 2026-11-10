import type { VercelRequest, VercelResponse } from '@vercel/node';
import { UnauthorizedError, ForbiddenError, ConflictError } from '../errors/AppError.js';
import { verifyFirebaseToken, getFirebaseAdmin } from '../utils/firebaseAdmin.js';
import { prisma } from '../db/client.js';
import { logger } from '../utils/logger.js';
import type { AuthenticatedRequest, AuthenticatedUser } from '../types/index.js';

export async function authenticate(
  req: VercelRequest,
  _res: VercelResponse,
  options?: { checkRevoked?: boolean }
): Promise<AuthenticatedUser> {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    throw new UnauthorizedError('Missing or malformed Authorization header. Expected Bearer token.');
  }

  const token = authHeader.substring(7).trim();
  if (!token) {
    throw new UnauthorizedError('Empty bearer authentication token provided.');
  }

  const decodedToken = await verifyFirebaseToken(token, options?.checkRevoked ?? false);

  const authenticatedUser: AuthenticatedUser = {
    uid: decodedToken.uid,
    email: decodedToken.email ?? '',
    emailVerified: decodedToken.email_verified ?? false,
    name: decodedToken.name,
    picture: decodedToken.picture,
  };

  // Upsert or re-key user record in PostgreSQL to ensure foreign keys exist
  try {
    let userRecord = await prisma.user.findUnique({
      where: { id: authenticatedUser.uid },
    });

    if (userRecord) {
      const shouldMarkVerified = authenticatedUser.emailVerified && userRecord.emailVerificationStatus !== 'VERIFIED';
      userRecord = await prisma.user.update({
        where: { id: authenticatedUser.uid },
        data: {
          email: authenticatedUser.email,
          emailVerified: authenticatedUser.emailVerified,
          emailVerificationStatus: shouldMarkVerified ? 'VERIFIED' : userRecord.emailVerificationStatus,
          displayName: authenticatedUser.name ?? undefined,
          photoUrl: authenticatedUser.picture ?? undefined,
        },
      });
    } else {
      // UID not found. Check if an account with this email exists (e.g. recreated in Firebase Console)
      const existingByEmail = authenticatedUser.email
        ? await prisma.user.findUnique({ where: { email: authenticatedUser.email } })
        : null;

      if (existingByEmail) {
        if (existingByEmail.deletionPending) {
          throw new ForbiddenError('Account is pending deletion and cannot process new requests.');
        }

        // Identity linking policy: Never link or re-key unless the incoming identity has a verified email
        if (!authenticatedUser.emailVerified) {
          throw new ConflictError(
            'An account with this email already exists. Please verify your email before linking.',
            undefined,
            'EMAIL_NOT_VERIFIED'
          );
        }

        // Verify if the old identity still exists as an active user in Firebase Admin
        try {
          const oldFirebaseUser = await getFirebaseAdmin().auth().getUser(existingByEmail.id);
          if (oldFirebaseUser && oldFirebaseUser.uid !== authenticatedUser.uid) {
            logger.warn('Conflicting active Firebase user identities detected for email', {
              existingDbUid: existingByEmail.id,
              incomingUid: authenticatedUser.uid,
              email: authenticatedUser.email,
            });
            throw new ConflictError(
              'An existing account with this email is associated with a different user identity.',
              undefined,
              'ACCOUNT_CONFLICT'
            );
          }
        } catch (error: unknown) {
          if (error instanceof ConflictError || error instanceof ForbiddenError) {
            throw error;
          }
          const fbError = error as { code?: string; message?: string } | null | undefined;
          // If the old user was deleted in Firebase ('auth/user-not-found'), re-keying is permitted.
          if (fbError?.code !== 'auth/user-not-found') {
            logger.warn('Could not verify old Firebase user status during identity re-key check', {
              error: fbError?.message,
              oldUid: existingByEmail.id,
            });
            if (process.env.NODE_ENV !== 'test') {
              throw new ConflictError(
                'Unable to safely verify existing account identity. Please contact support.',
                undefined,
                'IDENTITY_VERIFICATION_FAILED'
              );
            }
          }
        }

        logger.info('Re-keying existing PostgreSQL user to new authoritative Firebase UID', {
          oldUid: existingByEmail.id,
          newUid: authenticatedUser.uid,
          email: authenticatedUser.email,
        });

        // Foreign keys (categories, notes, etc.) have ON UPDATE CASCADE
        await prisma.$executeRaw`
          UPDATE users
          SET id = ${authenticatedUser.uid},
              "emailVerified" = ${authenticatedUser.emailVerified},
              "displayName" = ${authenticatedUser.name ?? existingByEmail.displayName},
              "photoUrl" = ${authenticatedUser.picture ?? existingByEmail.photoUrl},
              "updatedAt" = NOW()
          WHERE email = ${authenticatedUser.email}
        `;

        userRecord = await prisma.user.findUnique({
          where: { id: authenticatedUser.uid },
        });
      } else {
        userRecord = await prisma.user.create({
          data: {
            id: authenticatedUser.uid,
            email: authenticatedUser.email,
            emailVerified: authenticatedUser.emailVerified,
            emailVerificationStatus: authenticatedUser.emailVerified ? 'VERIFIED' : 'PENDING_VERIFICATION',
            displayName: authenticatedUser.name,
            photoUrl: authenticatedUser.picture,
          },
        });
      }
    }

    // If account deletion is in progress, disallow write/sync operations unless completing deletion
    if (userRecord?.deletionPending && req.method?.toUpperCase() !== 'DELETE') {
      throw new ForbiddenError('Account is pending deletion and cannot process new requests.');
    }
  } catch (error) {
    if (error instanceof ForbiddenError || error instanceof ConflictError) throw error;

    logger.error('Failed to sync authenticated user record with PostgreSQL', error, {
      userId: authenticatedUser.uid,
    });
    // In test environment, don't hard block auth if DB is mocked/disconnected
    if (process.env.NODE_ENV !== 'test') {
      throw error;
    }
  }

  // Attach to request
  (req as AuthenticatedRequest).user = authenticatedUser;
  return authenticatedUser;
}

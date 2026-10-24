import type { VercelRequest, VercelResponse } from '@vercel/node';
import { UnauthorizedError, ForbiddenError } from '../errors/AppError.js';
import { verifyFirebaseToken } from '../utils/firebaseAdmin.js';
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
      userRecord = await prisma.user.update({
        where: { id: authenticatedUser.uid },
        data: {
          email: authenticatedUser.email,
          emailVerified: authenticatedUser.emailVerified,
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
    if (error instanceof ForbiddenError) throw error;

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

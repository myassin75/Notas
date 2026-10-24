import * as admin from 'firebase-admin';
import { UnauthorizedError } from '../errors/AppError.js';
import { logger } from './logger.js';


export function getFirebaseAdmin(): admin.app.App {
  if (admin.apps.length > 0 && admin.apps[0]) {
    return admin.apps[0];
  }

  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  let privateKey = process.env.FIREBASE_PRIVATE_KEY;

  if (!projectId || !clientEmail || !privateKey) {
    if (process.env.NODE_ENV === 'test') {
      logger.warn('Firebase Admin credentials not set in test environment; using dummy app');
      return admin.initializeApp({
        projectId: 'test-project',
      });
    }
    throw new Error(
      'Missing Firebase Admin credentials in environment variables (FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY)'
    );
  }

  // Handle escaped newlines in PEM format when loaded from env
  if (privateKey.includes('\\n')) {
    privateKey = privateKey.replace(/\\n/g, '\n');
  }

  const app = admin.initializeApp({
    credential: admin.credential.cert({
      projectId,
      clientEmail,
      privateKey,
    }),
  });

  logger.info('Firebase Admin SDK initialized successfully', { projectId });
  return app;
}

export async function verifyFirebaseToken(idToken: string, checkRevoked = false) {
  if (!idToken || typeof idToken !== 'string') {
    throw new UnauthorizedError('Missing or malformed Authorization bearer token');
  }

  try {
    const app = getFirebaseAdmin();
    const decodedToken = await app.auth().verifyIdToken(idToken, checkRevoked);
    return decodedToken;
  } catch (error) {
    logger.warn('Firebase token verification failed', { error, checkRevoked });
    throw new UnauthorizedError('Invalid, expired, or revoked authentication token');
  }
}


import { describe, it, expect } from 'vitest';
import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

describe('Credential Hygiene & Secret Incident Response (F01 Regression Tests)', () => {
  const rootDir = path.resolve(__dirname, '../..');

  it('verifies that no service-account JSON files are tracked by Git', () => {
    const trackedFiles = execSync('git ls-files', { cwd: rootDir, encoding: 'utf-8' })
      .split('\n')
      .map((f) => f.trim())
      .filter(Boolean);

    const forbiddenPatterns = [
      /adminsdk.*\.json$/i,
      /service-account.*\.json$/i,
      /notas-7db14.*\.json$/i,
    ];

    const matchedFiles: string[] = [];
    for (const file of trackedFiles) {
      for (const pattern of forbiddenPatterns) {
        if (pattern.test(file)) {
          matchedFiles.push(file);
        }
      }
    }

    expect(matchedFiles).toEqual([]);
  });

  it('verifies that .gitignore blocks service account JSON patterns anywhere in the repository', () => {
    const testFilenames = [
      'notas-7db14-firebase-adminsdk-fbsvc-test.json',
      'app/notas-7db14-6f6a068f8ede.json',
      'backend/service-account.json',
      'notas-credential.json',
      'app/subfolder/test-adminsdk-abc.json',
    ];

    for (const file of testFilenames) {
      try {
        const result = execSync(`git check-ignore "${file}"`, { cwd: rootDir, encoding: 'utf-8' }).trim();
        expect(result).toBe(file);
      } catch (err) {
        throw new Error(`File '${file}' should be ignored by .gitignore but is NOT ignored.`);
      }
    }
  });

  it('verifies that no tracked files contain real unredacted private keys', () => {
    const trackedFiles = execSync('git ls-files', { cwd: rootDir, encoding: 'utf-8' })
      .split('\n')
      .map((f) => f.trim())
      .filter(Boolean);

    const privateKeyRegex = /-----BEGIN [A-Z ]*PRIVATE KEY-----/;
    const violations: { file: string; line: number }[] = [];

    for (const relPath of trackedFiles) {
      // Exclude binary files and .env.example placeholder
      if (
        relPath.endsWith('.png') ||
        relPath.endsWith('.jpg') ||
        relPath.endsWith('.jpeg') ||
        relPath.endsWith('.jar') ||
        relPath.endsWith('.apk') ||
        relPath.endsWith('.ttf') ||
        relPath.endsWith('.ico')
      ) {
        continue;
      }

      const fullPath = path.join(rootDir, relPath);
      if (!fs.existsSync(fullPath)) continue;

      const content = fs.readFileSync(fullPath, 'utf-8');
      if (privateKeyRegex.test(content)) {
        // Allow placeholder documentation or .env.example
        const isDocOrExample =
          relPath === 'backend/.env.example' ||
          relPath.endsWith('.md');

        // Check if it's an actual unredacted private key (which contains more than 100 characters of base64)
        const hasActualKeyMaterial =
          /[A-Za-z0-9+/]{64,}/.test(content) && content.includes('PRIVATE KEY');

        if (!isDocOrExample || hasActualKeyMaterial) {
          violations.push({ file: relPath, line: 0 });
        }
      }
    }

    expect(violations).toEqual([]);
  });
});

/**
 * Safely escapes HTML special characters to prevent HTML/XSS injection.
 */
export function escapeHtml(unsafe: string | null | undefined): string {
  if (!unsafe) return '';
  return String(unsafe)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * Masks an email address for safe logging without exposing full PII.
 * Example: "user@example.com" -> "u***r@example.com"
 */
export function maskEmail(email: string): string {
  if (!email || !email.includes('@')) return '***';
  const [localPart, domain] = email.split('@');
  if (!localPart || localPart.length <= 2) {
    return `${localPart?.[0] || '*'}***@${domain || '***'}`;
  }
  return `${localPart[0]}***${localPart[localPart.length - 1]}@${domain}`;
}


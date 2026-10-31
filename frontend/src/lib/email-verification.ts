export const EMAIL_NOT_VERIFIED = 'EMAIL_NOT_VERIFIED';

export class AuthError extends Error {
  code?: string;
  constructor(message: string, code?: string) {
    super(message);
    this.code = code;
  }
}

export function isEmailNotVerified(err: unknown): boolean {
  return err instanceof AuthError && err.code === EMAIL_NOT_VERIFIED;
}

export function verifyEmailSentPath(email: string): string {
  return `/verify-email-sent?email=${encodeURIComponent(email)}`;
}

export function verifiedLoginPath(): string {
  return `/login?message=${encodeURIComponent('E-posta adresiniz dogrulandi. Simdi giris yapabilirsiniz.')}`;
}

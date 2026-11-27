import { api, type AuthData, type Profile } from '@/api';

export const PASSWORD_HINT = 'Şifre 10-128 karakter olmalı, en az bir harf ve bir rakam içermeli ve e-posta ile aynı olmamalı';

export const isStrongPassword = (p: string, email?: string) =>
  p.length >= 10 && p.length <= 128 && /[A-Za-z]/.test(p) && /\d/.test(p) && (!email || p.toLowerCase() !== email.toLowerCase());

export function validateName(first: string, last: string): string | null {
  const f = first.trim(), l = last.trim();
  if (!f || !l) return 'Ad ve soyad alanları zorunludur.';
  if (f.length > 100 || l.length > 100) return 'Ad ve soyad en fazla 100 karakter olabilir.';
  return null;
}

export function validatePasswordChange(current: string, next: string, confirm: string, email?: string): string | null {
  if (!current || !next || !confirm) return 'Tüm alanlar zorunludur.';
  if (!isStrongPassword(next, email)) return PASSWORD_HINT;
  if (next !== confirm) return 'Yeni şifre ve tekrarı eşleşmiyor.';
  if (next === current) return 'Yeni şifre mevcut şifre ile aynı olamaz.';
  return null;
}

export const profileApi = {
  update: (firstName: string, lastName: string) => api.patch<Profile>('/api/me', { firstName: firstName.trim(), lastName: lastName.trim() }),
  changePassword: (currentPassword: string, newPassword: string) => api.post<AuthData>('/api/me/password', { currentPassword, newPassword }),
};

import { api, type AuthData, type Profile } from '@/api';

export const PASSWORD_HINT = 'Sifre 10-128 karakter olmali, en az bir harf ve bir rakam icermeli ve e-posta ile ayni olmamali';

export const isStrongPassword = (p: string, email?: string) =>
  p.length >= 10 && p.length <= 128 && /[A-Za-z]/.test(p) && /\d/.test(p) && (!email || p.toLowerCase() !== email.toLowerCase());

export function validateName(first: string, last: string): string | null {
  const f = first.trim(), l = last.trim();
  if (!f || !l) return 'Ad ve soyad alanlari zorunludur.';
  if (f.length > 100 || l.length > 100) return 'Ad ve soyad en fazla 100 karakter olabilir.';
  return null;
}

export function validatePasswordChange(current: string, next: string, confirm: string, email?: string): string | null {
  if (!current || !next || !confirm) return 'Tum alanlar zorunludur.';
  if (!isStrongPassword(next, email)) return PASSWORD_HINT;
  if (next !== confirm) return 'Yeni sifre ve tekrari eslesmiyor.';
  if (next === current) return 'Yeni sifre mevcut sifre ile ayni olamaz.';
  return null;
}

export const profileApi = {
  update: (firstName: string, lastName: string) => api.patch<Profile>('/api/me', { firstName: firstName.trim(), lastName: lastName.trim() }),
  changePassword: (currentPassword: string, newPassword: string) => api.post<AuthData>('/api/me/password', { currentPassword, newPassword }),
};

import { useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { api } from '@/api';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { AuthShell, Hint, SuccessNote } from '@/features/account/AccountUi';

// Same rule and wording as the web reset page.
const PASSWORD_HINT = 'Şifre 10-128 karakter olmalı, en az bir harf ve bir rakam içermeli ve e-posta ile aynı olmamalı';
const isStrong = (p: string) => p.length >= 10 && p.length <= 128 && /\p{L}/u.test(p) && /\p{N}/u.test(p);

/** Password reset deep link: waylo://reset-password?token=... or https://<host>/reset-password?token=... */
export default function ResetPassword() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(token ? null : 'Geçersiz sıfırlama linki');
  const [done, setDone] = useState(false);

  async function submit() {
    setError(null);
    if (!token) { setError('Geçersiz sıfırlama linki'); return; }
    if (password !== confirm) { setError('Şifreler uyuşmuyor'); return; }
    if (!isStrong(password)) { setError(PASSWORD_HINT); return; }
    setBusy(true);
    try { await api.publicPost('/api/auth/reset-password', { token, newPassword: password }); setDone(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'Şifre sıfırlanamadı'); }
    finally { setBusy(false); }
  }

  return (
    <Screen>
      <AuthShell title="Yeni şifre belirle">
      <ErrorBanner message={error} />
      {done ? (
        <SuccessNote text="Şifreniz güncellendi. Yeni şifrenizle giriş yapabilirsiniz." />
      ) : token ? (
        <>
          <Hint>{PASSWORD_HINT}</Hint>
          <Field label="Yeni şifre" value={password} onChangeText={setPassword} secureTextEntry />
          <Field label="Yeni şifre (tekrar)" value={confirm} onChangeText={setConfirm} secureTextEntry />
          <Button title={busy ? 'Kaydediliyor...' : 'Şifreyi güncelle'} onPress={submit} disabled={busy || !password || !confirm} />
        </>
      ) : null}
      </AuthShell>
    </Screen>
  );
}

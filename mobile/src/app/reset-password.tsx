import { useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { api } from '@/api';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { AuthShell, Hint, SuccessNote } from '@/features/account/AccountUi';

// Same rule and wording as the web reset page.
const PASSWORD_HINT = 'Sifre 10-128 karakter olmali, en az bir harf ve bir rakam icermeli ve e-posta ile ayni olmamali';
const isStrong = (p: string) => p.length >= 10 && p.length <= 128 && /\p{L}/u.test(p) && /\p{N}/u.test(p);

/** Password reset deep link: waylo://reset-password?token=... or https://<host>/reset-password?token=... */
export default function ResetPassword() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(token ? null : 'Gecersiz sifirlama linki');
  const [done, setDone] = useState(false);

  async function submit() {
    setError(null);
    if (!token) { setError('Gecersiz sifirlama linki'); return; }
    if (password !== confirm) { setError('Sifreler uyusmuyor'); return; }
    if (!isStrong(password)) { setError(PASSWORD_HINT); return; }
    setBusy(true);
    try { await api.publicPost('/api/auth/reset-password', { token, newPassword: password }); setDone(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'Sifre sifirlanamadi'); }
    finally { setBusy(false); }
  }

  return (
    <Screen>
      <AuthShell title="Yeni sifre belirle">
      <ErrorBanner message={error} />
      {done ? (
        <SuccessNote text="Sifreniz guncellendi. Yeni sifrenizle giris yapabilirsiniz." />
      ) : token ? (
        <>
          <Hint>{PASSWORD_HINT}</Hint>
          <Field label="Yeni sifre" value={password} onChangeText={setPassword} secureTextEntry />
          <Field label="Yeni sifre (tekrar)" value={confirm} onChangeText={setConfirm} secureTextEntry />
          <Button title={busy ? 'Kaydediliyor...' : 'Sifreyi guncelle'} onPress={submit} disabled={busy || !password || !confirm} />
        </>
      ) : null}
      </AuthShell>
    </Screen>
  );
}

import { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field } from '@/components/ui';
import { AuthShell, Hint } from '@/features/account/AccountUi';

export default function Forgot() {
  const { forgotPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit() {
    setBusy(true); setError(null);
    try { await forgotPassword(email.trim()); setDone(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'İstek gönderilemedi'); }
    finally { setBusy(false); }
  }
  return (
      <AuthShell title="Şifremi unuttum">
      <ErrorBanner message={error} />
      {done ? <Hint>Hesap varsa şifre sıfırlama bağlantısı e-postana gönderildi.</Hint> : (
        <>
          <Field label="E-posta" value={email} onChangeText={setEmail} keyboardType="email-address" />
          <Button title={busy ? 'Gönderiliyor...' : 'Bağlantı gönder'} onPress={submit} disabled={busy || !email} />
        </>
      )}
      </AuthShell>
  );
}

import { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';

export default function Forgot() {
  const { forgotPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit() {
    setBusy(true); setError(null);
    try { await forgotPassword(email.trim()); setDone(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'Istek gonderilemedi'); }
    finally { setBusy(false); }
  }
  return (
    <Screen>
      <Title>Sifremi unuttum</Title>
      <ErrorBanner message={error} />
      {done ? <Muted>Hesap varsa sifre sifirlama baglantisi e-postana gonderildi.</Muted> : (
        <>
          <Field label="E-posta" value={email} onChangeText={setEmail} keyboardType="email-address" />
          <Button title={busy ? 'Gonderiliyor...' : 'Baglanti gonder'} onPress={submit} disabled={busy || !email} />
        </>
      )}
    </Screen>
  );
}

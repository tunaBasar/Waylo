import { useState } from 'react';
import { Link } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { AuthShell, Hint, LinkText } from '@/features/account/AccountUi';

export default function Register() {
  const { register } = useAuth();
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (v: string) => setF((p) => ({ ...p, [k]: v }));

  async function submit() {
    setBusy(true); setError(null);
    try { const r = await register({ ...f, email: f.email.trim() }); setSentTo(r.email); }
    catch (e) { setError(e instanceof Error ? e.message : 'Kayit basarisiz'); }
    finally { setBusy(false); }
  }
  if (sentTo) {
    return (
      <Screen>
        <AuthShell title="E-postani dogrula">
        <Hint>{`${sentTo} adresine dogrulama baglantisi gonderildi. Dogruladiktan sonra giris yapabilirsin.`}</Hint>
        <Link href="/(auth)/login" accessibilityRole="link" accessibilityLabel="Girise don"><LinkText>Girise don</LinkText></Link>
        </AuthShell>
      </Screen>
    );
  }
  return (
    <Screen>
      <AuthShell title="Kayit ol">
      <ErrorBanner message={error} />
      <Field label="Ad" value={f.firstName} onChangeText={set('firstName')} autoCapitalize="words" />
      <Field label="Soyad" value={f.lastName} onChangeText={set('lastName')} autoCapitalize="words" />
      <Field label="E-posta" value={f.email} onChangeText={set('email')} keyboardType="email-address" />
      <Field label="Sifre" value={f.password} onChangeText={set('password')} secureTextEntry />
      <Button title={busy ? 'Kaydediliyor...' : 'Kayit ol'} onPress={submit} disabled={busy || !f.firstName || !f.lastName || !f.email || !f.password} />
      </AuthShell>
    </Screen>
  );
}

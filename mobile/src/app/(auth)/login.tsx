import { useState } from 'react';
import { Link } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field, Screen, Title } from '@/components/ui';

export default function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true); setError(null);
    try { await login(email.trim(), password); }
    catch (e) { setError(e instanceof Error ? e.message : 'Giris basarisiz'); }
    finally { setBusy(false); }
  }
  return (
    <Screen>
      <Title>Waylo&apos;ya giris</Title>
      <ErrorBanner message={error} />
      <Field label="E-posta" value={email} onChangeText={setEmail} keyboardType="email-address" autoComplete="email" />
      <Field label="Sifre" value={password} onChangeText={setPassword} secureTextEntry />
      <Button title={busy ? 'Giris yapiliyor...' : 'Giris yap'} onPress={submit} disabled={busy || !email || !password} />
      <Link href="/(auth)/register" accessibilityRole="link" accessibilityLabel="Hesabin yok mu? Kayit ol">Hesabin yok mu? Kayit ol</Link>
      <Link href="/(auth)/forgot" accessibilityRole="link" accessibilityLabel="Sifremi unuttum">Sifremi unuttum</Link>
    </Screen>
  );
}

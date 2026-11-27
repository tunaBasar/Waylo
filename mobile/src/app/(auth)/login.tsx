import { useState } from 'react';
import { Link } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { AuthShell, Hint, LinkText } from '@/features/account/AccountUi';

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
      <AuthShell title="Waylo'ya giriş">
      <Hint>Otel, uçuş ve araç kiralama tek yerde</Hint>
      <ErrorBanner message={error} />
      <Field label="E-posta" value={email} onChangeText={setEmail} keyboardType="email-address" autoComplete="email" />
      <Field label="Sifre" value={password} onChangeText={setPassword} secureTextEntry />
      <Button title={busy ? 'Giris yapiliyor...' : 'Giris yap'} onPress={submit} disabled={busy || !email || !password} />
      <Link href="/(auth)/register" accessibilityRole="link" accessibilityLabel="Hesabin yok mu? Kayit ol"><LinkText>Hesabin yok mu? Kayit ol</LinkText></Link>
      <Link href="/(auth)/forgot" accessibilityRole="link" accessibilityLabel="Sifremi unuttum"><LinkText>Sifremi unuttum</LinkText></Link>
      </AuthShell>
    </Screen>
  );
}

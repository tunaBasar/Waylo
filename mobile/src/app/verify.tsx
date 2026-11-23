import { useEffect, useState } from 'react';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, Text } from 'react-native';
import { api } from '@/api';
import { Button, Card, ErrorBanner, Muted, Screen, Title } from '@/components/ui';

/** Email verification deep link: waylo://verify?token=... or https://<host>/verify?token=... */
export default function Verify() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const router = useRouter();
  const [state, setState] = useState<{ status: 'loading' | 'success' | 'error'; message: string }>({ status: 'loading', message: '' });

  useEffect(() => {
    if (!token) return;
    let alive = true;
    api.publicGet<unknown>(`/api/auth/verify?token=${encodeURIComponent(token)}`)
      .then(() => { if (alive) setState({ status: 'success', message: 'E-posta adresiniz dogrulandi. Giris yapabilirsiniz.' }); })
      .catch((e: unknown) => { if (alive) setState({ status: 'error', message: e instanceof Error ? e.message : 'Dogrulama sirasinda bir hata olustu' }); });
    return () => { alive = false; };
  }, [token]);

  const s = token ? state : { status: 'error' as const, message: 'Dogrulama tokeni bulunamadi' };
  return (
    <Screen>
      <Title>E-posta dogrulama</Title>
      {s.status === 'loading' ? (
        <Card><ActivityIndicator accessibilityLabel="Dogrulaniyor" /><Muted>E-posta dogrulaniyor...</Muted></Card>
      ) : s.status === 'success' ? (
        <Card><Text accessibilityRole="alert" accessibilityLiveRegion="polite">{s.message}</Text></Card>
      ) : <ErrorBanner message={s.message} />}
      {s.status !== 'loading' ? <Button kind="secondary" title="Uygulamaya don" onPress={() => router.replace('/')} /> : null}
    </Screen>
  );
}

import { useEffect, useState } from 'react';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator } from 'react-native';
import { api } from '@/api';
import { Button, ErrorBanner, Screen } from '@/components/ui';
import { AuthShell, Hint, Section, SuccessNote } from '@/features/account/AccountUi';

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
      <AuthShell title="E-posta dogrulama">
      {s.status === 'loading' ? (
        <Section><ActivityIndicator accessibilityLabel="Dogrulaniyor" /><Hint>E-posta dogrulaniyor...</Hint></Section>
      ) : s.status === 'success' ? (
        <SuccessNote text={s.message} />
      ) : <ErrorBanner message={s.message} />}
      {s.status !== 'loading' ? <Button kind="secondary" title="Uygulamaya don" onPress={() => router.replace('/')} /> : null}
      </AuthShell>
    </Screen>
  );
}

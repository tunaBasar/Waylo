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
      .then(() => { if (alive) setState({ status: 'success', message: 'E-posta adresiniz doğrulandı. Giriş yapabilirsiniz.' }); })
      .catch((e: unknown) => { if (alive) setState({ status: 'error', message: e instanceof Error ? e.message : 'Doğrulama sırasında bir hata oluştu' }); });
    return () => { alive = false; };
  }, [token]);

  const s = token ? state : { status: 'error' as const, message: 'Doğrulama tokeni bulunamadı' };
  return (
    <Screen>
      <AuthShell title="E-posta doğrulama">
      {s.status === 'loading' ? (
        <Section><ActivityIndicator accessibilityLabel="Doğrulanıyor" /><Hint>E-posta doğrulanıyor...</Hint></Section>
      ) : s.status === 'success' ? (
        <SuccessNote text={s.message} />
      ) : <ErrorBanner message={s.message} />}
      {s.status !== 'loading' ? <Button kind="secondary" title="Uygulamaya dön" onPress={() => router.replace('/')} /> : null}
      </AuthShell>
    </Screen>
  );
}

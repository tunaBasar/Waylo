import { useState } from 'react';
import { ApiError } from '@/api';
import { useAuth } from '@/features/auth/AuthContext';
import { api } from '@/api';
import { Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';

export default function Settings() {
  const { clearSession, logout } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function deleteAccount() {
    setBusy(true); setError(null);
    try { await api.del('/api/me', { password }); await clearSession(); }
    catch (e) {
      if (e instanceof ApiError && e.status === 400) setError('Sifre hatali');
      else if (e instanceof ApiError && e.status === 422 && e.message.startsWith('SOLE_OWNER')) setError('Hesabinizi silmeden once organizasyonunuzun sahipligini devretmelisiniz.');
      else setError(e instanceof Error ? e.message : 'Hesap silinemedi');
    } finally { setBusy(false); }
  }
  return (
    <Screen>
      <Title>Ayarlar</Title>
      <Muted>Adinizi ve sifrenizi Profil sekmesinden degistirebilirsiniz.</Muted>
      <Button kind="secondary" title="Cikis yap" onPress={() => { void logout(); }} />
      <Card>
        <Muted>Tehlikeli bolge: hesabi silmek geri alinamaz.</Muted>
        {!confirming ? <Button kind="danger" title="Hesabimi sil" onPress={() => setConfirming(true)} /> : (
          <>
            <Field label="Sifreniz" value={password} onChangeText={setPassword} secureTextEntry />
            <ErrorBanner message={error} />
            <Button kind="danger" title="Hesabi kalici olarak sil" disabled={busy || !password} onPress={deleteAccount} />
            <Button kind="secondary" title="Vazgec" onPress={() => { setConfirming(false); setPassword(''); }} />
          </>
        )}
      </Card>
    </Screen>
  );
}

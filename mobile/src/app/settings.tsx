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

  const [newEmail, setNewEmail] = useState('');
  const [emailPassword, setEmailPassword] = useState('');
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);

  async function requestEmailChange() {
    setEmailBusy(true); setEmailError(null);
    try {
      await api.post('/api/me/email', { newEmail: newEmail.trim(), currentPassword: emailPassword });
      setPendingEmail(newEmail.trim().toLowerCase()); setNewEmail(''); setEmailPassword('');
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) setEmailError('Bu e-posta adresi zaten kullaniliyor');
      else setEmailError(e instanceof Error ? e.message : 'E-posta degistirilemedi');
    } finally { setEmailBusy(false); }
  }

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
        <Muted>E-posta adresini degistir</Muted>
        {pendingEmail ? (
          <>
            <Muted>Yeni gelen kutunuzu kontrol edin: {pendingEmail} adresine dogrulama linki gonderdik. Onaylayana kadar mevcut adresinizle giris yapabilirsiniz; onaydan sonra yeniden giris yapmaniz gerekir.</Muted>
            <Button kind="secondary" title="Baska bir adres gir" onPress={() => setPendingEmail(null)} />
          </>
        ) : (
          <>
            <Field label="Yeni e-posta" value={newEmail} onChangeText={setNewEmail} keyboardType="email-address" />
            <Field label="Mevcut sifreniz" value={emailPassword} onChangeText={setEmailPassword} secureTextEntry />
            <ErrorBanner message={emailError} />
            <Button title="Dogrulama linki gonder" disabled={emailBusy || !newEmail.trim() || !emailPassword} onPress={requestEmailChange} />
          </>
        )}
      </Card>
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

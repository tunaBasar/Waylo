import { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { api, ApiError } from '@/api';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { Hint, Section } from '@/features/account/AccountUi';

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
      <Hint>Adinizi ve sifrenizi Profil sekmesinden degistirebilirsiniz.</Hint>
      <Button kind="secondary" title="Cikis yap" onPress={() => { void logout(); }} />
      <Section title="E-posta adresini degistir">
        {pendingEmail ? (
          <>
            <Hint>Yeni gelen kutunuzu kontrol edin: {pendingEmail} adresine dogrulama linki gonderdik. Onaylayana kadar mevcut adresinizle giris yapabilirsiniz; onaydan sonra yeniden giris yapmaniz gerekir.</Hint>
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
      </Section>
      <Section>
        <Hint>Tehlikeli bolge: hesabi silmek geri alinamaz.</Hint>
        {!confirming ? <Button kind="danger" title="Hesabimi sil" onPress={() => setConfirming(true)} /> : (
          <>
            <Field label="Sifreniz" value={password} onChangeText={setPassword} secureTextEntry />
            <ErrorBanner message={error} />
            <Button kind="danger" title="Hesabi kalici olarak sil" disabled={busy || !password} onPress={deleteAccount} />
            <Button kind="secondary" title="Vazgec" onPress={() => { setConfirming(false); setPassword(''); }} />
          </>
        )}
      </Section>
    </Screen>
  );
}

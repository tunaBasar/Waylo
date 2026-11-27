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
      if (e instanceof ApiError && e.status === 409) setEmailError('Bu e-posta adresi zaten kullanılıyor');
      else setEmailError(e instanceof Error ? e.message : 'E-posta değiştirilemedi');
    } finally { setEmailBusy(false); }
  }

  async function deleteAccount() {
    setBusy(true); setError(null);
    try { await api.del('/api/me', { password }); await clearSession(); }
    catch (e) {
      if (e instanceof ApiError && e.status === 400) setError('Şifre hatalı');
      else if (e instanceof ApiError && e.status === 422 && e.message.startsWith('SOLE_OWNER')) setError('Hesabınızı silmeden önce organizasyonunuzun sahipliğini devretmelisiniz.');
      else setError(e instanceof Error ? e.message : 'Hesap silinemedi');
    } finally { setBusy(false); }
  }
  return (
    <Screen>
      <Hint>Adınızı ve şifrenizi Profil sekmesinden değiştirebilirsiniz.</Hint>
      <Button kind="secondary" title="Çıkış yap" onPress={() => { void logout(); }} />
      <Section title="E-posta adresini değiştir">
        {pendingEmail ? (
          <>
            <Hint>Yeni gelen kutunuzu kontrol edin: {pendingEmail} adresine doğrulama linki gönderdik. Onaylayana kadar mevcut adresinizle giriş yapabilirsiniz, onaydan sonra yeniden giriş yapmanız gerekir.</Hint>
            <Button kind="secondary" title="Başka bir adres gir" onPress={() => setPendingEmail(null)} />
          </>
        ) : (
          <>
            <Field label="Yeni e-posta" value={newEmail} onChangeText={setNewEmail} keyboardType="email-address" />
            <Field label="Mevcut şifreniz" value={emailPassword} onChangeText={setEmailPassword} secureTextEntry />
            <ErrorBanner message={emailError} />
            <Button title="Doğrulama linki gönder" disabled={emailBusy || !newEmail.trim() || !emailPassword} onPress={requestEmailChange} />
          </>
        )}
      </Section>
      <Section>
        <Hint>Tehlikeli bölge: hesabı silmek geri alınamaz.</Hint>
        {!confirming ? <Button kind="danger" title="Hesabımı sil" onPress={() => setConfirming(true)} /> : (
          <>
            <Field label="Şifreniz" value={password} onChangeText={setPassword} secureTextEntry />
            <ErrorBanner message={error} />
            <Button kind="danger" title="Hesabı kalıcı olarak sil" disabled={busy || !password} onPress={deleteAccount} />
            <Button kind="secondary" title="Vazgeç" onPress={() => { setConfirming(false); setPassword(''); }} />
          </>
        )}
      </Section>
    </Screen>
  );
}

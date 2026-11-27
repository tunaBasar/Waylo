import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useAuth } from '@/features/auth/AuthContext';
import { api, ApiError } from '@/api';
import { Button, ErrorBanner, Field } from '@/components/ui';
import { Hint, Icon, Section, StackScreen } from '@/features/account/AccountUi';
import { MIN_TOUCH, space, type Theme, type ThemePreference } from '@/theme/theme';
import { useStyles, useTheme, useThemePreference } from '@/theme/ThemeContext';

const OPTIONS: { value: ThemePreference; label: string; sub?: string; icon: 'theme-light-dark' | 'white-balance-sunny' | 'weather-night' }[] = [
  { value: 'system', label: 'Sistem', sub: 'Telefonun ayarını izler', icon: 'theme-light-dark' },
  { value: 'light', label: 'Açık', icon: 'white-balance-sunny' },
  { value: 'dark', label: 'Koyu', icon: 'weather-night' },
];

const makeStyles = ({ color, radius }: Theme) => StyleSheet.create({
  radio: { flexDirection: 'row', alignItems: 'center', gap: space.md, minHeight: MIN_TOUCH + 8, paddingVertical: space.sm, paddingHorizontal: space.md, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant },
  radioOn: { borderColor: color.primary, backgroundColor: color.surfaceContainer },
  grow: { flex: 1 },
});

export default function Settings() {
  const { clearSession, logout } = useAuth();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const { preference, setPreference } = useThemePreference();
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
    <StackScreen title="Ayarlar">
      <Section title="Görünüm">
        {OPTIONS.map((o) => {
          const checked = preference === o.value;
          return (
            <Pressable key={o.value} accessibilityRole="radio" accessibilityLabel={o.label} accessibilityState={{ checked }} onPress={() => setPreference(o.value)} style={[s.radio, checked && s.radioOn]}>
              <Icon name={o.icon} tint={checked ? color.primary : color.onSurfaceVariant} />
              <View style={s.grow}>
                <Text style={[type.bodyLg, { color: color.onSurface }]}>{o.label}</Text>
                {o.sub ? <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{o.sub}</Text> : null}
              </View>
              <Icon name={checked ? 'radiobox-marked' : 'radiobox-blank'} tint={checked ? color.primary : color.outline} />
            </Pressable>
          );
        })}
      </Section>
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
    </StackScreen>
  );
}

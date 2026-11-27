import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { Stack, useRouter, type Href } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { StatusPill } from '@/components/StatusPill';
import { useAccountStyles, Hint, NavRow, Section, SuccessNote } from '@/features/account/AccountUi';
import { profileApi, validateName, validatePasswordChange, PASSWORD_HINT } from '@/features/profile/api';

export default function ProfileScreen() {
  const router = useRouter();
  const accountStyles = useAccountStyles();
  const { user, logout, refreshProfile, applyAuth } = useAuth();
  const [first, setFirst] = useState('');
  const [last, setLast] = useState('');
  const [nameMsg, setNameMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [conf, setConf] = useState('');
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { if (user) { setFirst(user.firstName); setLast(user.lastName); } }, [user]);

  async function saveName() {
    const err = validateName(first, last);
    if (err) { setNameMsg({ ok: false, text: err }); return; }
    setBusy(true); setNameMsg(null);
    try { await profileApi.update(first, last); await refreshProfile(); setNameMsg({ ok: true, text: 'Profil güncellendi.' }); }
    catch (e) { setNameMsg({ ok: false, text: e instanceof Error ? e.message : 'Güncellenemedi' }); }
    finally { setBusy(false); }
  }
  async function savePassword() {
    const err = validatePasswordChange(cur, next, conf, user?.email);
    if (err) { setPwMsg({ ok: false, text: err }); return; }
    setBusy(true); setPwMsg(null);
    try {
      const d = await profileApi.changePassword(cur, next);
      if (d?.token && d.refreshToken) await applyAuth(d);
      setCur(''); setNext(''); setConf('');
      setPwMsg({ ok: true, text: 'Şifre güncellendi. Diğer oturumlar kapatıldı.' });
    } catch (e) { setPwMsg({ ok: false, text: e instanceof Error ? e.message : 'Şifre güncellenemedi' }); }
    finally { setBusy(false); }
  }

  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Hesap" />
      <Screen>
        {user ? (
          <Section>
            <Text style={accountStyles.strong}>{`${user.firstName} ${user.lastName}`}</Text>
            <Text style={accountStyles.meta}>{user.email}</Text>
            <StatusPill tone={user.emailVerified ? 'success' : 'warning'} text={user.emailVerified ? 'E-posta doğrulandı' : 'E-posta doğrulanmadı'} />
          </Section>
        ) : null}
        <Section title="Ad ve soyad">
          <Field label="Ad" value={first} onChangeText={setFirst} autoCapitalize="words" />
          <Field label="Soyad" value={last} onChangeText={setLast} autoCapitalize="words" />
          {nameMsg && (nameMsg.ok ? <SuccessNote text={nameMsg.text} /> : <ErrorBanner message={nameMsg.text} />)}
          <Button title="Kaydet" disabled={busy} onPress={saveName} />
        </Section>
        <Section title="Şifre değiştir">
          <Hint>{PASSWORD_HINT}</Hint>
          <Field label="Mevcut şifre" value={cur} onChangeText={setCur} secureTextEntry />
          <Field label="Yeni şifre" value={next} onChangeText={setNext} secureTextEntry />
          <Field label="Yeni şifre (tekrar)" value={conf} onChangeText={setConf} secureTextEntry />
          {pwMsg && (pwMsg.ok ? <SuccessNote text={pwMsg.text} /> : <ErrorBanner message={pwMsg.text} />)}
          <Button title="Şifreyi güncelle" disabled={busy} onPress={savePassword} />
        </Section>
        <Section>
          <NavRow icon="star-outline" label="Yorum yazabileceğim konaklamalar" onPress={() => router.push('/reviews')} />
          <NavRow icon="bell-outline" label="Bildirimler" onPress={() => router.push('/notifications')} />
          <NavRow icon="lifebuoy" label="Destek taleplerim" onPress={() => router.push('/support' as Href)} />
          <NavRow icon="cog-outline" label="Ayarlar" onPress={() => router.push('/settings')} last />
        </Section>
        <Button kind="danger" title="Çıkış yap" onPress={() => { void logout(); }} />
      </Screen>
    </View>
  );
}

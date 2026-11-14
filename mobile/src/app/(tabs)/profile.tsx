import { useEffect, useState } from 'react';
import { Text } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { profileApi, validateName, validatePasswordChange, PASSWORD_HINT } from '@/features/profile/api';

export default function ProfileScreen() {
  const router = useRouter();
  const { user, logout, refreshProfile, applyAuth } = useAuth();
  const [first, setFirst] = useState('');
  const [last, setLast] = useState('');
  const [nameMsg, setNameMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [conf, setConf] = useState('');
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (user) { setFirst(user.firstName); setLast(user.lastName); } }, [user]);

  async function saveName() {
    const err = validateName(first, last);
    if (err) { setNameMsg({ ok: false, text: err }); return; }
    setBusy(true); setNameMsg(null);
    try { await profileApi.update(first, last); await refreshProfile(); setNameMsg({ ok: true, text: 'Profil guncellendi.' }); }
    catch (e) { setNameMsg({ ok: false, text: e instanceof Error ? e.message : 'Guncellenemedi' }); }
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
      setPwMsg({ ok: true, text: 'Sifre guncellendi. Diger oturumlar kapatildi.' });
    } catch (e) { setPwMsg({ ok: false, text: e instanceof Error ? e.message : 'Sifre guncellenemedi' }); }
    finally { setBusy(false); }
  }

  return (
    <Screen>
      <Title>Profil</Title>
      {user ? (
        <Card>
          <Text style={{ fontWeight: '700' }}>{`${user.firstName} ${user.lastName}`}</Text>
          <Muted>{user.email}</Muted>
          <Muted>{user.emailVerified ? 'E-posta dogrulandi' : 'E-posta dogrulanmadi'}</Muted>
        </Card>
      ) : null}
      <Field label="Ad" value={first} onChangeText={setFirst} autoCapitalize="words" />
      <Field label="Soyad" value={last} onChangeText={setLast} autoCapitalize="words" />
      {nameMsg && (nameMsg.ok ? <Muted>{nameMsg.text}</Muted> : <ErrorBanner message={nameMsg.text} />)}
      <Button title="Kaydet" disabled={busy} onPress={saveName} />
      <Title>Sifre degistir</Title>
      <Muted>{PASSWORD_HINT}</Muted>
      <Field label="Mevcut sifre" value={cur} onChangeText={setCur} secureTextEntry />
      <Field label="Yeni sifre" value={next} onChangeText={setNext} secureTextEntry />
      <Field label="Yeni sifre (tekrar)" value={conf} onChangeText={setConf} secureTextEntry />
      {pwMsg && (pwMsg.ok ? <Muted>{pwMsg.text}</Muted> : <ErrorBanner message={pwMsg.text} />)}
      <Button title="Sifreyi guncelle" disabled={busy} onPress={savePassword} />
      <Button kind="secondary" title="Yorum yazabilecegim konaklamalar" onPress={() => router.push('/reviews')} />
      <Button kind="secondary" title="Bildirimler" onPress={() => router.push('/notifications')} />
      <Button kind="secondary" title="Ayarlar" onPress={() => router.push('/settings')} />
      <Button kind="danger" title="Cikis yap" onPress={() => { void logout(); }} />
    </Screen>
  );
}

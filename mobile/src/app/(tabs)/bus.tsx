import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import type { Gender, SeatMap, TripOffer } from '@/api';
import { busApi } from '@/features/bus/api';
import { busCartRequest, busErrorMessage, conflictSeats, GENDER_LABEL, GENDER_RULE_TEXT, MAX_SEATS, passengersReady, seatRows, type Picked } from '@/features/bus/seats';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { useCart } from '@/features/cart/CartContext';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, colors, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { DateField } from '@/components/DateField';
import { addDays, isoDate } from '@/features/dates';
import { fmtLocal, money } from '@/features/format';

const GENDERS: Gender[] = ['MALE', 'FEMALE'];

function SeatPicker({ offer }: { offer: TripOffer }) {
  const router = useRouter();
  const { user } = useAuth();
  const cart = useCart();
  const map = useAsync<SeatMap>(() => busApi.seats(offer.tripId, offer.fromSeq, offer.toSeq), [offer.tripId, offer.fromSeq, offer.toSeq]);
  const [picked, setPicked] = useState<Picked[]>([]);
  const [defaultGender, setDefaultGender] = useState<Gender>('MALE');
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const seatData = map.data;
  const conflicts = useMemo(() => (seatData ? conflictSeats(seatData.layout, seatData.seats, picked) : new Set<number>()), [seatData, picked]);
  const ready = passengersReady(picked, conflicts);
  const total = picked.length * offer.price;

  function toggle(seatNo: number) {
    setErr(null); setMsg(null);
    if (picked.some((p) => p.seatNo === seatNo)) { setPicked(picked.filter((p) => p.seatNo !== seatNo)); return; }
    if (picked.length >= MAX_SEATS) { setErr('Tek seferde en fazla 5 koltuk secilebilir.'); return; }
    setPicked([...picked, { seatNo, name: '', gender: defaultGender }]);
  }
  const patch = (seatNo: number, d: Partial<Picked>) => setPicked(picked.map((p) => (p.seatNo === seatNo ? { ...p, ...d } : p)));

  function addToCart() {
    if (!ready) return;
    if (!user) { setReturnTo('/bus'); router.navigate('/(auth)/login'); return; }
    const ok = cart.add({
      vertical: 'BUS', label: `${offer.fromCity} - ${offer.toCity} koltuk ${picked.map((p) => p.seatNo).join(', ')}`,
      amount: total, currency: offer.currency, request: busCartRequest(offer, picked),
    });
    setMsg(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
  }

  return (
    <AsyncView<SeatMap> state={map} emptyText="Koltuk haritasi alinamadi.">
      {(m) => (
        <Card>
          <Muted>Yeni secimde cinsiyet:</Muted>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {GENDERS.map((g) => <Button key={g} kind={defaultGender === g ? 'primary' : 'secondary'} title={GENDER_LABEL[g]} onPress={() => setDefaultGender(g)} />)}
          </View>
          {seatRows(m.layout, m.seats).map((row, i) => (
            <View key={i} style={{ flexDirection: 'row', justifyContent: 'center', gap: 24 }}>
              {row.map((block, k) => (
                <View key={k} style={{ flexDirection: 'row', gap: 6 }}>
                  {block.map((s) => {
                    const isPicked = picked.some((p) => p.seatNo === s.seatNo);
                    const bg = !s.available ? (s.occupantGender === 'FEMALE' ? '#f8c8dc' : '#bcd4f6') : isPicked ? colors.primary : '#e3e5e8';
                    return (
                      <Pressable key={s.seatNo} disabled={!s.available} onPress={() => toggle(s.seatNo)} accessibilityRole="button"
                        accessibilityLabel={`Koltuk ${s.seatNo}`} accessibilityState={{ selected: isPicked, disabled: !s.available }}
                        style={{ width: 40, height: 40, borderRadius: 6, alignItems: 'center', justifyContent: 'center', backgroundColor: bg, opacity: s.available ? 1 : 0.6, borderWidth: conflicts.has(s.seatNo) ? 2 : 0, borderColor: colors.error }}>
                        <Text style={{ color: isPicked ? '#fff' : colors.text, fontWeight: '600' }}>{s.seatNo}</Text>
                      </Pressable>
                    );
                  })}
                </View>
              ))}
            </View>
          ))}
          <Muted>{`Dolu koltuklar: pembe kadin, mavi erkek. ${GENDER_RULE_TEXT}`}</Muted>
          {picked.length === 0 ? <Muted>Haritadan koltuk secin.</Muted> : null}
          {picked.map((p) => (
            <View key={p.seatNo} style={{ gap: 6 }}>
              <Text style={{ fontWeight: '700' }}>{`Koltuk ${p.seatNo}`}</Text>
              <Field label="Ad soyad" value={p.name} onChangeText={(t) => patch(p.seatNo, { name: t })} maxLength={150} />
              <View style={{ flexDirection: 'row', gap: 8 }}>
                {GENDERS.map((g) => <Button key={g} kind={p.gender === g ? 'primary' : 'secondary'} title={GENDER_LABEL[g]} onPress={() => patch(p.seatNo, { gender: g })} />)}
              </View>
              {conflicts.has(p.seatNo) ? <Text style={{ color: colors.error }}>Yan koltukta farkli cinsiyet var</Text> : null}
            </View>
          ))}
          <ErrorBanner message={err} />
          {picked.length > 0 ? <Text style={{ fontWeight: '700' }}>{money(total, offer.currency)}</Text> : null}
          <Button kind="secondary" title="Sepete ekle" disabled={!ready} onPress={addToCart} />
          {msg ? <Muted>{msg}</Muted> : null}
        </Card>
      )}
    </AsyncView>
  );
}

export default function Bus() {
  const status = useAsync(() => busApi.salesStatus(), []);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => addDays(new Date(), 7));
  const [q, setQ] = useState<{ from: string; to: string; date: string } | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  // No `q!.x` inside the closure: the React Compiler (app.json reactCompiler) reads non-null access paths as memo
  // dependencies during render, and q is null until the first search (crashed the tab on Expo web, 27.11).
  const trips = useAsync(
    () => (q ? busApi.search(q.from, q.to, q.date) : Promise.resolve([] as TripOffer[]))
      .catch((e: unknown) => { throw new Error(busErrorMessage(e, 'Seferler yuklenemedi.')); }),
    [q], q !== null && status.data?.enabled === true);
  return (
    <Screen>
      <Title>Otobus bileti</Title>
      <AsyncView<{ enabled: boolean }> state={status} emptyText="Durum alinamadi.">
        {(s) => !s.enabled ? <Muted>Otobus satisi su an kapali.</Muted> : (
          <>
            <Field label="Nereden (sehir)" value={from} onChangeText={setFrom} autoCapitalize="words" />
            <Field label="Nereye (sehir)" value={to} onChangeText={setTo} autoCapitalize="words" />
            <DateField label="Tarih" value={date} minimumDate={new Date()} onChange={setDate} />
            <Button title="Ara" disabled={!from || !to} onPress={() => { setSelected(null); setQ({ from: from.trim(), to: to.trim(), date: isoDate(date) }); }} />
            {q && (
              <AsyncView<TripOffer[]> state={trips} isEmpty={(d) => d.length === 0} emptyText="Sefer bulunamadi.">
                {(list) => list.map((t) => {
                  const key = `${t.tripId}-${t.fromSeq}-${t.toSeq}`;
                  return (
                    <View key={key} style={{ gap: 8 }}>
                      <Card>
                        <Text style={{ fontWeight: '700' }}>{`${t.fromCity} → ${t.toCity}`}</Text>
                        <Muted>{`${t.companyName ?? ''} · ${fmtLocal(t.departureAt)} - ${fmtLocal(t.arrivalAt)} · ${t.availableSeats} bos koltuk`}</Muted>
                        <Text style={{ fontWeight: '700' }}>{money(t.price, t.currency)}</Text>
                        <Button kind="secondary" title={selected === key ? 'Koltuk secimini kapat' : 'Koltuk sec'} onPress={() => setSelected(selected === key ? null : key)} />
                      </Card>
                      {selected === key ? <SeatPicker offer={t} /> : null}
                    </View>
                  );
                })}
              </AsyncView>
            )}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}

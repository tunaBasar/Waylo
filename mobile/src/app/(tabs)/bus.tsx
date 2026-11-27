import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import type { Gender, SeatMap, TripOffer } from '@/api';
import { busApi } from '@/features/bus/api';
import { busCartRequest, busErrorMessage, conflictSeats, GENDER_LABEL, GENDER_RULE_TEXT, MAX_SEATS, passengersReady, seatRows, type Picked } from '@/features/bus/seats';
import { saveBusDraft, takeBusDraft } from '@/features/bus/draft';
import { buildBusReturn, busTripKey, parseBusReturn } from '@/features/returnState';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { useCart } from '@/features/cart/CartContext';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { ModeTabs } from '@/components/ModeTabs';
import { Price } from '@/components/Price';
import { StickyActionBar, STICKY_ACTION_BAR_HEIGHT } from '@/components/StickyActionBar';
import { color, radius, space, type, MIN_TOUCH } from '@/theme/tokens';
import { DateField } from '@/components/DateField';
import { addDays, isoDate, parseIsoDate } from '@/features/dates';
import { fmtLocal } from '@/features/format';

const GENDERS: Gender[] = ['FEMALE', 'MALE'];

type Bar = { amount: number; currency: string; ready: boolean };
type TripQ = { from: string; to: string; date: string };

const SEAT = 40;
const GENDER_LETTER: Record<Gender, string> = { FEMALE: 'K', MALE: 'E' };
const GENDER_WORD: Record<Gender, string> = { FEMALE: 'kadın', MALE: 'erkek' };
const FEMALE_BG = '#FBE3EC';
const MALE_BG = '#DCE8F8';

function LegendItem({ children, label }: { children: React.ReactNode; label: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.xs }}>
      {children}
      <Text style={[type.labelSm, { color: color.onSurface }]}>{label}</Text>
    </View>
  );
}

function SeatBox({ bg, border, children }: { bg: string; border?: string; children?: React.ReactNode }) {
  return (
    <View style={{ width: 24, height: 24, borderRadius: radius.sm, backgroundColor: bg, borderWidth: 1, borderColor: border ?? bg, alignItems: 'center', justifyContent: 'center' }}>{children}</View>
  );
}

function Legend() {
  return (
    <View accessible accessibilityLabel="Açıklama: boş, seçili, kadın yolcu K, erkek yolcu E" style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.md }}>
      <LegendItem label="Boş"><SeatBox bg={color.surfaceLowest} border={color.outline} /></LegendItem>
      <LegendItem label="Seçili"><SeatBox bg={color.primary}><MaterialCommunityIcons name="check" size={16} color={color.onPrimary} accessibilityElementsHidden importantForAccessibility="no" /></SeatBox></LegendItem>
      <LegendItem label="Kadın"><SeatBox bg={FEMALE_BG}><Text style={[type.labelSm, { color: color.onSurface }]}>K</Text></SeatBox></LegendItem>
      <LegendItem label="Erkek"><SeatBox bg={MALE_BG}><Text style={[type.labelSm, { color: color.onSurface }]}>E</Text></SeatBox></LegendItem>
    </View>
  );
}

function SeatPicker({ offer, search, onBar, register }: {
  offer: TripOffer; search: TripQ; onBar: (b: Bar | null) => void; register: (fn: (() => void) | null) => void;
}) {
  const router = useRouter();
  const { user } = useAuth();
  const cart = useCart();
  const map = useAsync<SeatMap>(() => busApi.seats(offer.tripId, offer.fromSeq, offer.toSeq), [offer.tripId, offer.fromSeq, offer.toSeq]);
  const [picked, setPicked] = useState<Picked[]>(() => takeBusDraft(busTripKey(offer)) ?? []);
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
    if (!user) { saveBusDraft(busTripKey(offer), picked); setReturnTo(buildBusReturn({ ...search, tripKey: busTripKey(offer) })); router.navigate('/(auth)/login'); return; }
    const ok = cart.add({
      vertical: 'BUS', label: `${offer.fromCity} - ${offer.toCity} koltuk ${picked.map((p) => p.seatNo).join(', ')}`,
      amount: total, currency: offer.currency, request: busCartRequest(offer, picked),
    });
    setMsg(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
  }

  // The sticky bar lives outside the scroll view: publish its data and the latest action to the screen.
  const actionRef = useRef(addToCart);
  useEffect(() => { actionRef.current = addToCart; });
  useEffect(() => { register(() => actionRef.current()); return () => register(null); }, [register]);
  const barCount = picked.length;
  useEffect(() => {
    onBar(barCount > 0 ? { amount: total, currency: offer.currency, ready } : null);
  }, [barCount, total, ready, offer.currency, onBar]);
  useEffect(() => () => onBar(null), [onBar]);

  return (
    <AsyncView<SeatMap> state={map} emptyText="Koltuk haritası alınamadı.">
      {(m) => (
        <Card>
          <Legend />
          <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{GENDER_RULE_TEXT}</Text>
          <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>{`En fazla ${MAX_SEATS} koltuk seçebilirsiniz.`}</Text>
          <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>Yeni seçimde cinsiyet:</Text>
          <GenderToggle value={defaultGender} onChange={setDefaultGender} />
          <View style={{ gap: space.sm, alignItems: 'center', paddingVertical: space.sm }}>
            {seatRows(m.layout, m.seats).map((row, i) => (
              <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
                <Text style={[type.monoSm, { width: 20, color: color.onSurfaceVariant, textAlign: 'right' }]}>{i + 1}</Text>
                {row.map((block, k) => (
                  <View key={k} style={{ flexDirection: 'row', gap: space.xs, minWidth: k === 0 ? 2 * SEAT + space.xs : SEAT, marginLeft: k === 1 ? space.xl : 0 }}>
                    {block.map((s) => {
                      const isPicked = picked.some((p) => p.seatNo === s.seatNo);
                      const taken = !s.available;
                      const g = s.occupantGender;
                      const bg = taken ? (g === 'FEMALE' ? FEMALE_BG : g === 'MALE' ? MALE_BG : color.surfaceHigh) : isPicked ? color.primary : color.surfaceLowest;
                      const label = taken
                        ? `Koltuk ${s.seatNo}, dolu${g ? `, ${GENDER_WORD[g]} yolcu` : ''}`
                        : `Koltuk ${s.seatNo}, ${isPicked ? 'seçili' : 'boş'}`;
                      return (
                        <Pressable key={s.seatNo} disabled={taken} onPress={() => toggle(s.seatNo)} accessibilityRole="button"
                          accessibilityLabel={label} accessibilityState={{ selected: isPicked, disabled: taken }}
                          style={{ width: SEAT, height: SEAT, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: bg,
                            borderWidth: conflicts.has(s.seatNo) ? 2 : 1, borderColor: conflicts.has(s.seatNo) ? color.error : taken ? bg : isPicked ? color.primary : color.outline }}>
                          {isPicked
                            ? <MaterialCommunityIcons name="check" size={20} color={color.onPrimary} accessibilityElementsHidden importantForAccessibility="no" />
                            : taken
                              ? <Text style={[type.labelLg, { color: color.onSurface }]}>{g ? GENDER_LETTER[g] : '–'}</Text>
                              : <Text style={[type.monoSm, { color: color.onSurface }]}>{s.seatNo}</Text>}
                        </Pressable>
                      );
                    })}
                  </View>
                ))}
              </View>
            ))}
          </View>
          {picked.length === 0 ? <Muted>Haritadan koltuk seçin.</Muted> : null}
          {picked.map((p) => (
            <View key={p.seatNo} style={{ gap: space.sm, padding: space.md, backgroundColor: color.surfaceLow, borderRadius: radius.lg }}>
              <Text style={[type.titleMd, { color: color.onSurface }]}>{`Koltuk ${p.seatNo}`}</Text>
              <Field label="Ad soyad" value={p.name} onChangeText={(t) => patch(p.seatNo, { name: t })} maxLength={150} />
              <GenderToggle value={p.gender} onChange={(g) => patch(p.seatNo, { gender: g })} />
              {conflicts.has(p.seatNo) ? <Text accessibilityRole="alert" style={[type.bodyMd, { color: color.error }]}>Yan koltukta farklı cinsiyet var</Text> : null}
            </View>
          ))}
          <ErrorBanner message={err} />
          {msg ? <Muted>{msg}</Muted> : null}
        </Card>
      )}
    </AsyncView>
  );
}

function GenderToggle({ value, onChange }: { value: Gender; onChange: (g: Gender) => void }) {
  return (
    <View style={{ flexDirection: 'row', gap: space.sm }}>
      {GENDERS.map((g) => {
        const on = value === g;
        return (
          <Pressable key={g} accessibilityRole="button" accessibilityLabel={GENDER_LABEL[g]} accessibilityState={{ selected: on }} onPress={() => onChange(g)}
            style={{ flex: 1, minHeight: MIN_TOUCH, borderRadius: radius.md, flexDirection: 'row', gap: space.xs, alignItems: 'center', justifyContent: 'center',
              backgroundColor: on ? color.primary : color.surfaceLowest, borderWidth: 1, borderColor: on ? color.primary : color.outline }}>
            {on ? <MaterialCommunityIcons name="check" size={16} color={color.onPrimary} accessibilityElementsHidden importantForAccessibility="no" /> : null}
            <Text style={[type.labelLg, { color: on ? color.onPrimary : color.onSurface }]}>{GENDER_LABEL[g]}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function Bus() {
  const router = useRouter();
  const { user } = useAuth();
  const insets = useSafeAreaInsets();
  const status = useAsync(() => busApi.salesStatus(), []);
  const params = useLocalSearchParams<{ from?: string; to?: string; date?: string; trip?: string }>();
  // State restored after the login detour (see features/returnState.ts). Read once on mount.
  const [restored] = useState(() => parseBusReturn(params));
  const [from, setFrom] = useState(restored?.from ?? '');
  const [to, setTo] = useState(restored?.to ?? '');
  const [date, setDate] = useState(() => (restored ? parseIsoDate(restored.date) : null) ?? addDays(new Date(), 7));
  const [q, setQ] = useState<TripQ | null>(restored ? { from: restored.from, to: restored.to, date: restored.date } : null);
  const [selected, setSelected] = useState<string | null>(restored?.trip ? busTripKey(restored.trip) : null);
  const [bar, setBar] = useState<Bar | null>(null);
  const [action, setAction] = useState<(() => void) | null>(null);
  const onBar = useCallback((b: Bar | null) => setBar((cur) => (cur && b && cur.amount === b.amount && cur.currency === b.currency && cur.ready === b.ready ? cur : b)), []);
  const register = useCallback((fn: (() => void) | null) => setAction(() => fn), []);
  // No `q!.x` inside the closure: the React Compiler (app.json reactCompiler) reads non-null access paths as memo
  // dependencies during render, and q is null until the first search (crashed the tab on Expo web, 27.11).
  const trips = useAsync(
    () => (q ? busApi.search(q.from, q.to, q.date) : Promise.resolve([] as TripOffer[]))
      .catch((e: unknown) => { throw new Error(busErrorMessage(e, 'Seferler yuklenemedi.')); }),
    [q], q !== null && status.data?.enabled === true);
  const showBar = bar !== null && selected !== null;
  return (
    <View style={{ flex: 1, backgroundColor: color.surface }}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Waylo" right={user ? undefined : (
        <Pressable accessibilityRole="button" accessibilityLabel="Giriş yap" onPress={() => { setReturnTo(q ? buildBusReturn({ from: q.from, to: q.to, date: q.date }) : '/bus'); router.navigate('/(auth)/login'); }} style={{ minHeight: MIN_TOUCH, justifyContent: 'center', paddingHorizontal: space.sm }}>
          <Text style={[type.labelLg, { color: color.onPrimary }]}>Giriş yap</Text>
        </Pressable>
      )} />
      <ModeTabs active="bus" />
      <View style={{ flex: 1 }}>
        <Screen>
          <AsyncView<{ enabled: boolean }> state={status} emptyText="Durum alinamadi.">
            {(s) => !s.enabled ? <Muted>Otobus satisi su an kapali.</Muted> : (
              <>
                <Card>
                  <Field label="Nereden (şehir)" value={from} onChangeText={setFrom} autoCapitalize="words" />
                  <Field label="Nereye (şehir)" value={to} onChangeText={setTo} autoCapitalize="words" />
                  <DateField label="Tarih" value={date} minimumDate={new Date()} onChange={setDate} />
                  <Button title="Ara" disabled={!from || !to} onPress={() => { setSelected(null); setQ({ from: from.trim(), to: to.trim(), date: isoDate(date) }); }} />
                </Card>
                {q && (
                  <AsyncView<TripOffer[]> state={trips} isEmpty={(d) => d.length === 0} emptyText="Sefer bulunamadı.">
                    {(list) => list.map((t) => {
                      const key = `${t.tripId}-${t.fromSeq}-${t.toSeq}`;
                      return (
                        <View key={key} style={{ gap: space.sm }}>
                          <Card>
                            {t.companyName ? <Text style={[type.labelLg, { color: color.onSurfaceVariant }]}>{t.companyName}</Text> : null}
                            <Text style={[type.titleMd, { color: color.onSurface }]}>{`${t.fromCity} → ${t.toCity}`}</Text>
                            <Text style={[type.monoMd, { color: color.onSurface }]}>{`${fmtLocal(t.departureAt)} → ${fmtLocal(t.arrivalAt)}`}</Text>
                            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${t.availableSeats} boş koltuk`}</Text>
                              <Price amount={t.price} currency={t.currency} />
                            </View>
                            <Button kind="secondary" title={selected === key ? 'Koltuk seçimini kapat' : 'Koltuk seç'} onPress={() => setSelected(selected === key ? null : key)} />
                          </Card>
                          {selected === key ? <SeatPicker offer={t} search={{ from: q.from, to: q.to, date: q.date }} onBar={onBar} register={register} /> : null}
                        </View>
                      );
                    })}
                  </AsyncView>
                )}
                {showBar ? <View style={{ height: STICKY_ACTION_BAR_HEIGHT + insets.bottom }} /> : null}
              </>
            )}
          </AsyncView>
        </Screen>
      </View>
      {bar && selected !== null ? (
        <StickyActionBar amount={bar.amount} currency={bar.currency} actionLabel="Sepete ekle" disabled={!bar.ready} onAction={() => { if (action) action(); }} />
      ) : null}
    </View>
  );
}

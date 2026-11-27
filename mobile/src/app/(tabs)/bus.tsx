import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
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
import { GuestLoginPill } from '@/components/GuestLoginPill';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { DateField } from '@/components/DateField';
import { addDays, isoDate, parseIsoDate } from '@/features/dates';
import { fmtLocal, money } from '@/features/format';

const GENDERS: Gender[] = ['FEMALE', 'MALE'];

type Bar = { amount: number; currency: string; ready: boolean; count: number };
type TripQ = { from: string; to: string; date: string };

const WEB_FRAME_MAX = 480;
/** Seat edge from the available width: 4 seats + aisle column (32) + gaps inside page (20x2) and card (16x2) paddings, min 44 pt touch target. */
const seatSize = (width: number) => Math.max(MIN_TOUCH, Math.min(52, Math.floor((Math.min(width, WEB_FRAME_MAX) - 72 - 32 - 2 * space.xs) / 4)));
const GENDER_LETTER: Record<Gender, string> = { FEMALE: 'K', MALE: 'E' };
const GENDER_WORD: Record<Gender, string> = { FEMALE: 'kadın', MALE: 'erkek' };
const hhmm = (iso: string) => fmtLocal(iso).slice(-5);
const dmy = (iso: string) => fmtLocal(iso).slice(0, 10);

function LegendItem({ children, label }: { children: React.ReactNode; label: string }) {
  const s = useStyles(makeStyles);
  return (
    <View style={s.legendItem}>
      {children}
      <Text style={s.legendText}>{label}</Text>
    </View>
  );
}

function SeatBox({ bg, fg, border, children }: { bg: string; fg: string; border?: string; children?: React.ReactNode }) {
  const s = useStyles(makeStyles);
  return <View style={[s.legendBox, { backgroundColor: bg, borderColor: border ?? bg }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Text style={[s.legendBoxText, { color: fg }]}>{children}</Text></View>;
}

function Legend() {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View accessible accessibilityLabel="Açıklama: boş, seçili, kadın yolcu K, erkek yolcu E" style={s.legend}>
      <LegendItem label="Boş"><SeatBox bg={color.seatEmptyBg} fg={color.onSurface} border={color.seatEmptyBorder}>1</SeatBox></LegendItem>
      <LegendItem label="Seçili">
        <View style={[s.legendBox, { backgroundColor: color.seatSelectedBg, borderColor: color.seatSelectedBg }]}>
          <MaterialCommunityIcons name="check" size={16} color={color.seatSelectedFg} accessibilityElementsHidden importantForAccessibility="no" />
        </View>
      </LegendItem>
      <LegendItem label="Kadın dolu"><SeatBox bg={color.seatFemaleBg} fg={color.seatFemaleFg}>K</SeatBox></LegendItem>
      <LegendItem label="Erkek dolu"><SeatBox bg={color.seatMaleBg} fg={color.seatMaleFg}>E</SeatBox></LegendItem>
    </View>
  );
}

function SeatPicker({ offer, search, onBar, register }: {
  offer: TripOffer; search: TripQ; onBar: (b: Bar | null) => void; register: (fn: (() => void) | null) => void;
}) {
  const router = useRouter();
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const { user } = useAuth();
  const seat = seatSize(useWindowDimensions().width);
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
    if (picked.length >= MAX_SEATS) { setErr('Tek seferde en fazla 5 koltuk seçilebilir.'); return; }
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
    onBar(barCount > 0 ? { amount: total, currency: offer.currency, ready, count: barCount } : null);
  }, [barCount, total, ready, offer.currency, onBar]);
  useEffect(() => () => onBar(null), [onBar]);

  return (
    <AsyncView<SeatMap> state={map} emptyText="Koltuk haritası alınamadı.">
      {(m) => (
        <>
          <View style={s.card}>
            <Legend />
          </View>
          <View style={s.infoBox}>
            <MaterialCommunityIcons name="information-outline" size={20} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
            <View style={s.infoText}>
              <Text style={s.body}>{GENDER_RULE_TEXT}</Text>
              <Text style={s.label}>{`En fazla ${MAX_SEATS} koltuk seçebilirsiniz.`}</Text>
            </View>
          </View>
          <View style={s.card}>
            <View style={s.mapHeader}>
              <Text style={s.label}>Ön · Kaptan</Text>
              <Text style={s.monoSm}>{`${m.layout === 'TWO_PLUS_TWO' ? '2+2' : '2+1'} · ${m.seats.length} koltuk`}</Text>
            </View>
            <Text style={s.label}>Yeni seçimde cinsiyet:</Text>
            <GenderToggle value={defaultGender} onChange={setDefaultGender} />
            <View style={s.map}>
              {seatRows(m.layout, m.seats).map((row, i) => (
                <View key={i} style={s.mapRow}>
                  {row.map((block, k) => (
                    <View key={k} style={s.block}>
                      {k === 1 ? <Text style={s.rowNo}>{i + 1}</Text> : null}
                      <View style={[s.blockSeats, { minWidth: k === 0 ? 2 * seat + space.xs : seat }]}>
                        {block.map((st) => {
                          const isPicked = picked.some((p) => p.seatNo === st.seatNo);
                          const taken = !st.available;
                          const g = st.occupantGender;
                          const bg = taken ? (g === 'FEMALE' ? color.seatFemaleBg : g === 'MALE' ? color.seatMaleBg : color.surfaceHigh) : isPicked ? color.seatSelectedBg : color.seatEmptyBg;
                          const fg = taken ? (g === 'FEMALE' ? color.seatFemaleFg : g === 'MALE' ? color.seatMaleFg : color.onSurfaceVariant) : isPicked ? color.seatSelectedFg : color.onSurface;
                          const conflict = conflicts.has(st.seatNo);
                          const label = taken
                            ? `Koltuk ${st.seatNo}, dolu${g ? `, ${GENDER_WORD[g]} yolcu` : ''}`
                            : `Koltuk ${st.seatNo}, ${isPicked ? 'seçili' : 'boş'}`;
                          return (
                            <Pressable key={st.seatNo} disabled={taken} onPress={() => toggle(st.seatNo)} accessibilityRole="button"
                              accessibilityLabel={label} accessibilityState={{ selected: isPicked, disabled: taken }}
                              style={[s.seat, { width: seat, height: seat, backgroundColor: bg, borderWidth: conflict ? 2 : 1, borderColor: conflict ? color.error : taken ? bg : isPicked ? color.seatSelectedBg : color.seatEmptyBorder }]}>
                              {isPicked
                                ? <MaterialCommunityIcons name="check" size={20} color={fg} accessibilityElementsHidden importantForAccessibility="no" />
                                : (
                                  <>
                                    <Text style={[s.seatNo, { color: fg }]}>{st.seatNo}</Text>
                                    {taken && g ? <Text style={[s.seatLetter, { color: fg }]}>{GENDER_LETTER[g]}</Text> : null}
                                  </>
                                )}
                            </Pressable>
                          );
                        })}
                      </View>
                    </View>
                  ))}
                </View>
              ))}
            </View>
          </View>
          <Text accessibilityRole="header" style={s.section}>{`Yolcu bilgileri (${picked.length} / ${MAX_SEATS} koltuk)`}</Text>
          {picked.length === 0 ? <Muted>Haritadan koltuk seçin.</Muted> : null}
          {picked.map((p) => (
            <View key={p.seatNo} style={s.card}>
              <View style={s.passHead}>
                <Text style={s.seatTitle}>{`Koltuk ${p.seatNo}`}</Text>
                <Text style={s.monoMd}>{money(offer.price, offer.currency)}</Text>
              </View>
              <Field label="Ad soyad" value={p.name} onChangeText={(t) => patch(p.seatNo, { name: t })} maxLength={150} />
              <GenderToggle value={p.gender} onChange={(g) => patch(p.seatNo, { gender: g })} />
              {conflicts.has(p.seatNo) ? <Text accessibilityRole="alert" style={s.conflict}>Yan koltukta farklı cinsiyet var</Text> : null}
            </View>
          ))}
          <ErrorBanner message={err} />
          {msg ? <Muted>{msg}</Muted> : null}
        </>
      )}
    </AsyncView>
  );
}

function GenderToggle({ value, onChange }: { value: Gender; onChange: (g: Gender) => void }) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View style={s.toggleRow}>
      {GENDERS.map((g) => {
        const on = value === g;
        const bg = g === 'FEMALE' ? color.seatFemaleBg : color.seatMaleBg;
        const fg = g === 'FEMALE' ? color.seatFemaleFg : color.seatMaleFg;
        return (
          <Pressable key={g} accessibilityRole="button" accessibilityLabel={GENDER_LABEL[g]} accessibilityState={{ selected: on }} onPress={() => onChange(g)}
            style={[s.toggle, on ? { backgroundColor: bg, borderColor: fg } : null]}>
            {on ? <MaterialCommunityIcons name="check" size={16} color={fg} accessibilityElementsHidden importantForAccessibility="no" /> : null}
            <Text style={[s.toggleText, { color: on ? fg : color.onSurface }]}>{GENDER_LABEL[g]}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function Bus() {
  const router = useRouter();
  const s = useStyles(makeStyles);
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
      .catch((e: unknown) => { throw new Error(busErrorMessage(e, 'Seferler yüklenemedi.')); }),
    [q], q !== null && status.data?.enabled === true);
  const showBar = bar !== null && selected !== null;
  const list = trips.data ?? [];
  const open = selected !== null && q ? list.find((t) => `${t.tripId}-${t.fromSeq}-${t.toSeq}` === selected) : undefined;
  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      {open && q ? (
        <AppHeader title={`${open.fromCity} → ${open.toCity}`}
          subtitle={`${open.companyName ? `${open.companyName} · ` : ''}${hhmm(open.departureAt)} - ${hhmm(open.arrivalAt)} · ${money(open.price, open.currency)}`}
          onBack={() => setSelected(null)} />
      ) : (
        <>
          <AppHeader title="Waylo" right={user ? undefined : <GuestLoginPill onPress={() => { setReturnTo(q ? buildBusReturn({ from: q.from, to: q.to, date: q.date }) : '/bus'); router.navigate('/(auth)/login'); }} />} />
          <ModeTabs active="bus" />
        </>
      )}
      <View style={{ flex: 1 }}>
        <Screen>
          {open && q ? (
            <SeatPicker offer={open} search={{ from: q.from, to: q.to, date: q.date }} onBar={onBar} register={register} />
          ) : (
          <AsyncView<{ enabled: boolean }> state={status} emptyText="Durum alınamadı.">
            {(st) => !st.enabled ? <Muted>Otobüs satışı şu an kapalı.</Muted> : (
              <>
                <Card>
                  <Field label="Nereden (şehir)" value={from} onChangeText={setFrom} autoCapitalize="words" />
                  <Field label="Nereye (şehir)" value={to} onChangeText={setTo} autoCapitalize="words" />
                  <DateField label="Tarih" value={date} minimumDate={new Date()} onChange={setDate} />
                  <Button title="Ara" disabled={!from || !to} onPress={() => { setSelected(null); setQ({ from: from.trim(), to: to.trim(), date: isoDate(date) }); }} />
                </Card>
                {q && (
                  <AsyncView<TripOffer[]> state={trips} isEmpty={(d) => d.length === 0} emptyText="Sefer bulunamadı.">
                    {(items) => items.map((t) => {
                      const key = `${t.tripId}-${t.fromSeq}-${t.toSeq}`;
                      return (
                        <Card key={key}>
                          <View style={s.rowBetween}>
                            <Text style={s.title}>{t.companyName ?? `${t.fromCity} → ${t.toCity}`}</Text>
                            <Price amount={t.price} currency={t.currency} />
                          </View>
                          <Text style={s.mono}>{`${hhmm(t.departureAt)} → ${hhmm(t.arrivalAt)}`}<Text style={s.muted}>{`  ${dmy(t.departureAt)}`}</Text></Text>
                          {t.companyName ? <Text style={s.body}>{`${t.fromCity} → ${t.toCity}`}</Text> : null}
                          <Text style={s.muted}>{`${t.availableSeats} boş koltuk`}</Text>
                          <Button kind="secondary" title="Koltuk seç" onPress={() => setSelected(key)} />
                        </Card>
                      );
                    })}
                  </AsyncView>
                )}
              </>
            )}
          </AsyncView>
          )}
          {showBar ? <View style={{ height: STICKY_ACTION_BAR_HEIGHT + insets.bottom }} /> : null}
        </Screen>
      </View>
      {bar && selected !== null ? (
        <StickyActionBar totalLabel={`Toplam (${bar.count} koltuk)`} amount={bar.amount} currency={bar.currency} actionLabel="Sepete ekle" disabled={!bar.ready} onAction={() => { if (action) action(); }} />
      ) : null}
    </View>
  );
}

const makeStyles = ({ color, type, radius, card }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space.sm },
  title: { ...type.titleMd, color: color.onSurface, flex: 1 },
  mono: { ...type.monoMd, color: color.onSurface },
  monoMd: { ...type.monoMd, color: color.onSurface },
  monoSm: { ...type.monoSm, color: color.onSurfaceVariant },
  muted: { ...type.bodyMd, color: color.onSurfaceVariant },
  body: { ...type.bodyMd, color: color.onSurface },
  label: { ...type.labelSm, color: color.onSurfaceVariant },
  section: { ...type.titleMd, color: color.onSurface },
  card: { ...card, borderRadius: radius.lg, padding: space.lg, gap: space.md },
  legend: { flexDirection: 'row', flexWrap: 'wrap', rowGap: space.md },
  legendItem: { width: '50%', flexDirection: 'row', alignItems: 'center', gap: space.sm },
  legendText: { ...type.labelSm, color: color.onSurface },
  legendBox: { width: 28, height: 28, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  legendBoxText: { ...type.monoSm },
  infoBox: { flexDirection: 'row', gap: space.md, padding: space.md, borderRadius: radius.md, backgroundColor: color.surfaceContainer },
  infoText: { flex: 1, gap: space.xs },
  mapHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  map: { gap: space.sm, alignItems: 'center', paddingVertical: space.sm },
  mapRow: { flexDirection: 'row', alignItems: 'center' },
  block: { flexDirection: 'row', alignItems: 'center' },
  blockSeats: { flexDirection: 'row', gap: space.xs },
  rowNo: { ...type.monoSm, color: color.onSurfaceVariant, width: 32, textAlign: 'center' },
  seat: { borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center' },
  seatNo: { ...type.monoSm },
  seatLetter: { ...type.labelSm, fontSize: 10, lineHeight: 12 },
  passHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  seatTitle: { ...type.titleMd, color: color.primary },
  conflict: { ...type.bodyMd, color: color.error },
  toggleRow: { flexDirection: 'row', gap: space.sm },
  toggle: { flex: 1, minHeight: MIN_TOUCH, borderRadius: radius.md, flexDirection: 'row', gap: space.xs, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surfaceLowest, borderWidth: 1, borderColor: color.outlineVariant },
  toggleText: { ...type.labelLg },
});

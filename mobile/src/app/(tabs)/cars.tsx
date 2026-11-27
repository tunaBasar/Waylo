import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import type { Branch, CarOffer } from '@/api';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { carsApi } from '@/features/cars/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { ModeTabs } from '@/components/ModeTabs';
import { Price } from '@/components/Price';
import { GuestLoginPill } from '@/components/GuestLoginPill';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { DateField } from '@/components/DateField';
import { addDays, isoLocalDateTime, validateRentalTimes } from '@/features/dates';
import { carErrorMessage, defaultPickup, PICKUP_LEAD_HINT, pickupLeadError } from '@/features/cars/lead';
import { buildCarsReturn, parseCarsReturn } from '@/features/returnState';

type Q = { pickupBranchId: string; returnBranchId: string; pickupAt: string; returnAt: string; driverAge: number };

export default function Cars() {
  const cart = useCart();
  const s = useStyles(makeStyles);
  const router = useRouter();
  const { user } = useAuth();
  const branches = useAsync(() => carsApi.branches(), []);
  const params = useLocalSearchParams<{ pickup?: string; ret?: string; pickupAt?: string; returnAt?: string; age?: string }>();
  // State restored after the login detour (see features/returnState.ts). Read once on mount.
  const [restored] = useState(() => parseCarsReturn(params));
  const [pickup, setPickup] = useState<string | null>(restored?.pickupBranchId ?? null);
  const [ret, setRet] = useState<string | null>(restored?.returnBranchId ?? null);
  const [pAt, setPAt] = useState(() => restored?.pickupAt ?? defaultPickup(new Date()));
  const [rAt, setRAt] = useState(() => restored?.returnAt ?? (restored?.pickupAt ? addDays(restored.pickupAt, 1) : (() => { const d = addDays(new Date(), 10); d.setHours(10, 0, 0, 0); return d; })()));
  const [age, setAge] = useState(restored?.driverAge ? String(restored.driverAge) : '30');
  const [q, setQ] = useState<Q | null>(() => (restored?.pickupAt && restored.returnAt && restored.driverAge
    ? { pickupBranchId: restored.pickupBranchId, returnBranchId: restored.returnBranchId, pickupAt: isoLocalDateTime(restored.pickupAt), returnAt: isoLocalDateTime(restored.returnAt), driverAge: restored.driverAge } : null));
  const [msg, setMsg] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const offers = useAsync(() => (q ? carsApi.offers(q) : Promise.resolve([] as CarOffer[])).catch((e: unknown) => { throw new Error(carErrorMessage(e)); }), [q], q !== null);
  const returnId = ret ?? pickup;

  function search() {
    const err = pickupLeadError(pAt, Date.now()) ?? validateRentalTimes(pAt, rAt);
    const ageN = parseInt(age, 10) || 0;
    setFormError(err ?? (ageN < 18 ? 'Sürücü yaşı en az 18 olmalı.' : null));
    if (err || ageN < 18) return;
    if (!pickup || !returnId) return;
    setQ({ pickupBranchId: pickup, returnBranchId: returnId, pickupAt: isoLocalDateTime(pAt), returnAt: isoLocalDateTime(rAt), driverAge: ageN });
  }

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Waylo" right={user ? undefined : <GuestLoginPill onPress={() => { setReturnTo(q ? buildCarsReturn({ pickupBranchId: q.pickupBranchId, returnBranchId: q.returnBranchId, pickupAt: new Date(q.pickupAt), returnAt: new Date(q.returnAt), driverAge: q.driverAge }) : '/cars'); router.navigate('/(auth)/login'); }} />} />
      <ModeTabs active="cars" />
      <Screen>
        <Card>
          <AsyncView<Branch[]> state={branches} isEmpty={(d) => d.length === 0} emptyText="Şu an kiralama şubesi yok.">
            {(list) => (
              <>
                <Text style={s.label}>Alış şubesi</Text>
                {list.map((b) => <BranchRow key={b.id} b={b} on={pickup === b.id} onPress={() => setPickup(b.id)} />)}
                <Text style={s.label}>Teslim şubesi</Text>
                <Muted>Farklı şube seçerseniz tek yön kiralama olur, ek ücret olabilir.</Muted>
                {list.map((b) => <BranchRow key={`r-${b.id}`} b={b} on={returnId === b.id} onPress={() => setRet(b.id)} />)}
              </>
            )}
          </AsyncView>
          <DateField label="Alış" mode="datetime" value={pAt} minimumDate={new Date()} onChange={(d) => { setPAt(d); if (rAt <= d) setRAt(addDays(d, 1)); }} />
          <DateField label="Teslim" mode="datetime" value={rAt} minimumDate={pAt} onChange={setRAt} />
          {formError ? null : <Text style={s.hint}>{PICKUP_LEAD_HINT}</Text>}
          <Field label="Sürücü yaşı" value={age} onChangeText={setAge} keyboardType="number-pad" />
          <ErrorBanner message={formError} />
          <Button title="Ara" disabled={!pickup || !returnId} onPress={search} />
        </Card>
        {q && (
          <AsyncView<CarOffer[]> state={offers} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde araç bulunamadı.">
            {(list) => list.map((o, i) => (
              <Card key={`${o.category}-${o.transmission}-${i}`}>
                <Text style={s.title}>{`${o.exampleModel} ve benzeri`}</Text>
                <Text style={s.muted}>{`${o.category} · ${o.transmission} · ${o.fuelType} · ${o.seatCount} koltuk`}</Text>
                <View style={s.rowBetween}>
                  <Text style={s.muted}>{`${o.chargeableDays} gün × `}</Text>
                  <Price amount={o.dailyRate} currency={o.currency} size="sm" />
                </View>
                <View style={s.rowBetween}>
                  <Text style={s.muted}>Toplam</Text>
                  <Price amount={o.totalAmount} currency={o.currency} />
                </View>
                <View style={s.rowBetween}>
                  <Text style={s.muted}>Depozito</Text>
                  <Price amount={o.depositAmount} currency={o.currency} size="sm" />
                </View>
                <Button kind="secondary" title="Sepete ekle" onPress={() => {
                  if (!user) { setReturnTo(buildCarsReturn({ pickupBranchId: q.pickupBranchId, returnBranchId: q.returnBranchId, pickupAt: new Date(q.pickupAt), returnAt: new Date(q.returnAt), driverAge: q.driverAge })); router.navigate('/(auth)/login'); return; }
                  const ok = cart.add({
                    vertical: 'CAR', label: `${o.exampleModel}, ${q.pickupAt.slice(0, 16)}`, amount: o.totalAmount, currency: o.currency,
                    request: { pickupBranchId: o.pickupBranchId, returnBranchId: o.returnBranchId, category: o.category, transmission: o.transmission, pickupAt: q.pickupAt, returnAt: q.returnAt, driverAge: q.driverAge },
                  });
                  setMsg(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
                }} />
              </Card>
            ))}
          </AsyncView>
        )}
        {msg ? <Muted>{msg}</Muted> : null}
      </Screen>
    </View>
  );
}

function BranchRow({ b, on, onPress }: { b: Branch; on: boolean; onPress: () => void }) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${b.name}, ${b.city}`} accessibilityState={{ selected: on }} onPress={onPress}
      style={[s.branch, on && s.branchOn]}>
      <MaterialCommunityIcons name={on ? 'radiobox-marked' : 'radiobox-blank'} size={20} color={on ? color.primary : color.outline} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={s.branchText}>{`${b.name} · ${b.city}`}</Text>
    </Pressable>
  );
}

const makeStyles = ({ color, type, radius }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  label: { ...type.labelSm, color: color.onSurfaceVariant },
  hint: { ...type.labelSm, color: color.onSurfaceVariant },
  title: { ...type.titleMd, color: color.onSurface },
  muted: { ...type.bodyMd, color: color.onSurfaceVariant },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  branch: { minHeight: MIN_TOUCH, flexDirection: 'row', alignItems: 'center', gap: space.sm, paddingHorizontal: space.md, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant, backgroundColor: color.surfaceLowest },
  branchOn: { borderWidth: 2, borderColor: color.primary, backgroundColor: color.surfaceLow },
  branchText: { ...type.bodyLg, color: color.onSurface, flex: 1 },
});

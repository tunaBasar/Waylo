import { useState } from 'react';
import { Text } from 'react-native';
import { useRouter } from 'expo-router';
import type { Branch, CarOffer } from '@/api';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { carsApi } from '@/features/cars/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { DateField } from '@/components/DateField';
import { addDays, isoLocalDateTime, validateRentalTimes } from '@/features/dates';
import { money } from '@/features/format';

type Q = { pickupBranchId: string; returnBranchId: string; pickupAt: string; returnAt: string; driverAge: number };

export default function Cars() {
  const cart = useCart();
  const router = useRouter();
  const { user } = useAuth();
  const branches = useAsync(() => carsApi.branches(), []);
  const [pickup, setPickup] = useState<string | null>(null);
  const [ret, setRet] = useState<string | null>(null);
  const [pAt, setPAt] = useState(() => { const d = addDays(new Date(), 7); d.setHours(10, 0, 0, 0); return d; });
  const [rAt, setRAt] = useState(() => { const d = addDays(new Date(), 10); d.setHours(10, 0, 0, 0); return d; });
  const [age, setAge] = useState('30');
  const [q, setQ] = useState<Q | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const offers = useAsync(() => carsApi.offers(q!), [q], q !== null);
  const returnId = ret ?? pickup;

  function search() {
    const err = validateRentalTimes(pAt, rAt);
    const ageN = parseInt(age, 10) || 0;
    setFormError(err ?? (ageN < 18 ? 'Surucu yasi en az 18 olmali.' : null));
    if (err || ageN < 18) return;
    setQ({ pickupBranchId: pickup!, returnBranchId: returnId!, pickupAt: isoLocalDateTime(pAt), returnAt: isoLocalDateTime(rAt), driverAge: ageN });
  }

  return (
    <Screen>
      <Title>Arac kirala</Title>
      <AsyncView<Branch[]> state={branches} isEmpty={(d) => d.length === 0} emptyText="Su an kiralama subesi yok.">
        {(list) => (
          <>
            <Muted>Alis subesi</Muted>
            {list.map((b) => (
              <Button key={b.id} kind={pickup === b.id ? 'primary' : 'secondary'} title={`${b.name} · ${b.city}`} onPress={() => setPickup(b.id)} />
            ))}
            <Muted>Teslim subesi (farkli sube secerseniz tek yon kiralama, ek ucret olabilir)</Muted>
            {list.map((b) => (
              <Button key={`r-${b.id}`} kind={returnId === b.id ? 'primary' : 'secondary'} title={`${b.name} · ${b.city}`} onPress={() => setRet(b.id)} />
            ))}
          </>
        )}
      </AsyncView>
      <DateField label="Alis" mode="datetime" value={pAt} minimumDate={new Date()} onChange={(d) => { setPAt(d); if (rAt <= d) setRAt(addDays(d, 1)); }} />
      <DateField label="Teslim" mode="datetime" value={rAt} minimumDate={pAt} onChange={setRAt} />
      <Field label="Surucu yasi" value={age} onChangeText={setAge} keyboardType="number-pad" />
      <ErrorBanner message={formError} />
      <Button title="Ara" disabled={!pickup || !returnId} onPress={search} />
      {q && (
        <AsyncView<CarOffer[]> state={offers} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde arac bulunamadi.">
          {(list) => list.map((o, i) => (
            <Card key={`${o.category}-${o.transmission}-${i}`}>
              <Text style={{ fontWeight: '700' }}>{o.exampleModel}</Text>
              <Muted>{`${o.category} · ${o.transmission} · ${o.seatCount} koltuk · ${o.chargeableDays} gun`}</Muted>
              <Text style={{ fontWeight: '700' }}>{money(o.totalAmount, o.currency)}</Text>
              <Button kind="secondary" title="Sepete ekle" onPress={() => {
                if (!user) { setReturnTo('/cars'); router.push('/(auth)/login'); return; }
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
  );
}

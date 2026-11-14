import { useState } from 'react';
import { Text } from 'react-native';
import type { Branch, CarOffer } from '@/api';
import { carsApi } from '@/features/cars/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, Field, Muted, Screen, Title } from '@/components/ui';
import { money } from '@/features/format';

type Q = { pickupBranchId: string; returnBranchId: string; pickupAt: string; returnAt: string; driverAge: number };

export default function Cars() {
  const cart = useCart();
  const branches = useAsync(() => carsApi.branches(), []);
  const [pickup, setPickup] = useState<string | null>(null);
  const [pDate, setPDate] = useState('');
  const [rDate, setRDate] = useState('');
  const [age, setAge] = useState('30');
  const [q, setQ] = useState<Q | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const offers = useAsync(() => carsApi.offers(q!), [q], q !== null);

  return (
    <Screen>
      <Title>Arac kirala</Title>
      <AsyncView<Branch[]> state={branches} isEmpty={(d) => d.length === 0} emptyText="Su an kiralama subesi yok.">
        {(list) => (
          <>
            <Muted>Sube (alis ve teslim ayni)</Muted>
            {list.map((b) => (
              <Button key={b.id} kind={pickup === b.id ? 'primary' : 'secondary'} title={`${b.name} · ${b.city}`} onPress={() => setPickup(b.id)} />
            ))}
          </>
        )}
      </AsyncView>
      <Field label="Alis (YYYY-AA-GGTSS:DD)" value={pDate} onChangeText={setPDate} placeholder="2026-12-01T10:00" />
      <Field label="Teslim (YYYY-AA-GGTSS:DD)" value={rDate} onChangeText={setRDate} placeholder="2026-12-04T10:00" />
      <Field label="Surucu yasi" value={age} onChangeText={setAge} keyboardType="number-pad" />
      <Button title="Ara" disabled={!pickup || !pDate || !rDate} onPress={() => setQ({
        pickupBranchId: pickup!, returnBranchId: pickup!, pickupAt: `${pDate}:00`, returnAt: `${rDate}:00`, driverAge: parseInt(age, 10) || 0,
      })} />
      {q && (
        <AsyncView<CarOffer[]> state={offers} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde arac bulunamadi.">
          {(list) => list.map((o, i) => (
            <Card key={`${o.category}-${o.transmission}-${i}`}>
              <Text style={{ fontWeight: '700' }}>{o.exampleModel}</Text>
              <Muted>{`${o.category} · ${o.transmission} · ${o.seatCount} koltuk · ${o.chargeableDays} gun`}</Muted>
              <Text style={{ fontWeight: '700' }}>{money(o.totalAmount, o.currency)}</Text>
              <Button kind="secondary" title="Sepete ekle" onPress={() => {
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

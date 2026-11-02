'use client';

/** Paid and cancelled tickets of a BUS partner org, with the passenger list per booking. */
import { useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  partnerBusApi, BOOKING_STATUS_LABEL, GENDER_LABEL, formatMoney, formatTripTime,
  type BusBooking, type Trip,
} from '@/lib/bus';
import { Card, EmptyState, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

export default function BusBookingsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bookings, setBookings] = useState<BusBooking[]>([]);
  const [trips, setTrips] = useState<Trip[]>([]);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([partnerBusApi.bookings(organizationId), partnerBusApi.trips(organizationId)])
      .then(([b, t]) => { if (alive) { setBookings(b.data ?? []); setTrips(t.data ?? []); setError(null); } })
      .catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId]);

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Biletler" subtitle="Satilan biletler ve yolcu listesi. Biniste QR Okut ekranini kullanin." />
      {bookings.length === 0 ? (
        <EmptyState icon="confirmation_number" title="Henuz bilet yok" description="Odemesi tamamlanan biletler burada gorunur." />
      ) : (
        <div className="space-y-3">
          {bookings.map(b => {
            const trip = trips.find(t => t.id === b.tripId);
            const from = trip?.stops.find(s => s.seq === b.fromSeq);
            const to = trip?.stops.find(s => s.seq === b.toSeq);
            return (
              <Card key={b.id} className="p-4 space-y-2">
                <div className="flex flex-wrap items-center gap-3">
                  <p className="font-semibold text-on-surface flex-1 min-w-0">
                    {trip ? `${trip.code}: ` : ''}{from?.city ?? '?'} - {to?.city ?? '?'}
                    <span className="text-on-surface-variant font-normal"> {formatTripTime(b.boardingAt)}</span>
                  </p>
                  <span className="text-sm text-on-surface">{formatMoney(b.totalAmount, b.currency)}</span>
                  <StatusPill tone={b.status === 'CONFIRMED' ? 'success' : 'neutral'}>{BOOKING_STATUS_LABEL[b.status]}</StatusPill>
                </div>
                <ul className="text-sm text-on-surface-variant grid gap-1 sm:grid-cols-2">
                  {b.tickets.map(t => (
                    <li key={t.id}>Koltuk {t.seatNo}: {t.passengerName} ({GENDER_LABEL[t.gender]})</li>
                  ))}
                </ul>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

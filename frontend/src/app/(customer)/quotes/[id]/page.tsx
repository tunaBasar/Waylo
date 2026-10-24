'use client';

import { Suspense, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { api, ApiError, ADMIN_CANNOT_PURCHASE_MESSAGE } from '@/lib/api';
import type { Hotel } from '@/lib/types';
import {
  hotelStayApi,
  stayQueryString,
} from '@/lib/hotel-stay';
import type { Quote } from '@/lib/hotel-stay';
import { formatCurrency } from '@/lib/utils';
import { MEAL_PLAN_LABEL, CANCELLATION_POLICY_LABEL } from '@/lib/partner';
import { useAuth } from '@/lib/auth';

function ErrorState({ message }: { message: string }) {
  return (
    <div className="text-center py-16">
      <span className="material-symbols-outlined text-on-surface-variant text-5xl mb-4 block">error</span>
      <p className="text-on-surface-variant text-sm">{message}</p>
      <Link href="/hotels" className="inline-flex items-center gap-1 text-primary text-sm font-medium mt-4 hover:underline">
        <span className="material-symbols-outlined text-[18px]">arrow_back</span> Otellere don
      </Link>
    </div>
  );
}

function LoadingState() {
  return (
    <div className="text-center py-16">
      <div className="w-12 h-12 border-3 border-primary/30 border-t-primary rounded-full animate-spin mx-auto mb-4" />
      <p className="text-on-surface-variant text-sm">Yukleniyor...</p>
    </div>
  );
}

function Countdown({ expiresAt, onExpire }: { expiresAt: string; onExpire: () => void }) {
  const [remaining, setRemaining] = useState(0);

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const now = Date.now();
    const target = new Date(expiresAt).getTime();
    const diff = Math.max(0, Math.floor((target - now) / 1000));
    setRemaining(diff);

    const interval = setInterval(() => {
      const d = Math.max(0, Math.floor((target - Date.now()) / 1000));
      setRemaining(d);
      if (d <= 0) {
        clearInterval(interval);
        onExpire();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const mins = Math.floor(remaining / 60);
  const secs = remaining % 60;

  return (
    <span className="font-mono text-base font-semibold text-error">
      {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
    </span>
  );
}

function QuoteExpired({ quote }: { quote: Quote }) {
  return (
    <div className="text-center py-8">
      <span className="material-symbols-outlined text-error text-5xl mb-4 block">hourglass_disabled</span>
      <h3 className="text-lg font-semibold text-on-surface mb-2">Teklifin suresi doldu</h3>
      <p className="text-sm text-on-surface-variant mb-4">
        Bu teklif icin yeni fiyatlar gecerli olmayabilir.
      </p>
      <Link
        href={`/hotels/${quote.hotelId}?${stayQueryString({
          checkIn: quote.checkIn,
          checkOut: quote.checkOut,
          rooms: quote.rooms,
          adults: quote.adults,
          childrenAges: quote.childrenAges,
        })}`}
        className="inline-flex items-center gap-1 text-primary text-sm font-medium hover:underline"
      >
        <span className="material-symbols-outlined text-[18px]">search</span>
        Tekrar ara
      </Link>
    </div>
  );
}

function QuoteContent() {
  const params = useParams();
  const id = params?.id as string;
  const router = useRouter();
  const { user } = useAuth();

  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [isExpired, setIsExpired] = useState(false);
  const [hotelName, setHotelName] = useState<string | null>(null);
  const [hotelLoading, setHotelLoading] = useState(false);
  const [booking, setBooking] = useState(false);
  const [bookingError, setBookingError] = useState<string | null>(null);

  // Load quote
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!id) return;
    setQuoteLoading(true);
    setQuoteError(null);
    hotelStayApi.getQuote(id).then(res => {
      setQuote(res.data);
      setQuoteLoading(false);
    }).catch((err: unknown) => {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403 || err.status === 404)) {
        setQuoteError('Teklif bulunamadi');
      } else {
        setQuoteError('Teklif yuklenirken bir hata olustu');
      }
      setQuoteLoading(false);
    });
  }, [id]);
  /* eslint-enable react-hooks/set-state-in-effect */

  // Load hotel name for the quote
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!quote?.hotelId) return;
    setHotelLoading(true);
    api.get<Hotel>(`/api/hotels/${quote.hotelId}`).then(res => {
      setHotelName(res.data.name);
    }).catch(() => {
      // Ignore failure - show hotel ID fallback
    }).finally(() => {
      setHotelLoading(false);
    });
  }, [quote?.hotelId]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const bookQuote = async () => {
    if (!user || !quote) return;
    setBooking(true);
    setBookingError(null);
    try {
      const response = await api.post<{ id: string }>('/api/orders/hotel', {
        quoteId: quote.quoteId,
        notes: null,
      });
      router.push(`/orders?created=${response.data.id}`);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'ADMIN_CANNOT_PURCHASE') setBookingError(ADMIN_CANNOT_PURCHASE_MESSAGE);
        else if (error.status === 404) setBookingError('Teklif bulunamadi');
        else if (error.status === 409) setBookingError('Secilen oda icin musaitlik kalmadi veya teklif baska bir islemde kullaniliyor');
        else if (error.status === 422) setBookingError('Teklifin suresi dolmus olabilir. Lutfen yeniden arayin');
        else if (error.status === 503) setBookingError('Otel servisi su an yanit vermiyor, lutfen biraz sonra tekrar deneyin');
        else setBookingError('Rezervasyon olusturulamadi');
      } else {
        setBookingError('Rezervasyon olusturulamadi');
      }
    } finally {
      setBooking(false);
    }
  };


  if (quoteLoading) return <LoadingState />;
  if (quoteError || !quote) return <ErrorState message={quoteError ?? 'Teklif bulunamadi'} />;

  return (
    <div className="space-y-space-md">
      {/* Back link */}
      <Link href="/hotels" className="inline-flex items-center gap-1 text-primary text-sm font-medium hover:underline">
        <span className="material-symbols-outlined text-[18px]">arrow_back</span> Otellere don
      </Link>

      {/* Quote header */}
      <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-on-surface">
            {hotelLoading ? 'Yukleniyor...' : (hotelName || `Otel #${quote.hotelId}`)}
          </h2>
          <span className="text-xs text-on-surface-variant">Teklif #{quote.quoteId}</span>
        </div>

        {/* Quote status */}
        <div className="flex items-center gap-3 mb-4">
          {quote.expired || isExpired ? (
            <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-error-container text-error text-xs font-semibold">
              <span className="material-symbols-outlined text-[14px]">hourglass_disabled</span>
              Suresi doldu
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-tertiary-container text-on-tertiary-container text-xs font-semibold">
              <span className="material-symbols-outlined text-[14px]">schedule</span>
              Sure: <Countdown expiresAt={quote.expiresAt} onExpire={() => setIsExpired(true)} />
            </span>
          )}
        </div>

        {/* Room & rate plan */}
        <div className="mb-4">
          <h3 className="text-sm font-semibold text-on-surface">{quote.roomTypeName}</h3>
          <p className="text-xs text-on-surface-variant">{quote.ratePlanName}</p>
        </div>

        {/* Meal plan & cancellation */}
        <div className="flex flex-wrap gap-3 mb-4 text-xs text-on-surface-variant">
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">restaurant</span>
            {MEAL_PLAN_LABEL[quote.mealPlan] ?? ''}
          </span>
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">cancel</span>
            {(() => {
              if (quote.cancellationPolicy === 'FREE_CANCELLATION' && quote.freeCancellationDaysBefore != null) {
                return `Giristen ${quote.freeCancellationDaysBefore} gun oncesine kadar ucretsiz iptal`;
              }
              if (quote.cancellationPolicy === 'NON_REFUNDABLE') {
                return 'Iade edilemez';
              }
              return CANCELLATION_POLICY_LABEL[quote.cancellationPolicy] ?? '';
            })()}
          </span>
        </div>

        {/* Dates & guests */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4 text-sm">
          <div className="bg-surface-container-lowest rounded-lg p-3 text-center">
            <span className="block text-xs text-on-surface-variant mb-1">Giris</span>
            <span className="font-semibold text-on-surface">{quote.checkIn}</span>
          </div>
          <div className="bg-surface-container-lowest rounded-lg p-3 text-center">
            <span className="block text-xs text-on-surface-variant mb-1">Cikis</span>
            <span className="font-semibold text-on-surface">{quote.checkOut}</span>
          </div>
          <div className="bg-surface-container-lowest rounded-lg p-3 text-center">
            <span className="block text-xs text-on-surface-variant mb-1">Gece</span>
            <span className="font-semibold text-on-surface">{quote.nights}</span>
          </div>
          <div className="bg-surface-container-lowest rounded-lg p-3 text-center">
            <span className="block text-xs text-on-surface-variant mb-1">Misafir</span>
            <span className="font-semibold text-on-surface">{quote.rooms} oda, {quote.adults} yetiskin</span>
          </div>
        </div>

        {/* Children if any */}
        {quote.childrenAges.length > 0 && (
          <div className="mb-4">
            <span className="text-xs text-on-surface-variant">Cocuk yaslari: {quote.childrenAges.map(a => a).join(', ')}</span>
          </div>
        )}

        {/* Nightly breakdown */}
        {quote.nightly.length > 0 && (
          <div className="mb-4 p-3 rounded-lg bg-surface-container-lowest">
            <h4 className="text-xs font-semibold text-on-surface mb-2">Gecelik fiyatlari</h4>
            <div className="space-y-1">
              {quote.nightly.map((night, i) => (
                <div key={i} className="flex justify-between text-xs text-on-surface-variant">
                  <span>{night.stayDate}</span>
                  <span>{formatCurrency(night.amount, night.currency)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Total */}
        <div className="flex items-center justify-between p-3 rounded-lg bg-primary-container">
          <span className="text-sm font-semibold text-on-primary-container">Toplam</span>
          <span className="text-lg font-bold text-on-primary-container">
            {formatCurrency(quote.totalAmount, quote.currency)}
          </span>
        </div>
      </div>

      {/* Not expired section */}
      {!quote.expired && !isExpired && (
        <div className="text-center py-4">
          {bookingError && <p className="text-sm text-error mb-3">{bookingError}</p>}
          <button onClick={bookQuote} disabled={booking || !user} className="px-5 py-2.5 rounded-xl bg-primary text-on-primary text-sm font-medium disabled:opacity-50">
            {booking ? 'Rezervasyon olusturuluyor...' : 'Rezervasyon yap'}
          </button>
        </div>
      )}

      {/* Expired section */}
      {(quote.expired || isExpired) && <QuoteExpired quote={quote} />}
    </div>
  );
}

export default function QuoteDetailPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <QuoteContent />
    </Suspense>
  );
}

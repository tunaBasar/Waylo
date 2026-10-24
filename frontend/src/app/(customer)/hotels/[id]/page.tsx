'use client';

import { Suspense, useCallback, useEffect, useState } from 'react';
import { useRouter, useSearchParams, useParams } from 'next/navigation';
import Link from 'next/link';
import { api, ApiError, ADMIN_CANNOT_PURCHASE_MESSAGE } from '@/lib/api';
import type { Hotel } from '@/lib/types';
import {
  todayIso, addDaysIso, validateStay, stayQueryString, parseStayQuery,
  hotelStayApi,
} from '@/lib/hotel-stay';
import type { StayQuery, AvailableOffer } from '@/lib/hotel-stay';
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

interface StayFormProps {
  initial: StayQuery;
  onValidSubmit: (q: StayQuery) => void;
}

function StayForm({ initial, onValidSubmit }: StayFormProps) {
  const [checkIn, setCheckIn] = useState(initial.checkIn);
  const [checkOut, setCheckOut] = useState(initial.checkOut);
  const [rooms, setRooms] = useState(String(initial.rooms));
  const [adults, setAdults] = useState(String(initial.adults));
  const [childrenAges, setChildrenAges] = useState<number[]>(initial.childrenAges);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = useCallback(() => {
    setError(null);
    const q: StayQuery = {
      checkIn,
      checkOut,
      rooms: parseInt(rooms, 10),
      adults: parseInt(adults, 10),
      childrenAges,
    };
    const err = validateStay(q);
    if (err) {
      setError(err);
      return;
    }
    onValidSubmit(q);
  }, [checkIn, checkOut, rooms, adults, childrenAges, onValidSubmit]);

  const addChild = () => {
    setChildrenAges(prev => [...prev, 0]);
  };

  const removeChild = (index: number) => {
    setChildrenAges(prev => prev.filter((_, i) => i !== index));
  };

  const setChildAge = (index: number, age: number) => {
    setChildrenAges(prev => prev.map((a, i) => (i === index ? age : a)));
  };

  return (
    <div className="bg-surface-container-lowest rounded-xl p-4 shadow-sm">
      <div className="flex items-center gap-2 mb-4">
        <span className="material-symbols-outlined text-primary text-[20px]">calendar_month</span>
        <h3 className="text-sm font-semibold text-on-surface">Konaklama Aramasi</h3>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-3">
        <div>
          <label className="block text-xs text-on-surface-variant mb-1">Giris</label>
          <input
            type="date"
            value={checkIn}
            onChange={e => setCheckIn(e.target.value)}
            className="w-full bg-surface-container-lowest text-on-surface text-sm rounded-lg px-3 py-2 outline-none shadow-sm border border-outline/30 focus:border-primary transition-colors"
          />
        </div>
        <div>
          <label className="block text-xs text-on-surface-variant mb-1">Cikis</label>
          <input
            type="date"
            value={checkOut}
            onChange={e => setCheckOut(e.target.value)}
            className="w-full bg-surface-container-lowest text-on-surface text-sm rounded-lg px-3 py-2 outline-none shadow-sm border border-outline/30 focus:border-primary transition-colors"
          />
        </div>
        <div>
          <label className="block text-xs text-on-surface-variant mb-1">Oda</label>
          <input
            type="number"
            min={1}
            max={10}
            value={rooms}
            onChange={e => setRooms(e.target.value)}
            className="w-full bg-surface-container-lowest text-on-surface text-sm rounded-lg px-3 py-2 outline-none shadow-sm border border-outline/30 focus:border-primary transition-colors"
          />
        </div>
        <div>
          <label className="block text-xs text-on-surface-variant mb-1">Yetiskin</label>
          <input
            type="number"
            min={1}
            max={20}
            value={adults}
            onChange={e => setAdults(e.target.value)}
            className="w-full bg-surface-container-lowest text-on-surface text-sm rounded-lg px-3 py-2 outline-none shadow-sm border border-outline/30 focus:border-primary transition-colors"
          />
        </div>
      </div>

      <div className="mb-3">
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-on-surface-variant">Cocuklar ({childrenAges.length})</label>
            <button
              type="button"
              onClick={addChild}
              disabled={childrenAges.length >= 10}
              className="text-xs text-primary font-medium hover:underline disabled:opacity-50"
            >
              + Cocuk ekle
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {childrenAges.map((age, index) => (
              <div key={index} className="flex items-center gap-1">
                <span className="text-xs text-on-surface-variant">Cocuk {index + 1}:</span>
                <input
                  type="number"
                  min={0}
                  max={17}
                  value={age}
                  onChange={e => setChildAge(index, parseInt(e.target.value, 10) || 0)}
                  className="w-16 bg-surface-container-lowest text-on-surface text-sm rounded-lg px-2 py-1 outline-none shadow-sm border border-outline/30 focus:border-primary transition-colors"
                />
                <button
                  onClick={() => removeChild(index)}
                  className="text-on-surface-variant hover:text-on-surface"
                  aria-label="Cocuk sil"
                >
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>
              </div>
            ))}
          </div>
        </div>

      {error && (
        <div className="mb-3 p-2.5 rounded-lg bg-error-container text-error text-xs font-medium">
          {error}
        </div>
      )}

      <button
        onClick={handleSubmit}
        className="w-full bg-primary text-on-primary text-sm font-medium py-2.5 rounded-xl shadow-sm active:scale-[0.98] transition-all"
      >
        Musaitlik Ara
      </button>
    </div>
  );
}

interface OfferCardProps {
  offer: AvailableOffer;
  query: StayQuery;
  onLockPrice: (roomTypeId: string, ratePlanId: string, q: StayQuery) => void;
  pending: boolean;
}

function OfferCard({ offer, query, onLockPrice, pending }: OfferCardProps) {
  const [expanded, setExpanded] = useState(false);

  const cancellationText = (() => {
    if (offer.cancellationPolicy === 'FREE_CANCELLATION' && offer.freeCancellationDaysBefore != null) {
      return `Giristen ${offer.freeCancellationDaysBefore} gun oncesine kadar ucretsiz iptal`;
    }
    if (offer.cancellationPolicy === 'NON_REFUNDABLE') {
      return 'Iade edilemez';
    }
    return CANCELLATION_POLICY_LABEL[offer.cancellationPolicy] ?? '';
  })();

  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
      <div className="p-4">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="min-w-0">
            <h4 className="text-sm font-semibold text-on-surface">{offer.roomTypeName}</h4>
            <p className="text-xs text-on-surface-variant mt-0.5">{offer.ratePlanName}</p>
          </div>
          <span className="text-lg font-bold text-primary shrink-0">
            {formatCurrency(offer.totalAmount, offer.currency)}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mb-3 text-xs text-on-surface-variant">
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">restaurant</span>
            {MEAL_PLAN_LABEL[offer.mealPlan] ?? ''}
          </span>
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">cancel</span>
            {cancellationText}
          </span>
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">person</span>
            En fazla {offer.maxAdults} yetiskin, {offer.maxChildren} cocuk ({offer.maxOccupancy} kisi)
          </span>
          <span className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">calendar_today</span>
            {offer.nights} gece, {offer.rooms} oda
          </span>
        </div>

        {offer.nightly.length > 0 && (
          <div>
            <button
              onClick={() => setExpanded(!expanded)}
              className="flex items-center gap-1 text-xs text-primary font-medium hover:underline mb-2"
            >
              <span className={`material-symbols-outlined text-[16px] transition-transform ${expanded ? 'rotate-90' : ''}`}>
                chevron_right
              </span>
              Gecelik fiyatlari
            </button>
            {expanded && (
              <div className="space-y-1 mb-3">
                {offer.nightly.map((night, i) => (
                  <div key={i} className="flex justify-between text-xs text-on-surface-variant">
                    <span>{night.stayDate}</span>
                    <span>{formatCurrency(night.amount, night.currency)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <button
          onClick={() => onLockPrice(offer.roomTypeId, offer.ratePlanId, query)}
          disabled={pending}
          className="w-full bg-secondary text-on-secondary text-sm font-medium py-2.5 rounded-xl shadow-sm active:scale-[0.98] transition-all disabled:opacity-60 disabled:pointer-events-none"
        >
          {pending ? 'Yukleniyor...' : 'Fiyati sabitle'}
        </button>
      </div>
    </div>
  );
}

function HotelsDetailContent() {
  const params = useParams();
  const id = params?.id as string;
  const router = useRouter();
  const searchParams = useSearchParams();

  const { user: authUser } = useAuth();

  const [hotel, setHotel] = useState<Hotel | null>(null);
  const [hotelLoading, setHotelLoading] = useState(true);
  const [hotelError, setHotelError] = useState<string | null>(null);
  const [offers, setOffers] = useState<AvailableOffer[]>([]);
  const [offerLoading, setOfferLoading] = useState(false);
  const [offerError, setOfferError] = useState<string | null>(null);
  const [pendingQuote, setPendingQuote] = useState(false);
  const [searchQuery, setSearchQuery] = useState<StayQuery | null>(
    () => parseStayQuery(searchParams),
  );
  const [noticeMessage, setNoticeMessage] = useState<string | null>(null);

  // Load hotel
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!id) return;
    setHotelLoading(true);
    setHotelError(null);
    api.get<Hotel>(`/api/hotels/${id}`).then(res => {
      setHotel(res.data);
      setHotelLoading(false);
    }).catch(() => {
      setHotelError('Otel bulunamadi');
      setHotelLoading(false);
    });
  }, [id]);
  /* eslint-enable react-hooks/set-state-in-effect */

  // Load availability when searchQuery changes
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!searchQuery || !id) return;
    setOfferLoading(true);
    setOfferError(null);
    hotelStayApi.availability(id, searchQuery).then(res => {
      setOffers(res.data.offers);
      setOfferLoading(false);
    }).catch(err => {
      setOfferError(err instanceof Error ? err.message : 'Araba sirasinda bir hata olustu');
      setOfferLoading(false);
    });
  }, [id, searchQuery]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const handleFormSubmit = useCallback((q: StayQuery) => {
    setNoticeMessage(null);
    setSearchQuery(q);
    router.replace(`/hotels/${id}?${stayQueryString(q)}`);
  }, [id, router]);

  const handleLockPrice = useCallback((roomTypeId: string, ratePlanId: string, q: StayQuery) => {
    if (!authUser) {
      router.push('/login');
      return;
    }
    if (!hotel) return;
    setPendingQuote(true);
    hotelStayApi.createQuote({
      hotelId: hotel.id,
      roomTypeId,
      ratePlanId,
      ...q,
    }).then(res => {
      router.push(`/quotes/${res.data.quoteId}`);
    }).catch((err: unknown) => {
      setPendingQuote(false);
      if (err instanceof ApiError && err.code === 'ADMIN_CANNOT_PURCHASE') {
        setOfferError(ADMIN_CANNOT_PURCHASE_MESSAGE);
      } else if (err instanceof ApiError && (err.status === 409 || err.status === 422)) {
        setNoticeMessage('Fiyat veya musaitlik degisti, lutfen tekrar arayin');
        // Re-run availability to refresh
        if (searchQuery) {
          hotelStayApi.availability(hotel.id, searchQuery).then(r => {
            setOffers(r.data.offers);
          }).catch(() => {
            // Re-run failed; notice message stays visible
          });
        }
      } else {
        setOfferError(err instanceof Error ? err.message : 'Teklif olusturulurken bir hata olustu');
      }
    });
  }, [hotel, searchQuery, router, authUser]);

  if (hotelLoading) return <LoadingState />;
  if (hotelError || !hotel) return <ErrorState message={hotelError ?? 'Otel bulunamadi'} />;

  const stars = '★'.repeat(hotel.starRating);

  return (
    <div className="space-y-space-md">
      {/* Back link */}
      <Link href="/hotels" className="inline-flex items-center gap-1 text-primary text-sm font-medium hover:underline">
        <span className="material-symbols-outlined text-[18px]">arrow_back</span> Otellere don
      </Link>

      {/* Hotel info */}
      <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4 mb-3">
          <div>
            <h2 className="text-xl font-semibold text-on-surface">{hotel.name}</h2>
            <div className="flex items-center gap-2 mt-1">
              <span className="text-sm text-warning">{stars}</span>
              <span className="text-xs text-on-surface-variant">
                {[hotel.city, hotel.country].filter(Boolean).join(', ')}
              </span>
            </div>
          </div>
        </div>
        {hotel.address && (
          <div className="flex items-center gap-1 text-xs text-on-surface-variant mb-3">
            <span className="material-symbols-outlined text-[14px]">location_on</span>
            {hotel.address}
          </div>
        )}
        {hotel.description && (
          <p className="text-sm text-on-surface-variant leading-relaxed">{hotel.description}</p>
        )}
      </div>

      {/* Stay form */}
      <StayForm
        key={searchQuery ? stayQueryString(searchQuery) : 'default'}
        initial={searchQuery || {
          checkIn: addDaysIso(todayIso(), 1),
          checkOut: addDaysIso(todayIso(), 2),
          rooms: 1,
          adults: 1,
          childrenAges: [],
        }}
        onValidSubmit={handleFormSubmit}
      />

      {/* Notice / stale-price banner */}
      {noticeMessage && (
        <div className="p-4 rounded-xl bg-warning-container text-on-warning-container text-sm font-medium text-center">
          {noticeMessage}
        </div>
      )}

      {/* Loading */}
      {offerLoading && <LoadingState />}

      {/* Error */}
      {offerError && (
        <div className="p-4 rounded-xl bg-error-container text-error text-sm font-medium text-center">
          {offerError}
        </div>
      )}

      {/* No offers */}
      {!offerLoading && searchQuery && offers.length === 0 && !offerError && (
        <div className="text-center py-12">
          <span className="material-symbols-outlined text-outline text-5xl mb-3 block">hotel</span>
          <p className="text-on-surface-variant text-sm">Bu tarihlerde uygun oda yok</p>
        </div>
      )}

      {/* Offers */}
      {!offerLoading && offers.length > 0 && (
        <div className="space-y-3">
          {offers.map(offer => (
            <OfferCard
              key={`${offer.roomTypeId}-${offer.ratePlanId}`}
              offer={offer}
              query={searchQuery!}
              onLockPrice={handleLockPrice}
              pending={pendingQuote}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function HotelsDetailPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <HotelsDetailContent />
    </Suspense>
  );
}

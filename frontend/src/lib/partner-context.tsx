'use client';

/**
 * Partner workspace state: which organization and hotel the user is currently managing.
 * Every /partner page reads the selection from here instead of fetching it itself.
 * The selection is remembered in localStorage so a reload keeps the same hotel.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { partnerApi, type Membership, type PartnerHotel } from './partner';

const ORG_KEY = 'waylo_partner_org';
const HOTEL_KEY = 'waylo_partner_hotel';

interface PartnerContextValue {
  loading: boolean;
  error: string | null;
  memberships: Membership[];
  organizationId: string | null;
  membership: Membership | null;
  hotels: PartnerHotel[];
  hotelId: string | null;
  hotel: PartnerHotel | null;
  selectOrganization: (organizationId: string) => void;
  selectHotel: (hotelId: string) => void;
  /** Re-fetch memberships and hotels, e.g. after creating an organization or hotel. */
  reload: () => Promise<void>;
}

const PartnerContext = createContext<PartnerContextValue | null>(null);

export function PartnerProvider({ children }: { children: React.ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [organizationId, setOrganizationId] = useState<string | null>(null);
  const [hotels, setHotels] = useState<PartnerHotel[]>([]);
  const [hotelId, setHotelId] = useState<string | null>(null);

  const loadHotels = useCallback(async (orgId: string | null) => {
    if (!orgId) {
      setHotels([]);
      setHotelId(null);
      return;
    }
    const res = await partnerApi.hotelsByOrganization(orgId);
    const list = res.data ?? [];
    setHotels(list);
    const stored = localStorage.getItem(HOTEL_KEY);
    const next = list.find(h => h.id === stored)?.id ?? list[0]?.id ?? null;
    setHotelId(next);
    if (next) localStorage.setItem(HOTEL_KEY, next);
  }, []);

  const fetchAll = useCallback(async () => {
    try {
      const res = await partnerApi.myMemberships();
      const list = (res.data ?? []).filter(m => m.status === 'ACTIVE');
      setMemberships(list);
      const stored = localStorage.getItem(ORG_KEY);
      const orgId = list.find(m => m.organizationId === stored)?.organizationId ?? list[0]?.organizationId ?? null;
      setOrganizationId(orgId);
      if (orgId) localStorage.setItem(ORG_KEY, orgId);
      await loadHotels(orgId);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Isletme bilgileri yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, [loadHotels]);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    await fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    // Initial load: state starts as loading=true, so only the async result updates state.
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return fetchAll(); });
    return () => { cancelled = true; };
  }, [fetchAll]);

  const selectOrganization = useCallback((orgId: string) => {
    localStorage.setItem(ORG_KEY, orgId);
    localStorage.removeItem(HOTEL_KEY);
    setOrganizationId(orgId);
    void loadHotels(orgId).catch(e => setError(e instanceof Error ? e.message : 'Oteller yuklenemedi'));
  }, [loadHotels]);

  const selectHotel = useCallback((id: string) => {
    localStorage.setItem(HOTEL_KEY, id);
    setHotelId(id);
  }, []);

  const value = useMemo<PartnerContextValue>(() => ({
    loading,
    error,
    memberships,
    organizationId,
    membership: memberships.find(m => m.organizationId === organizationId) ?? null,
    hotels,
    hotelId,
    hotel: hotels.find(h => h.id === hotelId) ?? null,
    selectOrganization,
    selectHotel,
    reload,
  }), [loading, error, memberships, organizationId, hotels, hotelId, selectOrganization, selectHotel, reload]);

  return <PartnerContext.Provider value={value}>{children}</PartnerContext.Provider>;
}

export function usePartner() {
  const ctx = useContext(PartnerContext);
  if (!ctx) throw new Error('usePartner must be used within PartnerProvider');
  return ctx;
}

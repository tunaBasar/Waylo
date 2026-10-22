'use client';

/**
 * Partner workspace state: which organization and hotel the user is currently managing.
 * Every /partner page reads the selection from here instead of fetching it itself.
 * The selection is remembered in localStorage so a reload keeps the same hotel.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
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
  reload: (preferred?: { organizationId?: string; hotelId?: string }) => Promise<void>;
  /** A page with unsaved edits sets this; the workspace selector is locked while it is true. */
  hasUnsavedChanges: boolean;
  setHasUnsavedChanges: (dirty: boolean) => void;
}

const PartnerContext = createContext<PartnerContextValue | null>(null);

export function PartnerProvider({ children }: { children: React.ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [organizationId, setOrganizationId] = useState<string | null>(null);
  const [hotels, setHotels] = useState<PartnerHotel[]>([]);
  const [hotelId, setHotelId] = useState<string | null>(null);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  const hotelRequest = useRef(0);
  const workspaceRequest = useRef(0);
  const loadHotels = useCallback(async (orgId: string | null, preferredHotelId?: string) => {
    const request = ++hotelRequest.current;
    setHotels([]);
    setHotelId(null);
    if (!orgId) {
      setError(null);
      return;
    }
    try {
      const res = await partnerApi.hotelsByOrganization(orgId);
      if (request !== hotelRequest.current) return;
      const list = res.data ?? [];
      setHotels(list);
      const stored = preferredHotelId ?? localStorage.getItem(HOTEL_KEY);
      const next = list.find(h => h.id === stored)?.id ?? list[0]?.id ?? null;
      setHotelId(next);
      if (next) localStorage.setItem(HOTEL_KEY, next);
      setError(null);
    } catch (e) {
      if (request === hotelRequest.current) setError(e instanceof Error ? e.message : 'Oteller yuklenemedi');
    }
  }, []);

  const fetchAll = useCallback(async (preferred?: { organizationId?: string; hotelId?: string }) => {
    const request = ++workspaceRequest.current;
    try {
      const res = await partnerApi.myMemberships();
      if (request !== workspaceRequest.current) return;
      const list = (res.data ?? []).filter(m => m.status === 'ACTIVE');
      setMemberships(list);
      const stored = preferred?.organizationId ?? localStorage.getItem(ORG_KEY);
      const orgId = list.find(m => m.organizationId === stored)?.organizationId ?? list[0]?.organizationId ?? null;
      setOrganizationId(orgId);
      if (orgId) localStorage.setItem(ORG_KEY, orgId);
      await loadHotels(orgId, preferred?.hotelId);
    } catch (e) {
      if (request === workspaceRequest.current) setError(e instanceof Error ? e.message : 'Isletme bilgileri yuklenemedi');
    } finally {
      if (request === workspaceRequest.current) setLoading(false);
    }
  }, [loadHotels]);

  const reload = useCallback(async (preferred?: { organizationId?: string; hotelId?: string }) => {
    setLoading(true);
    setError(null);
    await fetchAll(preferred);
  }, [fetchAll]);

  useEffect(() => {
    // Initial load: state starts as loading=true, so only the async result updates state.
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return fetchAll(); });
    return () => { cancelled = true; };
  }, [fetchAll]);

  // Ignored while a page has unsaved edits. The selects are also disabled in the UI, but a disabled
  // control can still be changed programmatically, so the guard lives here where the state does.
  const selectOrganization = useCallback((orgId: string) => {
    if (hasUnsavedChanges || loading || orgId === organizationId || !memberships.some(m => m.organizationId === orgId)) return;
    workspaceRequest.current++;
    localStorage.setItem(ORG_KEY, orgId);
    localStorage.removeItem(HOTEL_KEY);
    setOrganizationId(orgId);
    void loadHotels(orgId);
  }, [loadHotels, hasUnsavedChanges, loading, organizationId, memberships]);

  const selectHotel = useCallback((id: string) => {
    if (hasUnsavedChanges || !hotels.some(h => h.id === id)) return;
    localStorage.setItem(HOTEL_KEY, id);
    setHotelId(id);
  }, [hasUnsavedChanges, hotels]);

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
    hasUnsavedChanges,
    setHasUnsavedChanges,
  }), [loading, error, memberships, organizationId, hotels, hotelId, selectOrganization, selectHotel, reload, hasUnsavedChanges]);

  return <PartnerContext.Provider value={value}>{children}</PartnerContext.Provider>;
}

export function usePartner() {
  const ctx = useContext(PartnerContext);
  if (!ctx) throw new Error('usePartner must be used within PartnerProvider');
  return ctx;
}

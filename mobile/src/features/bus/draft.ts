/**
 * Seat picks and passenger names kept in memory only across the login detour. Never persisted
 * (passenger names are personal data). take() returns the draft once and deletes it.
 */
import type { Picked } from '@/features/bus/seats';

const drafts = new Map<string, Picked[]>();

export const saveBusDraft = (key: string, picked: Picked[]) => { if (picked.length) drafts.set(key, picked.map((p) => ({ ...p }))); };
export function takeBusDraft(key: string): Picked[] | null { const d = drafts.get(key) ?? null; drafts.delete(key); return d; }
export const clearBusDrafts = () => drafts.clear();

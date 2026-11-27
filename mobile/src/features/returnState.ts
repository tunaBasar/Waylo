/** Search state carried through the login detour as a returnTo query (pure, unit tested). Never holds personal data. */
import { isoLocalDateTime, parseIsoDate } from '@/features/dates';

const enc = encodeURIComponent;
const one = (v: string | string[] | undefined): string | undefined => (Array.isArray(v) ? v[0] : v);
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());

export interface BusReturn { from: string; to: string; date: string; trip?: { tripId: string; fromSeq: number; toSeq: number } }

export const busTripKey = (t: { tripId: string; fromSeq: number; toSeq: number }) => `${t.tripId}-${t.fromSeq}-${t.toSeq}`;

export function buildBusReturn(s: { from: string; to: string; date: string; tripKey?: string | null }): string {
  const qs = `from=${enc(s.from)}&to=${enc(s.to)}&date=${enc(s.date)}${s.tripKey ? `&trip=${enc(s.tripKey)}` : ''}`;
  return `/bus?${qs}`;
}

export function parseBusReturn(p: Record<string, string | string[] | undefined>, now: Date = new Date()): BusReturn | null {
  const from = one(p.from)?.trim();
  const to = one(p.to)?.trim();
  const date = one(p.date);
  if (!from || !to || from.length > 80 || to.length > 80 || !date) return null;
  const d = parseIsoDate(date);
  if (!d || d < startOfDay(now)) return null;
  const m = one(p.trip)?.match(/^(.+)-(\d{1,4})-(\d{1,4})$/);
  const trip = m && m[1].length <= 80 ? { tripId: m[1], fromSeq: Number(m[2]), toSeq: Number(m[3]) } : undefined;
  return { from, to, date, ...(trip ? { trip } : {}) };
}

export interface CarsReturn { pickupBranchId: string; returnBranchId: string; pickupAt?: Date; returnAt?: Date; driverAge?: number }

export function buildCarsReturn(s: { pickupBranchId: string; returnBranchId: string; pickupAt: Date; returnAt: Date; driverAge: number }): string {
  return `/cars?pickup=${enc(s.pickupBranchId)}&ret=${enc(s.returnBranchId)}&pickupAt=${enc(isoLocalDateTime(s.pickupAt))}&returnAt=${enc(isoLocalDateTime(s.returnAt))}&age=${s.driverAge}`;
}

function parseDateTime(s: string | undefined): Date | null {
  const m = s?.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2})?$/);
  if (!m) return null;
  const day = parseIsoDate(`${m[1]}-${m[2]}-${m[3]}`);
  const h = Number(m[4]); const mi = Number(m[5]);
  if (!day || h > 23 || mi > 59) return null;
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, mi);
}

export function parseCarsReturn(p: Record<string, string | string[] | undefined>, now: Date = new Date()): CarsReturn | null {
  const id = (v: string | undefined) => (v && /^[\w-]{1,64}$/.test(v) ? v : null);
  const pickupBranchId = id(one(p.pickup));
  if (!pickupBranchId) return null;
  const returnBranchId = id(one(p.ret)) ?? pickupBranchId;
  let pickupAt = parseDateTime(one(p.pickupAt));
  let returnAt = parseDateTime(one(p.returnAt));
  if (pickupAt && pickupAt.getTime() < now.getTime()) pickupAt = null;
  if (!pickupAt) returnAt = null;
  else if (returnAt && returnAt.getTime() <= pickupAt.getTime()) returnAt = null;
  const ageRaw = one(p.age);
  const age = ageRaw && /^\d{1,3}$/.test(ageRaw) ? Number(ageRaw) : NaN;
  return { pickupBranchId, returnBranchId, ...(pickupAt ? { pickupAt } : {}), ...(returnAt ? { returnAt } : {}), ...(age >= 18 && age <= 99 ? { driverAge: age } : {}) };
}


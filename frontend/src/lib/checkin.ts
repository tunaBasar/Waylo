/** Online check-in window (CHECKIN-CONTRACT.md): departure-24h .. departure-1h, both inclusive. Pure, no imports. */
export const CHECKIN_OPENS_HOURS = 24;
export const CHECKIN_CLOSES_HOURS = 1;

export interface CheckinWindow { open: boolean; reason: string | null }

export function checkinWindow(departureAt: string, now: number, supplier?: string | null, itemCancelled = false, boarded = false): CheckinWindow {
  if (supplier && supplier !== 'INTERNAL') return { open: false, reason: 'Bu ucusun check-in islemi havayolunda yapilir' };
  if (itemCancelled) return { open: false, reason: 'Iptal edilen bilet icin check-in yapilamaz' };
  if (boarded) return { open: false, reason: 'Ucusa binildi' };
  const dep = Date.parse(departureAt);
  if (Number.isNaN(dep)) return { open: false, reason: 'Kalkis saati bilinmiyor' };
  const opens = dep - CHECKIN_OPENS_HOURS * 3600_000;
  const closes = dep - CHECKIN_CLOSES_HOURS * 3600_000;
  if (now < opens) return { open: false, reason: `Online check-in kalkistan ${CHECKIN_OPENS_HOURS} saat once acilir` };
  if (now > closes) return { open: false, reason: `Online check-in kalkistan ${CHECKIN_CLOSES_HOURS} saat once kapanir` };
  return { open: true, reason: null };
}

export function checkinDoneText(seat: string | null | undefined): string {
  return seat ? `Check-in yapildi, koltuk ${seat}` : 'Check-in yapildi';
}

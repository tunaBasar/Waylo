import { ApiError, type Gender, type SeatLayout, type SeatState } from '@/api';

export const GENDER_LABEL: Record<Gender, string> = { FEMALE: 'Kadın', MALE: 'Erkek' };
export const MAX_SEATS = 5;

/** Seats per row for a layout. */
export function seatsPerRow(layout: SeatLayout): number {
  return layout === 'TWO_PLUS_TWO' ? 4 : 3;
}

/** Splits seats into rows. Each row is [left block, right block]; a 2+1 right block has one seat. */
export function seatRows(layout: SeatLayout, seats: SeatState[]): SeatState[][][] {
  const per = seatsPerRow(layout);
  const rows: SeatState[][][] = [];
  for (let i = 0; i < seats.length; i += per) {
    const row = seats.slice(i, i + per);
    rows.push([row.slice(0, 2), row.slice(2)]);
  }
  return rows;
}

/** The seat sharing a 2-seat block, or null (single seat of a 2+1 row). */
export function neighborSeat(layout: SeatLayout, seatNo: number): number | null {
  const per = seatsPerRow(layout);
  const idx = (seatNo - 1) % per;
  if (layout === 'TWO_PLUS_ONE' && idx === 2) return null;
  return idx % 2 === 0 ? seatNo + 1 : seatNo - 1;
}

/** Early feedback only (the server decides): neighbour taken by the other gender and not picked in this booking. */
export function seatBlockedByGender(layout: SeatLayout, seats: SeatState[], seatNo: number, gender: Gender, picked: number[]): boolean {
  const n = neighborSeat(layout, seatNo);
  if (n === null || picked.includes(n)) return false;
  const other = seats.find((s) => s.seatNo === n);
  return !!other && !other.available && other.occupantGender !== null && other.occupantGender !== gender;
}

export const GENDER_RULE_TEXT = 'Yan yana farklı cinsiyetten tanımadığı yolcu oturamaz.';

const ERROR_TEXT: Record<string, string> = {
  BUS_SALES_DISABLED: 'Otobüs bileti satışı henüz açık değil.',
  SEAT_TAKEN: 'Seçilen koltuk az önce satıldı. Başka koltuk seçin.',
  SEAT_GENDER_CONFLICT: 'Yan koltukta farklı cinsiyetten yolcu var. Başka koltuk seçin.',
  SALES_CLOSED: 'Bu sefer için bilet satışı kapandı.',
  PARTNER_NOT_SELLABLE: 'Bu firmanın seferi şu an satışa kapalı.',
  INVALID_SEAT: 'Geçersiz koltuk.',
  SEGMENT_NOT_OFFERED: 'Bu güzergah için fiyat yok.',
  ADMIN_CANNOT_PURCHASE: 'Platform yöneticisi hesabı ile satın alma yapılamaz.',
};

export function busErrorMessage(err: unknown, fallback: string): string {
  const code = err instanceof ApiError ? err.code : null;
  if (code && ERROR_TEXT[code]) return ERROR_TEXT[code];
  return (err instanceof Error && err.message) || fallback;
}

export interface Picked { seatNo: number; name: string; gender: Gender }

export function conflictSeats(layout: SeatLayout, seats: SeatState[], picked: Picked[]): Set<number> {
  const nos = picked.map((p) => p.seatNo);
  return new Set(picked.filter((p) => seatBlockedByGender(layout, seats, p.seatNo, p.gender, nos)).map((p) => p.seatNo));
}

export const passengersReady = (picked: Picked[], conflicts: Set<number>) =>
  picked.length > 0 && picked.every((p) => p.name.trim().length >= 2) && conflicts.size === 0;

/** Request of the BUS cart item, same wire shape as the web. */
export function busCartRequest(o: { tripId: string; fromSeq: number; toSeq: number }, picked: Picked[]) {
  return { tripId: o.tripId, fromSeq: o.fromSeq, toSeq: o.toSeq, passengers: picked.map((p) => ({ seatNo: p.seatNo, name: p.name.trim(), gender: p.gender })) };
}

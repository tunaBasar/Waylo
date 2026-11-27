import { ApiError, type SeatState } from '@/api';
import { busCartRequest, busErrorMessage, conflictSeats, neighborSeat, passengersReady, seatBlockedByGender, seatRows, seatsPerRow } from '@/features/bus/seats';

const seat = (seatNo: number, available = true, occupantGender: SeatState['occupantGender'] = null): SeatState => ({ seatNo, available, occupantGender });

describe('bus seat helpers', () => {
  it('seatsPerRow and seatRows', () => {
    expect(seatsPerRow('TWO_PLUS_TWO')).toBe(4);
    expect(seatsPerRow('TWO_PLUS_ONE')).toBe(3);
    const rows = seatRows('TWO_PLUS_ONE', [1, 2, 3, 4, 5, 6].map((n) => seat(n)));
    expect(rows).toHaveLength(2);
    expect(rows[0][0].map((s) => s.seatNo)).toEqual([1, 2]);
    expect(rows[0][1].map((s) => s.seatNo)).toEqual([3]);
  });
  it('neighborSeat', () => {
    expect(neighborSeat('TWO_PLUS_TWO', 1)).toBe(2);
    expect(neighborSeat('TWO_PLUS_TWO', 2)).toBe(1);
    expect(neighborSeat('TWO_PLUS_TWO', 3)).toBe(4);
    expect(neighborSeat('TWO_PLUS_ONE', 3)).toBeNull();
    expect(neighborSeat('TWO_PLUS_ONE', 4)).toBe(5);
  });
  it('gender rule', () => {
    const seats = [seat(1), seat(2, false, 'FEMALE'), seat(3), seat(4)];
    expect(seatBlockedByGender('TWO_PLUS_TWO', seats, 1, 'MALE', [])).toBe(true);
    expect(seatBlockedByGender('TWO_PLUS_TWO', seats, 1, 'FEMALE', [])).toBe(false);
    expect(seatBlockedByGender('TWO_PLUS_TWO', seats, 3, 'MALE', [])).toBe(false);
    const picked = [{ seatNo: 1, name: 'Ali Veli', gender: 'MALE' as const }];
    expect([...conflictSeats('TWO_PLUS_TWO', seats, picked)]).toEqual([1]);
    expect(passengersReady(picked, conflictSeats('TWO_PLUS_TWO', seats, picked))).toBe(false);
  });
  it('passengersReady needs names', () => {
    expect(passengersReady([], new Set())).toBe(false);
    expect(passengersReady([{ seatNo: 1, name: 'A', gender: 'MALE' }], new Set())).toBe(false);
    expect(passengersReady([{ seatNo: 1, name: 'Ay', gender: 'MALE' }], new Set())).toBe(true);
  });
  it('cart request matches the web shape', () => {
    expect(busCartRequest({ tripId: 't', fromSeq: 0, toSeq: 2 }, [{ seatNo: 4, name: ' Ayse Y ', gender: 'FEMALE' }]))
      .toEqual({ tripId: 't', fromSeq: 0, toSeq: 2, passengers: [{ seatNo: 4, name: 'Ayse Y', gender: 'FEMALE' }] });
  });
  it('error texts', () => {
    expect(busErrorMessage(new ApiError('x', 409, 'SEAT_TAKEN'), 'f')).toMatch(/az önce satıldı/);
    expect(busErrorMessage(new ApiError('x', 409, 'SEAT_GENDER_CONFLICT'), 'f')).toMatch(/cinsiyet/);
    expect(busErrorMessage(new ApiError('server text', 400, 'OTHER'), 'f')).toBe('server text');
    expect(busErrorMessage('boom', 'fallback')).toBe('fallback');
  });
});

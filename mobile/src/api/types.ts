/** Wire contracts, mirrored from frontend/src/lib/*.ts. No business logic here. */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp?: string;
  code?: string;
}

export interface AuthData {
  token: string;
  refreshToken: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  emailVerified: boolean;
}

export interface Profile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface Hotel {
  id: string;
  name: string;
  city: string;
  country: string;
  address: string;
  starRating: number;
  description: string | null;
  averageScore?: number | null;
  reviewCount?: number | null;
  coverMediaId?: string | null;
}

export interface StayQuery {
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  childrenAges: number[];
}

export interface AvailableOffer {
  roomTypeId: string;
  roomTypeName: string;
  ratePlanId: string;
  ratePlanName: string;
  mealPlan: string;
  cancellationPolicy: string;
  freeCancellationDaysBefore: number | null;
  nights: number;
  currency: string;
  totalAmount: number;
}

export interface Availability {
  hotelId: string;
  nights: number;
  offers: AvailableOffer[];
  emptyReason?: { code: 'NO_STOCK' | 'NO_PRICE_FOR_OCCUPANCY' | 'NO_ROOM_TYPE_FOR_GROUP'; date: string | null } | null;
  nextAvailableCheckIn?: string | null;
}

export interface Quote {
  quoteId: string;
  hotelId: string;
  roomTypeName: string;
  ratePlanName: string;
  mealPlan: string;
  cancellationPolicy: string;
  freeCancellationDaysBefore: number | null;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: number;
  adults: number;
  currency: string;
  totalAmount: number;
  expiresAt: string;
  expired: boolean;
}

export interface FlightOffer {
  offerId: string;
  supplier: string;
  flightNumber: string;
  airline: string;
  departureAirport: string;
  arrivalAirport: string;
  departureTime: string;
  arrivalTime: string;
  cabin: string;
  currency: string;
  totalAmount: number;
  availableSeats: number;
  expiresAt: string;
}

export interface Branch {
  id: string;
  name: string;
  city: string;
  address: string;
}

export interface CarOffer {
  pickupBranchId: string;
  returnBranchId: string;
  category: string;
  transmission: string;
  exampleModel: string;
  seatCount: number;
  fuelType: string;
  chargeableDays: number;
  dailyRate: number;
  totalAmount: number;
  currency: string;
  depositAmount: number;
}

export interface Terminal {
  id: string;
  city: string;
  name: string;
}

export type SeatLayout = 'TWO_PLUS_TWO' | 'TWO_PLUS_ONE';
export type Gender = 'FEMALE' | 'MALE';
export interface SeatState { seatNo: number; available: boolean; occupantGender: Gender | null }
export interface SeatMap { tripId: string; layout: SeatLayout; rowCount: number; fromSeq: number; toSeq: number; seats: SeatState[] }
export interface PassengerInput { seatNo: number; name: string; gender: Gender }

export interface TripOffer {
  tripId: string;
  fromSeq: number;
  toSeq: number;
  layout: SeatLayout;
  companyName: string | null;
  code: string;
  fromCity: string;
  toCity: string;
  departureAt: string;
  arrivalAt: string;
  price: number;
  currency: string;
  availableSeats: number;
}

export interface OrderItem {
  id: string;
  vertical: string;
  description: string;
  subtotal: number;
  currency: string;
  cancellationPolicy?: string | null;
  cancelFreeHoursBefore?: number | null;
  freeCancellationUntil?: string | null;
  cancelOutcome?: string | null;
  refundAmount?: number | null;
  flight?: {
    departureAt?: string | null; supplier?: string | null; flightNumber: string;
    passengerFirstName: string; passengerLastName: string; seatNumber: string | null; pnr: string | null; ticketNumber: string | null;
  } | null;
}

export interface Order {
  id: string;
  status: string;
  currency: string;
  totalAmount: number;
  createdAt: string;
  holdExpiresAt?: string | null;
  items?: OrderItem[];
}

export interface Voucher {
  voucherId: string;
  orderItemId?: string;
  description: string;
  payload: string | null;
  status: 'ACTIVE' | 'REDEEMED' | 'VOID' | 'NO_SHOW';
  validFrom: string;
  validUntil: string;
}

export interface CancellationPreview {
  cancellable: boolean;
  blockedReason: string | null;
  currency: string;
  totalRefund: number;
}

export interface PaymentIntent {
  id: string;
  testMode: boolean;
  status: string;
}

export type CartVertical = 'HOTEL' | 'CAR' | 'FLIGHT' | 'BUS';

/** Request fields of POST /api/orders/package items, flat. The server re-prices everything. */
export interface CartItem {
  id: string;
  vertical: CartVertical;
  label: string;
  amount: number | null;
  currency: string | null;
  request: Record<string, unknown>;
}

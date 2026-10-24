// Flight types
export interface Airport {
  id: string;
  iataCode: string;
  name: string;
  city: string;
  country: string;
  timezone: string;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Flight {
  id: string;
  flightNumber: string;
  departureAirportCode: string;
  departureAirportName: string;
  arrivalAirportCode: string;
  arrivalAirportName: string;
  departureAirport?: string;
  arrivalAirport?: string;
  departureCity?: string;
  arrivalCity?: string;
  departureTime: string;
  arrivalTime: string;
  airline: string;
  status: 'SCHEDULED' | 'CANCELLED';
  duration?: string;
  price?: number;
  totalSeats?: number;
  availableSeats?: number;
  aircraftType?: string;
  createdAt: string;
  updatedAt: string;
}

// Hotel types
export interface Hotel {
  id: string;
  name: string;
  city: string;
  country: string;
  address: string;
  starRating: number;
  description: string | null;
  latitude: number | null;
  longitude: number | null;
  active: boolean;
  roomCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Room {
  id: string;
  hotelId: string;
  hotelName: string;
  roomNumber: string;
  roomType: 'SINGLE' | 'DOUBLE' | 'SUITE' | 'DELUXE' | 'FAMILY';
  status: 'AVAILABLE' | 'RESERVED' | 'OCCUPIED' | 'MAINTENANCE';
  capacity: number;
  pricePerNight: number;
  floor: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

// Vehicle types
export interface Vehicle {
  id: string;
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  vehicleType: 'ECONOMY' | 'COMPACT' | 'MIDSIZE' | 'FULLSIZE' | 'SUV' | 'LUXURY' | 'VAN';
  status: 'AVAILABLE' | 'MAINTENANCE';
  version: number;
  color: string | null;
  seatCount: number;
  dailyRate: number;
  location: string;
  mileage: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

// Public availability and rental types
export interface AvailableVehicle {
  id: string;
  plateNumber: string;
  brand: string;
  model: string;
  vehicleType: 'ECONOMY' | 'COMPACT' | 'MIDSIZE' | 'FULLSIZE' | 'SUV' | 'LUXURY' | 'VAN';
  seatCount: number;
  dailyRate: number;
  location: string;
}

export type RentalStatus = 'RESERVED' | 'CANCELLED';

export interface Rental {
  id: string;
  vehicleId: string;
  customerId: string;
  pickupDate: string;
  returnDate: string;
  dailyRate: number;
  totalPrice: number;
  status: RentalStatus;
  createdAt: string;
}

export interface PackageBooking {
  id: string;
  customerId: string;
  flightId: string | null;
  hotelReservationId: string | null;
  vehicleReservationId: string | null;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED' | 'PARTIALLY_CONFIRMED';
  totalPrice: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// Customer types
export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  passportNumber: string | null;
  tier: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
  loyaltyPoints: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

// Payment types
export interface Payment {
  id: string;
  bookingId: string;
  customerId: string;
  amount: number;
  currency: string;
  paymentMethod: 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER' | 'MOCK';
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  transactionRef: string;
  createdAt: string;
  updatedAt: string;
}

// Notification types
export interface Notification {
  id: string;
  customerId: string;
  bookingId: string | null;
  type: 'EMAIL' | 'SMS' | 'PUSH';
  recipient: string;
  subject: string | null;
  body: string;
  status: 'PENDING' | 'SENT' | 'FAILED' | 'RETRYING';
  retryCount: number;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// Checkin types
export interface Checkin {
  id: string;
  bookingId: string;
  customerId: string;
  flightId: string;
  seatNumber: string | null;
  boardingPassUrl: string | null;
  status: 'NOT_CHECKED_IN' | 'CHECKED_IN' | 'BOARDING_PASS_ISSUED' | 'BOARDED' | 'CANCELLED';
  checkedInAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// Pricing types
export interface PricingRule {
  id: string;
  name: string;
  pricingType: 'FLIGHT' | 'HOTEL' | 'CAR_RENTAL' | 'PACKAGE_DISCOUNT';
  basePrice: number;
  discountPercentage: number | null;
  currency: string;
  active: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

import { api } from '@/api';

export interface HotelReview {
  id: string; hotelId: string; score: number; text: string | null; partnerResponse: string | null; createdAt: string;
}
export interface HotelReviewPage { items: HotelReview[]; page: number; size: number; total: number; averageScore: number | null; reviewCount: number }
export interface ReviewableItem { orderItemId: string; hotelId: string; hotelName: string; description: string; checkIn: string | null; checkOut: string | null }

export const MAX_REVIEW_TEXT = 2000;

/** Returns an error message or null. Score is an integer 1..5. */
export function validateReview(score: number, text: string): string | null {
  if (!Number.isInteger(score) || score < 1 || score > 5) return 'Puan 1 ile 5 arasinda olmali.';
  if (text.trim().length > MAX_REVIEW_TEXT) return `Yorum en fazla ${MAX_REVIEW_TEXT} karakter olabilir.`;
  return null;
}

export const scoreLabel = (s: number | null | undefined) => (s == null ? null : s.toFixed(1));

export const reviewsApi = {
  list: (hotelId: string, page = 0, size = 10) =>
    api.get<HotelReviewPage>(`/api/hotels/${encodeURIComponent(hotelId)}/reviews?page=${page}&size=${size}`),
  reviewable: () => api.get<ReviewableItem[]>('/api/me/reviewable-hotel-items'),
  create: (orderItemId: string, score: number, text: string) =>
    api.post<HotelReview>('/api/me/hotel-reviews', { orderItemId, score, text: text.trim() || null }),
};

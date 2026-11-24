/** Verified hotel review API client (RM-29). */
import { api } from '@/lib/api';

export interface HotelReview {
  id: string;
  hotelId: string;
  score: number;
  text: string | null;
  status: 'PUBLISHED' | 'HIDDEN' | null;
  partnerResponse: string | null;
  respondedAt: string | null;
  createdAt: string;
  orderItemId: string | null;
  moderationReason: string | null;
  moderatedAt: string | null;
}

export interface HotelReviewPage {
  items: HotelReview[];
  page: number;
  size: number;
  total: number;
  averageScore: number | null;
  reviewCount: number;
}

export interface ReviewableItem {
  orderItemId: string;
  hotelId: string;
  hotelName: string;
  description: string;
  checkIn: string | null;
  checkOut: string | null;
}

export const reviewApi = {
  publicList: (hotelId: string, page = 0, size = 10) =>
    api.get<HotelReviewPage>(`/api/hotels/${encodeURIComponent(hotelId)}/reviews?page=${page}&size=${size}`),
  reviewable: () => api.get<ReviewableItem[]>('/api/me/reviewable-hotel-items'),
  create: (orderItemId: string, score: number, text: string) =>
    api.post<HotelReview>('/api/me/hotel-reviews', { orderItemId, score, text: text.trim() || null }),
  partnerList: (orgId: string, page = 0, size = 20) =>
    api.get<HotelReviewPage>(`/api/partner/organizations/${encodeURIComponent(orgId)}/hotel-reviews?page=${page}&size=${size}`),
  respond: (orgId: string, id: string, text: string) =>
    api.post<HotelReview>(`/api/partner/organizations/${encodeURIComponent(orgId)}/hotel-reviews/${encodeURIComponent(id)}/response`, { text }),
  adminList: (status: string, page = 0, size = 20) =>
    api.get<HotelReviewPage>(`/api/admin/hotel-reviews?page=${page}&size=${size}${status ? `&status=${status}` : ''}`),
  moderate: (id: string, status: 'PUBLISHED' | 'HIDDEN', reason: string) =>
    api.post<HotelReview>(`/api/admin/hotel-reviews/${encodeURIComponent(id)}/moderate`, { status, reason }),
};

/** "4.5" style label, null when there is no score. */
export function scoreLabel(score: number | null | undefined): string | null {
  return score == null ? null : score.toFixed(1);
}

/** When a stay becomes reviewable (RM-29: a recorded check-out is required). */
export const REVIEW_WHEN_HINT = 'Konaklamaniz tamamlanip cikisiniz (check-out) kaydedildikten sonra degerlendirme yapabilirsiniz.';

export const REVIEW_REASON_MESSAGE: Record<string, string> = {
  REVIEW_NOT_CHECKED_OUT: 'Bu konaklama icin cikisiniz (check-out) henuz kaydedilmedi. ' + REVIEW_WHEN_HINT,
  REVIEW_NOT_CHECKED_IN: 'Bu konaklama icin giris (check-in) kaydedilmedigi icin degerlendirme yapilamiyor.',
  REVIEW_CANCELLED_OR_NO_SHOW: 'Iptal edilen veya gelinmeyen konaklamalar degerlendirilemez.',
  REVIEW_ORDER_NOT_CONFIRMED: 'Siparisiniz onaylanmadigi icin degerlendirme yapilamiyor.',
  ORG_MEMBER_CANNOT_REVIEW: 'Bu otelin isletmesinde calisan hesaplar otele degerlendirme yapamaz.',
};

/** Turkish message for a failed review submission; unknown codes keep the server message. */
export function reviewErrorMessage(code: string | null | undefined, fallback: string): string {
  return (code && REVIEW_REASON_MESSAGE[code]) || fallback || 'Degerlendirme gonderilemedi';
}

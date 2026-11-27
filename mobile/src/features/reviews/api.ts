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

export const REVIEW_WHEN_HINT = 'Konaklamanız tamamlanıp çıkışınız (check-out) kaydedildikten sonra yorum yapabilirsiniz.';

const REVIEW_REASONS: Record<string, string> = {
  REVIEW_NOT_CHECKED_OUT: 'Bu konaklama için çıkışınız (check-out) henüz kaydedilmedi. ' + REVIEW_WHEN_HINT,
  REVIEW_NOT_CHECKED_IN: 'Bu konaklama için giriş (check-in) kaydedilmediği için yorum yapılamıyor.',
  REVIEW_CANCELLED_OR_NO_SHOW: 'İptal edilen veya gelinmeyen konaklamalar için yorum yapılamaz.',
  REVIEW_ORDER_NOT_CONFIRMED: 'Siparişiniz onaylanmadığı için yorum yapılamıyor.',
  ORG_MEMBER_CANNOT_REVIEW: 'Bu otelin işletmesinde çalışan hesaplar otele yorum yapamaz.',
};

/** Turkish message for a review submission error. Unknown codes fall back to the given text. */
export function reviewErrorMessage(err: { status?: number; code?: string | null; message?: string }): string {
  if (err.code && REVIEW_REASONS[err.code]) return REVIEW_REASONS[err.code];
  if (err.status === 0) return 'Sunucuya bağlanılamıyor. Lütfen internet bağlantınızı kontrol edin.';
  return err.message || 'Yorum gönderilemedi';
}

export const scoreLabel = (s: number | null | undefined) => (s == null ? null : s.toFixed(1));

/** '4,6 / 5 (328 değerlendirme)' with a Turkish decimal comma, or null when there are no reviews or no average. */
export function scoreText(average: number | null | undefined, count: number | null | undefined): string | null {
  if (average == null || !count || count <= 0) return null;
  return `${average.toFixed(1).replace('.', ',')} / 5 (${count} değerlendirme)`;
}

export const reviewsApi = {
  list: (hotelId: string, page = 0, size = 10) =>
    api.get<HotelReviewPage>(`/api/hotels/${encodeURIComponent(hotelId)}/reviews?page=${page}&size=${size}`),
  reviewable: () => api.get<ReviewableItem[]>('/api/me/reviewable-hotel-items'),
  create: (orderItemId: string, score: number, text: string) =>
    api.post<HotelReview>('/api/me/hotel-reviews', { orderItemId, score, text: text.trim() || null }),
};

# Waylo mobile: design brief for an AI design tool (2026-11-27)

The user judged the current mobile UI not good enough and will generate new designs with an AI design tool (Google
Stitch), compare several directions and pick one. This file keeps the prompts. They describe the product and the real
data only; the visual style is left to the tool on purpose. Prompts are in English (these tools follow English best);
every UI text in the designs is Turkish. Each prompt is self-contained and stays under ~4,500 characters (Stitch drops
components on longer prompts).

## Prompt 1: three directions on six key screens

```
Design a customer mobile app for "Waylo", a Turkish travel marketplace. In one app a traveller searches and books hotels, domestic flights, car rentals and intercity bus tickets, puts them in one cart, pays once and gets a QR voucher per item. Phone portrait 390x844, to be built in React Native for iOS and Android.

Do not settle on one style. Create 3 clearly different design directions (layout, navigation, typography, color, density, card style). Give each a short name and a one-sentence idea. No brand guide exists; you may propose a simple "Waylo" wordmark. Design the same 6 screens in each direction:

1. Start: choose Otel, Uçuş, Araç or Otobüs and start a search. Guests can search without an account.
2. Hotel results for "İstanbul, 04.12.2026 - 06.12.2026, 1 oda, 2 yetişkin".
3. Hotel detail with photo gallery, reviews and room offers for those dates.
4. Bus seat selection: İstanbul -> Ankara, "Waylo Otobüs İşletmesi", 22:30 - 05:00, ₺750,00, 2+2 layout, 48 seats, some taken.
5. Cart with a hotel, a flight and a bus item, then checkout.
6. Order detail after payment with a QR voucher per item.

Rules:
- All text Turkish with correct characters (ç ğ ı İ ö ş ü). Money "₺7.220,00", dates "04.12.2026", 24-hour times "22:30".
- Only real features. Not available: loyalty points, maps, amenity lists, airline or bus company logos, car photos, live chat, social login, prices on the hotel list.
- Touch targets at least 44 pt, WCAG AA contrast, never color alone. Show at least one loading, empty or error state per direction.

Real data:
- Hotel search: city (optional), check-in, check-out, rooms, adults, children with the age of each child.
- Hotel card: cover photo (may be missing, needs a fallback), name, city and country, stars 1-5, guest score "4,6 / 5" with review count (only if reviews exist).
- Hotel detail: gallery, name, stars, address, description, reviews (score 1-5, text, date, optional reply from the hotel). Room offer: room type "Standart oda", rate plan "Kahvaltı dahil, esnek iptal", nights, cancellation rule "Ücretsiz iptal: 02.12.2026 00:00'a kadar" or "İade edilmez", total price. Empty state "Bu tarihlerde uygun oda yok" with the next free date. Choosing an offer locks the price for 15 minutes, then "Rezervasyon yap" or "Sepete ekle".
- Bus seats: free, selected, taken by a woman, taken by a man. Nobody may sit next to an unknown passenger of the other gender. Up to 5 seats; for each seat the passenger name and Kadın or Erkek; total price; "Sepete ekle".
- Cart: each item with a one-line summary and price, remove, optional promo code, a note that the final price is confirmed when the order is created, "Siparişi oluştur". A guest must log in and returns to the same place.
- Order: status Ödeme bekleniyor (15-minute payment deadline, pay button), Onaylandı, İptal edildi or Süresi doldu. Per paid item: QR voucher, validity "04.12.2026 20:30 - 23:30", voucher status Geçerli, Kullanıldı or İptal edildi, free cancellation deadline. Flight items get "Online check-in". Actions: cancel the order (refund amount shown first) and open a support request.
```

## Prompt 2: search and booking screens in the chosen direction

Replace `[DIRECTION]` with the name of the chosen direction (run it in the same project).

```
Continue the Waylo customer travel app (Turkish travel marketplace: hotels, domestic flights, car rentals, intercity buses, one cart, one payment, QR vouchers). Use exactly the style, components and navigation of the direction "[DIRECTION]" already on this canvas. Phone portrait 390x844.

Rules: all text Turkish with correct characters (ç ğ ı İ ö ş ü); money "₺7.220,00", dates "04.12.2026", 24-hour times; only real features (no loyalty points, maps, logos, car photos, live chat, social login); touch targets 44 pt, WCAG AA, never color alone.

Design these screens:
1. Flight search: departure and arrival airport shown as city + code, e.g. "İstanbul (IST)", and date.
2. Flight results: airline name, flight number "WD1332", departure code + time -> arrival code + time, duration, cabin Ekonomi or Business, price, seats left.
3. Flight detail: same facts, passenger first and last name, "Sepete ekle".
4. Car rental search: pickup branch and return branch (name + city; a different return branch is a one-way rental and may cost extra), pickup and return date and time, driver age. Inline error "Alış zamanı en az 2 saat sonrası olmalı".
5. Car offers: "Toyota Corolla veya benzeri", category (Ekonomi, Kompakt, SUV...), Otomatik or Manuel, fuel type, seats, rental days, daily and total price, deposit, "Sepete ekle".
6. Bus search (from city, to city, date) and trip list: company name, departure -> arrival (often overnight), seats left, price, "Koltuk seç".
7. Orders list: unpaid orders first, then newest. Card: item types, one-line description "Otobüs · İstanbul - Ankara, 04.12.2026 22:30, koltuk 3", status, total.
8. Unpaid order: 15-minute payment deadline countdown and pay button.
9. Cancel order: refund preview with the amount and the rule that applies, then confirm.
10. Flight online check-in (opens 24 hours, closes 1 hour before departure) and the result with the seat number.
11. Guest prompt "Devam etmek için giriş yap", an offline banner, empty and error states with "Tekrar dene".
```

## Prompt 3: account and support screens in the chosen direction

```
Continue the Waylo customer travel app (Turkish travel marketplace: hotels, domestic flights, car rentals, intercity buses, one cart, one payment, QR vouchers). Use exactly the style, components and navigation of the direction "[DIRECTION]" already on this canvas. Phone portrait 390x844.

Rules: all text Turkish with correct characters (ç ğ ı İ ö ş ü); dates "04.12.2026"; only real features (no social login, no loyalty, no live chat); touch targets 44 pt, WCAG AA.

Design these screens:
1. Login: e-mail, password, "Şifremi unuttum", link to register.
2. Register: first name, last name, e-mail, password with its rule shown: 10-128 characters, at least one letter and one digit, not the same as the e-mail.
3. "E-postanı doğrula": a verification link was sent to the address; and the success screen after the link is opened.
4. Forgot password and set a new password.
5. Profile: name, e-mail with a verified badge, edit name, change password, links "Yorum yazabileceğim konaklamalar", "Bildirimler", "Destek taleplerim", "Ayarlar", and "Çıkış yap".
6. Settings: change e-mail (a verification link goes to the new address, the old one keeps working until then) and a danger zone "Hesabımı sil" that asks for the password.
7. Support requests list: subject, status (Açık, Yanıtlandı, Kapalı), last update.
8. New support request for an order (optionally one item of it): subject and message.
9. One support request as a conversation: messages from me, from the hotel or company, and from Waylo; reply box; close request.
10. Reviews: stays I can review (after check-out) and writing a review (score 1-5 and text).
11. Notifications list: message and date.
```

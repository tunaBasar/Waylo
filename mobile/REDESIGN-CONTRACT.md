# Waylo mobile redesign: contract (2026-11-27)

The user rejected the current mobile UI and generated three directions with Google Stitch from `mobile/DESIGN-BRIEF.md`
(Anadolu Editoryal, Bosphorus Express, Gece Ekspresi). User decision 2026-11-27 14:00: "use these designs, remove the
AI slop". Chosen direction: **Bosphorus Express** (closest to the web app and the app icon, which are both blue; the
most data-first layout for a booking app; the dark Gece Ekspresi conflicts with the light web app).

Reference files (read-only, never ship them): `mobile/design/stitch-bosphorus-express/`
- `design-system.md` (tokens), `1-start` ... `6-order` (`.png` screenshot at 390 px, `.html` Stitch export).
- The HTML is Tailwind/web. It is a picture of the target, not code to copy: rebuild it with React Native components.

## 1. AI slop: rules that override the Stitch screens

Remove or replace everything below. If a screen and this list disagree, this list wins.

1. **No invented data or features.** Only fields the API returns (see `src/api/types.ts`). Not in the product:
   order numbers like `#WYL-84920`, voucher codes like `WYL-OTL-904`, an "Aktif Sepet" card on the start screen with a
   made-up total, "Üyeliksiz arama" badges, live promo validation (there is no endpoint: the code is checked when the
   order is created), maps, amenity lists, logos, car photos, loyalty, social login.
   Order id: show `Sipariş #` + the first 8 characters of the id (as today). Voucher: QR from `payload` + description +
   validity + status, nothing else.
2. **No fake loading content.** No skeleton card that says "Diğer oteller yükleniyor..." under real results. Show a
   spinner/skeleton only while a request is really running.
3. **No broken image alt text on screen.** When a photo is missing or fails, show the designed fallback (icon +
   "Fotoğraf yok"), never alt text. Use `expo-image` with a placeholder.
4. **Bus seat legend: never color alone.** Taken seats show a letter K (Kadın) or E (Erkek) and an accessibility label
   ("Koltuk 7, dolu, kadın yolcu"); the selected seat shows a check mark. Keep exactly the real rules:
   `GENDER_RULE_TEXT`, `MAX_SEATS` (5), `seatBlockedByGender` from `src/features/bus/seats.ts`.
5. **One primary action per area.** The cart has one "Siparişi oluştur" button; a guest sees one login prompt.
6. **Countdown only where it is real.** The 15-minute price lock comes from `quote.expiresAt`, the payment deadline
   from `order.holdExpiresAt`. Show a live mm:ss timer computed from those instants; never a static "14:42".
7. **Money and dates.** Money always through `money()` in `src/features/format.ts` (₺7.220,00); dates `dd.MM.yyyy` and
   24-hour times through `fmtLocal()` (Europe/Istanbul). Prices and times use the mono font with tabular numbers.
8. **Turkish text with correct characters** (ç ğ ı İ ö ş ü) for every NEW or CHANGED label. Do not churn text you do
   not touch, and keep the strings that existing tests assert (`__tests__/orders-screen.test.tsx`,
   `support-screen.test.tsx`, `deeplinks.test.tsx`, `api-sync.test.ts` and the feature tests) unless you update the test
   in the same change.
9. **Accessibility.** Every pressable has `accessibilityRole` and `accessibilityLabel` (enforced by
   `__tests__/a11y-screens.test.tsx`), touch targets at least 44 pt, WCAG AA contrast (check text on `primary`,
   `secondary-container`, `warning-container`), never color alone for status.
10. **Light mode only** (`app.json` `userInterfaceStyle: light`), no dark variant in this phase.
11. **Screen-specific slop in the Bosphorus pictures** (do not copy):
    - Start: "Misafir" chip, "Üyeliksiz arama" link, "Aktif Sepet ... Tek QR ödeme" card (there is one QR per item,
      not one per payment) and a bottom bar without an account tab.
    - Hotel detail: the "Fiyat 15 dakika kilitlendi" banner sits on the detail page in the picture, but the price is
      locked only after an offer is chosen: the banner belongs to the quote screen. Keep the real flow: offer ->
      quote screen (countdown + "Rezervasyon yap" / "Sepete ekle"). No buttons on the offer card except choosing it.
    - Cart: the "Uygula" button next to the promo field (nothing validates it before the order: use a plain field with
      the hint "Sipariş oluşturulurken uygulanır") and the guest login block (guests never reach the cart; the cart
      belongs to the signed-in account).
    - Bus: the sticky total bar covers the passenger form in the picture. A `StickyActionBar` never covers content:
      the scroll content gets bottom padding equal to the bar height.
    - Order: the `#WYL-84920` style number and voucher codes (see 1).

## 2. Design tokens (from design-system.md)

`src/theme/tokens.ts` exports plain objects, no runtime theming library:
- `color`: primary #003772, onPrimary #FFFFFF, primaryContainer #124E96, onPrimaryContainer #ECF0FF, secondary
  #006C53, secondaryContainer #D8F6EA, onSecondaryContainer #00382A, tertiary #743500, tertiaryContainer #FFEDE5,
  onTertiaryContainer #522300, success #006C53, successContainer #D8F6EA, warning #8C4A00, warningContainer #FFF0D9,
  onWarningContainer #4D2600, error #BA1A1A, errorContainer #FFDAD6, onErrorContainer #690005, background/surface
  #F4F7FA, surfaceLowest #FFFFFF, surfaceLow #EDF1F7, surfaceContainer #E4E9F2, surfaceHigh #DCE2EC, onSurface
  #191C20, onSurfaceVariant #424751, outline #737782, outlineVariant #C8D0DC.
- `space`: xs 4, sm 8, md 12, lg 16, xl 20, xxl 24, xxxl 32. `radius`: sm 4, md 8, lg 12, xl 16, full 9999.
- `font`: display = Wix Madefor Display (700/600), body = Instrument Sans (400/600/700), mono = Spline Sans Mono
  (500/600). `type` scale exactly as design-system.md (displayLgMobile 26/32, headlineLg 22/28, headlineMd 18/24,
  titleMd 15/20, bodyLg 15/22, bodyMd 13/18, labelLg 14/18, labelSm 12/16, monoMd 14/18, monoSm 12/16).
- Fonts via `npx expo install @expo-google-fonts/wix-madefor-display @expo-google-fonts/instrument-sans
  @expo-google-fonts/spline-sans-mono` (never npm/yarn add) and `useFonts` in the root layout while the splash screen
  is visible. If fonts fail to load, render with the system font (never block the app).
- Icons: `@expo/vector-icons` (MaterialCommunityIcons) via `npx expo install @expo/vector-icons`; no emoji icons.
  Icons are decorative (`accessibilityElementsHidden`); the label is on the pressable.

## 3. Components (`src/components/`)

Keep the existing exports of `src/components/ui.tsx` working (same names and props, restyled with the tokens):
`colors` (kept as an alias mapped to the new tokens), `Screen`, `Title`, `Muted`, `Card`, `Button` (`primary`,
`secondary`, `danger`), `Field`, `ErrorBanner`, `TestBanner`, `AsyncView`. Add in `src/components/`:
- `AppHeader` (cobalt bar: optional back button, title, subtitle in mono, optional right element),
- `ModeTabs` (Otel / Uçuş / Araç / Otobüs segmented pills with icons; navigates to the existing tabs),
- `Chip`/`StatusPill` (status with icon + text, tones success/warning/error/neutral),
- `Price` (mono, tabular), `CountdownBanner` (props: until ISO instant, label; live mm:ss; expired state),
- `StickyActionBar` (bottom bar with total + primary action, safe-area aware),
- `PhotoFallback`, `SkeletonCard` (only used while loading).
`Stepper`, `DateField`, `StarPicker` restyled with the tokens.

## 4. Navigation

- No route files move. The tab navigator keeps its six routes (`hotels`, `flights`, `cars`, `bus`, `orders`,
  `profile`); a custom `tabBar` (`src/components/BottomBar.tsx`) shows four items:
  - **Arama**: active on hotels, flights, cars and bus; opens the last used search mode (default hotels).
  - **Sepet**: count badge; pushes the existing `/cart` stack screen; a guest goes to login with returnTo `/cart`
    (same as the current header button, which is removed).
  - **Siparişler** and **Hesap** (profile): the current guest-to-login behaviour (`guestTab`) stays.
- Every effect in `(tabs)/_layout.tsx` stays (pending order deep link, `takeReturnTo` after login, `needsLogin`
  redirect). The four search screens keep their routes and query-param restore (`src/features/returnState.ts`, bus
  `draft.ts`); `ModeTabs` switches between them with `router.replace`.
- Tab screens draw their own `AppHeader` (search screens: "Waylo" wordmark, a guest sees one "Giriş yap" button).
  Stack screens (hotel/flight detail, quote, cart, order, support, reviews, notifications, settings, auth) get the
  cobalt header from the root `Stack` `screenOptions`.

## 5. Screen mapping

| Stitch screen | Route(s) | Notes |
|---|---|---|
| 1 Başlangıç | `(tabs)/hotels` (+ same header/ModeTabs on flights, cars, bus) | search card as in 1-start.png; no "Aktif Sepet" card; results may stay on the same screen (the form collapses into a summary row with "Değiştir"), no new routes |
| 2 Otel sonuçları | `(tabs)/hotels` results | horizontal split card: cover (or fallback), stars, name, city, score + count only if reviews exist, no price |
| 3 Otel detay | `hotel/[id]`, `quote/[id]` | gallery (`HotelGallery`), score, address, description, offers, empty state with next date, reviews + hotel reply; quote shows `CountdownBanner` from `expiresAt` |
| 4 Otobüs koltuk | `(tabs)/bus` seat picker | legend Boş/Seçili/Kadın/Erkek with letters, rule text, seat grid by layout, sticky total + "Sepete ekle", per seat name + Kadın/Erkek |
| 5 Sepet | `cart` | item rows (vertical, label, price, Kaldır), promo field (no live check), note "Nihai fiyat sipariş oluşturulduğunda kesinleşir.", sticky total + "Siparişi oluştur" |
| 6 Sipariş detay | `order/[id]` | header with status pill; per item QR voucher card (status pill, validity, free-cancel deadline); flight "Online check-in"; refund preview, "Siparişi iptal et", "Destek talebi aç"; pending: payment `CountdownBanner` from `holdExpiresAt` + pay buttons |

Screens without a Stitch picture (flights, cars, orders list, profile, settings, support, reviews, notifications,
login/register/forgot/verify/reset) use the same components and tokens; no new features.

## 6. Phases and file ownership (workers never touch another lane's files)

- **Phase 1, lane `mobile-foundation`:** `src/theme/**`, `src/components/**`, `src/app/_layout.tsx` (fonts, stack
  header), `src/app/(tabs)/_layout.tsx` (tab bar), `src/features/network/NetworkContext.tsx` (OfflineBanner look
  only), `package.json`/lockfile (`npx expo install` only), new tests for pure helpers in `__tests__/`.
- **Phase 2 (after phase 1 is merged), four parallel lanes:**
  - `mobile-hotel`: `src/app/(tabs)/hotels.tsx`, `src/app/hotel/[id].tsx`, `src/app/quote/[id].tsx`,
    `src/features/hotels/HotelGallery.tsx`.
  - `mobile-transport`: `src/app/(tabs)/bus.tsx`, `src/app/(tabs)/flights.tsx`, `src/app/flight/[id].tsx`,
    `src/app/(tabs)/cars.tsx`.
  - `mobile-orders`: `src/app/cart.tsx`, `src/app/order/[id].tsx`, `src/app/(tabs)/orders.tsx`.
  - `mobile-account`: `src/app/(tabs)/profile.tsx`, `src/app/settings.tsx`, `src/app/support/*`,
    `src/app/reviews.tsx`, `src/app/notifications.tsx`, `src/app/(auth)/*`, `src/app/verify.tsx`,
    `src/app/reset-password.tsx`.
  Each lane may add tests in `__tests__/` and update an existing test only for a string it changed.

## 7. Done means

- Workers: `cd mobile && npx tsc --noEmit` 0 errors and `npx expo lint` 0 problems; tests written for new pure logic;
  workers never run jest or start Expo (user rule).
- Coordinator: jest (all suites, including the a11y walk), then Expo web on the walk stack: each redesigned screen
  checked in a 390 px browser tab next to its Stitch png, with real data (guest and customer), plus one full purchase.

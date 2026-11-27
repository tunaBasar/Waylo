# Waylo mobile redesign: contract (2026-11-27, revision 2)

History: revision 1 rebuilt the app on the Stitch direction "Bosphorus Express" (commits 2cd53b2, 83e80a6, 59e5d02).
User decision 2026-11-27 15:47: "Ben Anadolu Editoryal ve Gece Ekspresi'ni beğenmiştim. Bu çok fazla 2000'ler internet
siteleri gibi duruyor, Facebook'u andırıyor." The coordinator had picked Bosphorus without asking; that was wrong.

**Revision 2 (this file):** one layout, two themes.
- **Light theme = Anadolu Editoryal** (warm paper, terracotta, serif headings, hairline-bordered cards).
- **Dark theme = Gece Ekspresi** (obsidian surfaces, amber accents, borderless tonal cards).
- The app follows the device setting (light/dark) and the user can override it in Ayarlar: Sistem / Açık / Koyu.
- On a wide web window the app is shown as a centered phone-width column (no stretched desktop page).

References (read-only, never ship them):
- `mobile/design/stitch-anadolu-editoryal/` and `mobile/design/stitch-gece-ekspresi/`: `design-system.md` (tokens),
  `1-start` ... `6-order` (`.png` at 390 px, `.html` Stitch export). Both directions draw the SAME six screens, so the
  layout below is shared; only the theme differs. The HTML is Tailwind/web: a picture of the target, never code to copy.

## 1. AI slop: rules that override the Stitch screens

If a picture and this list disagree, this list wins.

1. **No invented data or features.** Only fields the API returns (`src/api/types.ts`). Not in the product: order numbers
   like `#WYL-84920`, voucher codes like `WYL-OTL-904`, "Aktif Sepet" cards with made-up totals, live promo validation
   (no endpoint: the code is checked when the order is created, so no "Uygula" button and no "kod geçersiz" message
   before the order), maps, amenity lists, logos, car photos, loyalty, social login, "tek biletle" claims (each item
   gets its own QR voucher). Order id: `Sipariş #` + first 8 characters of the id. Voucher: QR from `payload` +
   description + validity + status + free-cancellation text, nothing else.
2. **No fake loading content**; spinner or skeleton only while a request really runs.
3. **No broken image alt text on screen**: missing or failed photo -> PhotoFallback (icon + "Kapak fotoğrafı yok").
4. **Bus seats never by color alone**: taken seats show K (Kadın) or E (Erkek), the selected seat a check icon, and an
   accessibility label ("Koltuk 7, dolu, kadın yolcu"). Keep the real rules (`GENDER_RULE_TEXT`, `MAX_SEATS`,
   `seatBlockedByGender` in `src/features/bus/seats.ts`).
5. **One primary action per area.** Guests never reach the cart (no guest block there); the cart has one
   "Siparişi oluştur".
6. **Countdowns only where real**: price lock from `quote.expiresAt` (quote screen, NOT the hotel page: the price is
   locked only after an offer is chosen), payment deadline from `order.holdExpiresAt`; live mm:ss, never a static text.
7. **Keep the real hotel flow**: offer card -> createQuote -> quote screen (countdown, "Rezervasyon yap", "Sepete ekle").
   The offer card has one action (choose); no booking buttons on the hotel page.
8. **Money** via `money()` (₺7.220,00), dates `dd.MM.yyyy`, 24-hour times via `fmtLocal()` (Europe/Istanbul); prices,
   times and seat numbers in the mono font with tabular numbers.
9. **Turkish with correct characters** everywhere; keep strings that tests assert unless the test changes with them.
10. **Accessibility**: every pressable has `accessibilityRole` + `accessibilityLabel` (enforced by
    `__tests__/a11y-screens.test.tsx`), at least 44 pt, WCAG AA contrast in BOTH themes, status never by color alone.
11. **A sticky bar never covers content**: scroll content gets bottom padding equal to the bar height.
12. **Bottom bar keeps 4 items** (Arama, Sepet, Siparişler, Hesap) in both themes; the 3-item Gece picture has no
    account tab, which the app needs.

## 2. Themes (`src/theme/`)

One `Theme` type, two values: `lightTheme` (Anadolu) and `darkTheme` (Gece). Same keys in both.

**color** (from the two design-system.md files):

| key | light (Anadolu) | dark (Gece) |
|---|---|---|
| primary / onPrimary | #973319 / #FFFFFF | #F59E0B / #1A120B |
| primaryContainer / onPrimaryContainer | #B84A2E / #FFECE8 | #4D2600 / #FFDCC3 |
| secondary / onSecondary | #466275 / #FFFFFF | #7BD0FF / #00354A |
| secondaryContainer / onSecondaryContainer | #C7E3FA / #173345 | #004C69 / #E3F3FF |
| tertiary / onTertiary | #005E6E / #FFFFFF | #FFB77D / #4D2600 |
| tertiaryContainer / onTertiaryContainer | #D8F6FF / #003640 | #6E3900 / #FFEDE3 |
| success / onSuccess | #1B6B44 / #FFFFFF | #4ADE80 / #052E16 |
| successContainer / onSuccessContainer | #DCF5E7 / #0E3F27 | #14532D / #DCFCE7 |
| warning / onWarning | #8C5100 / #FFFFFF | #FBBF24 / #1A120B |
| warningContainer / onWarningContainer | #FFF0D6 / #4D2B00 | #451A03 / #FEF3C7 |
| error / onError | #BA1A1A / #FFFFFF | #FCA5A5 / #450A0A |
| errorContainer / onErrorContainer | #FFDAD6 / #690005 | #7F1D1D / #FEE2E2 |
| background, surface | #FAF7F2 | #0F141B |
| surfaceLowest / surfaceLow | #FFFFFF / #F5EFE6 | #0B0F15 / #161D26 |
| surfaceContainer / surfaceHigh | #EDE5D8 / #E5DACB | #1C2531 / #253040 |
| onSurface / onSurfaceVariant | #241916 / #57423D | #F1F5F9 / #B6C2D1 |
| outline / outlineVariant | #8B716B / #D9C9BE | #64748B / #2B3748 |
| backdrop (web, outside the phone column) | #EDE5D8 | #0B0F15 |
| seatEmptyBg / seatEmptyBorder | #FFFFFF / #D9C9BE | #161D26 / #2B3748 |
| seatSelectedBg / seatSelectedFg | #973319 / #FFFFFF | #F59E0B / #1A120B |
| seatFemaleBg / seatFemaleFg | #D8F6FF / #003640 | #6E3900 / #FFEDE3 |
| seatMaleBg / seatMaleFg | #C7E3FA / #173345 | #004C69 / #E3F3FF |

**font / type**:
- light: display Crimson Pro 600 (headings), body Lato 400/700, mono Source Code Pro 500/600.
  displayLgMobile 28/34 (-0.25), headlineLg 24/30, headlineMd 20/26, titleMd Lato 700 16/22, bodyLg Lato 16/24,
  bodyMd Lato 14/20, labelLg Lato 700 14/20, labelSm Lato 700 12/16, monoMd SCP 600 14/20, monoSm SCP 500 12/16.
- dark: display/title/label Alegreya Sans 700, body Alegreya 400, mono Source Code Pro.
  displayLgMobile 28/34, headlineLg 22/28, headlineMd 18/24, titleMd 16/22, bodyLg 16/24, bodyMd 15/21,
  labelLg 14/20, labelSm 12/16, monoMd 14/20, monoSm 12/16.
- Packages via `npx expo install @expo-google-fonts/crimson-pro @expo-google-fonts/lato
  @expo-google-fonts/source-code-pro @expo-google-fonts/alegreya-sans @expo-google-fonts/alegreya`; remove the three
  Bosphorus font packages (`npm uninstall @expo-google-fonts/wix-madefor-display @expo-google-fonts/instrument-sans
  @expo-google-fonts/spline-sans-mono`) once nothing imports them. All fonts load behind the splash screen; on error the
  app continues with system fonts.

**shape / surfaces**:
- light: radius sm 6, md 12, lg 16, xl 20, full 9999; cards = surfaceLowest + 1 px outlineVariant hairline, no shadow.
- dark: radius sm 8, md 14, lg 20, xl 24, full 9999; cards = surfaceLow, no border (tonal layers); borders only on
  inputs and seat cells.
- `space` (4, 8, 12, 16, 20, 24, 32) and `MIN_TOUCH` (44) are theme-independent; page margin 20.

**API** (`src/theme/ThemeContext.tsx`): `ThemeProvider` (root layout), `useTheme(): Theme` (falls back to
`lightTheme` when no provider is mounted, so tests that render screens keep working), `useThemePreference()`
(`{ preference: 'system' | 'light' | 'dark', setPreference }`, persisted in AsyncStorage under `waylo_theme`; not a
secret), and `useStyles(factory: (t: Theme) => StyleSheet)` memoised per theme. Pure, tested helper:
`resolveScheme(preference, systemScheme) -> 'light' | 'dark'`. `app.json`: `userInterfaceStyle: "automatic"`, splash
with a dark variant (#0F141B) per the SDK 58 expo-splash-screen docs. Status bar text follows the theme.

**Migration rule**: no file may import `color`, `type`, `font` or `radius` from `@/theme/tokens` at the end of revision 2
(module-level StyleSheets cannot change with the theme). Phase A keeps them as `@deprecated` light aliases so the
not-yet-migrated files still compile; phase C deletes them and `tsc` proves nothing still uses them.

## 3. Shared layout (both themes)

- **Web frame**: on web, the whole app (headers, screens, bottom bar) lives in a centered column `maxWidth: 480`,
  full height, `backdrop` color around it, hairline side borders in light. Narrow screens: full width.
- **Headers**: no colored bar. Surface background. Tab screens: wordmark "Waylo" (display font, a small primary dot or
  mark) left, guest "Giriş yap" pill (outlined, person icon) right. Stack screens: round outlined back button (44 pt),
  title in the display font, optional subtitle in mono (dates, "2 gece"). Stack navigator `screenOptions` from the
  theme: surface background, onSurface tint, display-font title, no shadow.
- **Mode tiles** (Otel / Uçuş / Araç / Otobüs): four square tiles in a row, icon above label; selected = primary bg.
- **Search card** (1-start): Şehir; Giriş and Çıkış side by side; a guests row "1 oda · 2 yetişkin · 1 çocuk (6 yaş)"
  that expands inline into the steppers (oda, yetişkin, çocuk + one age stepper per child); primary "Otelleri ara".
  Light start screen may show the honest intro "Yolculuğunuzu tek yerden planlayın" + "Üye olmadan otel, uçuş, araç ve
  otobüs seferlerini arayabilirsiniz."
- **Hotel results** (2): title "{şehir} otelleri" (or "Oteller"), mono subtitle with dates and guests, line
  "{n} tesis listeleniyor · Oda fiyatları otel detayında gösterilir"; full-width cards with the cover photo on top
  (or PhotoFallback), stars ("4 yıldız"), score chip only when reviews exist (`scoreText`), name in the display font,
  city and country.
- **Hotel detail** (3): gallery with "1 / N" counter and thumbnails, stars, name, address, description, "Oda teklifleri"
  (offer cards: room type, rate plan, nights, cancellation rule, mono price, chevron), empty state card with calendar
  icon and the next-date button, "Değerlendirmeler" with score and review cards, hotel reply in an inset box.
- **Quote**: countdown banner (warning container, timer icon, "Fiyat 15 dakika kilitlendi", live mm:ss), summary card,
  promo field, "Rezervasyon yap" (primary) and "Sepete ekle" (secondary).
- **Bus** (4): trip cards; seat picker = legend chips (Boş, Seçili ✓, Kadın K, Erkek E), rule info box with info
  icon, a seat map card with header "Ön · Kaptan" and "{2+2 | 2+1} · {n} koltuk" (from the seat map), row numbers and
  aisle; "Yolcu bilgileri ({n} / 5 koltuk)" with one card per seat (Koltuk 17 + mono price, Ad soyad, Kadın/Erkek
  toggle); sticky bar "Toplam ({n} koltuk)" + "Sepete ekle".
- **Cart** (5): title "Sepetim ({n} ürün)"; item cards (vertical icon, label, mono price, "Kaldır"); promo field with the
  hint "Sipariş oluşturulurken uygulanır"; info note "Nihai fiyat sipariş oluşturulduğunda kesinleşir."; sticky bar
  "Toplam tutar" + "Siparişi oluştur".
- **Order** (6): "Sipariş #xxxxxxxx" + status pill; pending: countdown + TEST pay buttons + TestBanner; per item card:
  "{Tür} · {başlık}", mono validity, status pill "Geçerli", inset panel with the QR and the free-cancellation text;
  flight items "Online check-in" (secondary color); "Siparişi iptal et" (error style, refund preview first, as today);
  "Destek talebi aç".
- **Bottom bar**: 4 items with icon above label. Light: docked, surfaceLowest, hairline top border. Dark: floating
  rounded bar (radius xl, surfaceContainer, margin 12) like the Gece picture.
- Other screens (flights, cars, orders list, profile, settings, support, reviews, notifications, auth) use the same
  components and tokens. **Ayarlar** gets a "Görünüm" section: Sistem / Açık / Koyu (radio rows), applied at once.

## 4. Phases and file ownership

- **Phase A, lane `theme-core`**: `src/theme/**`, `src/components/**`, `src/app/_layout.tsx` (fonts, ThemeProvider,
  Stack header from theme, status bar, web frame), `src/app/(tabs)/_layout.tsx` (only theme-related lines),
  `src/features/network/NetworkContext.tsx`, `app.json`, `package.json`/lockfile, tests for pure helpers.
- **Phase B, four parallel lanes** (same files as revision 1, migrated to `useTheme`/`useStyles` and restyled):
  - `theme-hotel`: `src/app/(tabs)/hotels.tsx`, `src/app/hotel/[id].tsx`, `src/app/quote/[id].tsx`,
    `src/features/hotels/HotelGallery.tsx`.
  - `theme-transport`: `src/app/(tabs)/bus.tsx`, `src/app/(tabs)/flights.tsx`, `src/app/flight/[id].tsx`,
    `src/app/(tabs)/cars.tsx` (bus seat colors from the theme `seat*` tokens, no hard-coded colors).
  - `theme-orders`: `src/app/cart.tsx`, `src/app/order/[id].tsx`, `src/app/(tabs)/orders.tsx`.
  - `theme-account`: `src/app/(tabs)/profile.tsx`, `src/app/settings.tsx` (+ Görünüm), `src/app/support/*`,
    `src/app/reviews.tsx`, `src/app/notifications.tsx`, `src/app/(auth)/*`, `src/app/verify.tsx`,
    `src/app/reset-password.tsx`, `src/features/account/AccountUi.tsx`.
- **Phase C, lane `theme-cleanup`**: delete the deprecated token aliases, fix any remaining import, remove the Bosphorus
  font packages, prove with grep + tsc that no file imports color/type/font/radius from tokens and no hex color literal
  remains outside `src/theme/`.

## 5. Done means

- Workers: `cd mobile && npx tsc --noEmit` and `npx expo lint` clean (read the output, not only the exit code); tests
  written for new pure logic; workers never run jest, Expo or a browser (user rule).
- Coordinator: jest (all suites, incl. the a11y walk); Expo web at 390 px in light AND dark (Playwright
  `colorScheme`) next to the Stitch pictures; a 1440 px window shows the centered phone column; the Ayarlar switch
  changes the theme at once; one full customer purchase.

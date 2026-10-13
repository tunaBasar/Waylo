---
name: Modern Travel Platform
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#434655'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#737686'
  outline-variant: '#c3c6d7'
  surface-tint: '#0053db'
  primary: '#004ac6'
  on-primary: '#ffffff'
  primary-container: '#2563eb'
  on-primary-container: '#eeefff'
  inverse-primary: '#b4c5ff'
  secondary: '#006c4a'
  on-secondary: '#ffffff'
  secondary-container: '#82f5c1'
  on-secondary-container: '#00714e'
  tertiary: '#632ecd'
  on-tertiary: '#ffffff'
  tertiary-container: '#7d4ce7'
  on-tertiary-container: '#f6edff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b4c5ff'
  on-primary-fixed: '#00174b'
  on-primary-fixed-variant: '#003ea8'
  secondary-fixed: '#85f8c4'
  secondary-fixed-dim: '#68dba9'
  on-secondary-fixed: '#002114'
  on-secondary-fixed-variant: '#005137'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-xl:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.015em
  headline-xl-mobile:
    fontFamily: Inter
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-mobile: 1rem
  margin: 2rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

The design system embodies a high-end travel commerce and dashboard ecosystem that balances transactional efficiency with aspirational travel discovery. Built for modern Turkish leisure and business travelers seeking curated flight, hotel, and transfer bundles, the visual identity pairs the technical precision of enterprise SaaS with the inviting warmth of leisure hospitality.

The design movement combines **Corporate Modern** with **Refined Tactility**:
- Crisp, low-contrast border infrastructure that creates clarity in complex multi-item booking flows.
- Deep, atmospheric contrast between pristine light canvas tones and dark slate structural anchors.
- High visual legibility tailored specifically to Turkish diacritics (ç, ğ, ı, ö, ş, ü, İ) across high-density data tables and promotional package cards.
- Restrained color application, reserving vibrant saturations for call-to-actions, booking status indicators, and contextual pricing metrics.

## Colors

The color architecture is built around functional separation and situational status:

- **Primary (`#2563EB` — Cobalt Sky):** Guides the core booking flow, primary search triggers, active navigation markers, and step-by-step itinerary progression.
- **Secondary (`#059669` — Emerald Coast):** Reserved for financial confirmations, package savings highlights, confirmed reservation badges, and value metrics.
- **Tertiary (`#8B5CF6` — Royal Violet):** Designates curated bundle tiers, VIP perk callouts, and loyalty status indicators.
- **Warning (`#F59E0B` — Amber Horizon):** Communicates dynamic seat/room availability, urgent checkout expirations, and departure gate alterations.
- **Danger (`#EF4444` — Rose Alert):** Strictly reserved for destructive actions, failed payment states, and critical cancellation deadlines.
- **Neutrals (Slate Hierarchy):** 
  - Canvas Base: `#F8FAFC` (Slate 50)
  - Surface Default: `#FFFFFF` (White)
  - Surface Inactive/Hover: `#F1F5F9` (Slate 100)
  - Border Subdued: `#E2E8F0` (Slate 200)
  - Text Muted: `#64748B` (Slate 500)
  - Text Strong: `#0F172A` (Slate 900)

## Typography

Inter serves as the unified typographic engine across all digital touchpoints. Its neutral neo-grotesque geometry provides clean vertical alignments for numbers, flight codes, currency strings (`TRY`, `EUR`, `USD`), and complex Turkish terminology (`Rezervasyonlarım`, `Kişiselleştir`).

Typography guidelines:
- Apply tabular figures (`font-variant-numeric: tabular-nums`) to all price matrices, flight durations, and booking timers.
- Use negative tracking on titles (`display-lg`, `headline-xl`) to preserve density on promotional banners.
- Keep label typography (`label-sm`, `label-md`) in medium-to-semibold weights to ensure readability on small badges and status indicators.

## Layout & Spacing

The layout follows a strict 8pt base grid implemented through an adaptive 12-column layout on desktop and tablet, collapsing to a single-column or 4-column flow on mobile.

Breakpoint rules:
- **Desktop (>= 1280px):** 12 columns, max content container width of 1280px, fixed 24px gutters, dynamic horizontal centering margins.
- **Tablet (768px – 1279px):** 8 columns, 24px margins, 16px gutters. Dashboard filters collapse into off-canvas side sheets.
- **Mobile (< 768px):** 4 columns, 16px margins, 12px gutters. Flight cards and package comparison matrices reflow into vertically stacked lists or swipeable horizontal carousels.

## Elevation & Depth

Visual hierarchy uses a hybrid system combining low-contrast boundary lines (`#E2E8F0`) with soft, diffused neutral slate drops:

1. **Level 0 (Flat Surface):** Background surfaces (`#F8FAFC`). No border, no shadow.
2. **Level 1 (Card / Content Panels):** White surface (`#FFFFFF`) framed by a crisp 1px solid border in Slate 200 (`#E2E8F0`) paired with subtle ambient diffusion: `0 1px 3px 0 rgba(15, 23, 42, 0.05), 0 1px 2px -1px rgba(15, 23, 42, 0.05)`.
3. **Level 2 (Hover States / Floating Toolbars):** Used when the user hovers over travel package cards or when toolbars stick to the viewport: `0 4px 6px -1px rgba(15, 23, 42, 0.07), 0 2px 4px -2px rgba(15, 23, 42, 0.05)`. Border remains active.
4. **Level 3 (Modals / Booking Sheets / Flight Select Dropdowns):** Crisp border preserved alongside higher elevation depth: `0 20px 25px -5px rgba(15, 23, 42, 0.1), 0 8px 10px -6px rgba(15, 23, 42, 0.08)`.

## Shapes

The design system operates at roundedness level 2. 

- Interactive targets, including inputs, standard buttons, and small dropdowns, feature 8px (`0.5rem`) corner rounding.
- High-level containers, summary cards, travel package tiles, and modal dialogs adopt 16px (`1rem` / `rounded-xl`) corner radiuses.
- Badges, status chips, and flight segment connector points use full pill rounding (`9999px`) to create clear contrast against card layouts.

## Components

### Buttons
- **Primary:** Background `#2563EB`, text `#FFFFFF`, rounded 8px (`rounded-lg`), bold micro-interactions with `#1D4ED8` on hover. Disabled state uses `#94A3B8` background.
- **Secondary:** White background, 1px solid border `#CBD5E1`, text `#0F172A`. Hover transitions to `#F8FAFC`.
- **Tertiary (Subtle):** Transparent background, text `#2563EB`, hover background `#EFF6FF`.

### Cards (Package & Itinerary)
- Standard package card uses `#FFFFFF` background, `rounded-xl` (16px radius), 1px solid `#E2E8F0` border, and Level 1 elevation.
- Cards maintain distinct segments: top visual/destination block, middle tabular flight/hotel summary with `#F1F5F9` divider lines, and bottom action footer hosting Turkish price displays (e.g., `14.850 TL'den başlayan fiyatlarla`).

### Input Fields & Search Bars
- Booking bar components utilize `#FFFFFF` background with 1px border `#CBD5E1`.
- Focused state applies a dual-ring: 1px border `#2563EB` and a 3px outer ring in `rgba(37, 99, 235, 0.15)`.
- Includes calendar, room, and destination picker slots with prefix icon support and Turkish placeholder copy (e.g., `Nereye gitmek istersiniz?`).

### Chips & Status Badges
- **Confirmed / Approved (`Onaylandı`):** Emerald tint `#ECFDF5`, text `#065F46`, border `#A7F3D0`.
- **Pending / In Review (`Beklemede`):** Amber tint `#FFFBEB`, text `#92400E`, border `#FDE68A`.
- **VIP Package (`Özel Paket`):** Purple tint `#F5F3FF`, text `#5B21B6`, border `#DDD6FE`.
- All chips adopt pill radii, uppercase typography via `label-sm`, and inline vector icons.

### Selection Controls (Checkboxes & Radios)
- Checkboxes maintain 4px corner rounding; radio buttons are circular.
- Unchecked: 1.5px border `#94A3B8`, background `#FFFFFF`.
- Checked: Background `#2563EB` with pure white vector marks.

### Travel-Specific Components
- **Flight Timeline Stepper:** Linear progression markers connecting departure and arrival airports with transit dots; 2px dashed border `#CBD5E1` linking nodes.
- **Pricing Breakdown Sheet:** Segmented receipt component showing flights, hotel nights, transfer fees, and total package discount highlights (`Paket Tasarrufu`).
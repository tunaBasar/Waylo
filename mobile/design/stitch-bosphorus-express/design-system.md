---
name: "Bosphorus Express"
description: "High-efficiency Swiss-transit utility built for fast multi-modal booking—compact density, segmented pill navigation, crisp cobalt & emerald signals, and structured data-first cards."
---

## Overview

Bosphorus Express treats multi-modal Turkish travel like a precision transit timetable. Built for speed and scannability, it uses a cool ice-white ground, high-contrast Bosphorus cobalt actions, emerald transit confirmations, and compact horizontal split cards. Every screen prioritizes tabular alignment, immediate status clarity, and rapid one-handed switching between Otel, Uçuş, Araç, and Otobüs.

## Colors

Deep Bosphorus Cobalt (`primary`) anchors primary actions and active transit modes, paired with Emerald Transit (`secondary`) for confirmed availability and valid QR vouchers.

```yaml
colors:
  primary: "#003772"
  on-primary: "#FFFFFF"
  primary-container: "#124E96"
  on-primary-container: "#ECF0FF"
  secondary: "#006C53"
  on-secondary: "#FFFFFF"
  secondary-container: "#D8F6EA"
  on-secondary-container: "#00382A"
  tertiary: "#743500"
  on-tertiary: "#FFFFFF"
  tertiary-container: "#FFEDE5"
  on-tertiary-container: "#522300"
  success: "#006C53"
  on-success: "#FFFFFF"
  success-container: "#D8F6EA"
  on-success-container: "#00382A"
  warning: "#8C4A00"
  on-warning: "#FFFFFF"
  warning-container: "#FFF0D9"
  on-warning-container: "#4D2600"
  error: "#BA1A1A"
  on-error: "#FFFFFF"
  error-container: "#FFDAD6"
  on-error-container: "#690005"
  background: "#F4F7FA"
  on-background: "#191C20"
  surface: "#F4F7FA"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#EDF1F7"
  surface-container: "#E4E9F2"
  surface-container-high: "#DCE2EC"
  on-surface: "#191C20"
  on-surface-variant: "#424751"
  outline: "#737782"
  outline-variant: "#C8D0DC"
```

## Typography

Geometric precision pairs **Wix Madefor Display** for sharp, compact headers with **Instrument Sans** for ultra-clean Turkish UI labels and **Spline Sans Mono** for tabular times, prices, and seat numbers.

```yaml
typography:
  display-lg:
    fontFamily: "Wix Madefor Display"
    fontSize: 34px
    fontWeight: 700
    lineHeight: 40px
    letterSpacing: -0.5px
  display-lg-mobile:
    fontFamily: "Wix Madefor Display"
    fontSize: 26px
    fontWeight: 700
    lineHeight: 32px
    letterSpacing: -0.4px
  headline-lg:
    fontFamily: "Wix Madefor Display"
    fontSize: 22px
    fontWeight: 700
    lineHeight: 28px
    letterSpacing: -0.3px
  headline-md:
    fontFamily: "Wix Madefor Display"
    fontSize: 18px
    fontWeight: 600
    lineHeight: 24px
    letterSpacing: -0.2px
  title-md:
    fontFamily: "Instrument Sans"
    fontSize: 15px
    fontWeight: 700
    lineHeight: 20px
    letterSpacing: 0px
  body-lg:
    fontFamily: "Instrument Sans"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 22px
    letterSpacing: 0px
  body-md:
    fontFamily: "Instrument Sans"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 18px
    letterSpacing: 0px
  label-lg:
    fontFamily: "Instrument Sans"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 18px
    letterSpacing: 0.1px
  label-sm:
    fontFamily: "Instrument Sans"
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: 0.2px
  mono-md:
    fontFamily: "Spline Sans Mono"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 18px
    letterSpacing: 0px
  mono-sm:
    fontFamily: "Spline Sans Mono"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 16px
    letterSpacing: 0.1px
```

## Layout & Spacing

Compact, structured grid (390px mobile) with 16px outer margins and tight 8px–12px intra-card gaps for high information density without sacrificing 44px minimum touch targets.

```yaml
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  2xl: 24px
  3xl: 32px
```

## Elevation & Depth

Crisp structural grid with 1px `outline-variant` borders on pure white `surface-container-lowest` cards over cool `surface` backgrounds. Sticky top segmented headers and bottom action bars use subtle 1px dividers.

## Shapes

Tight, technical radii (`12px` for cards, `8px` for inputs, buttons, and thumbnails, `9999px` for status pills).

```yaml
rounded:
  sm: 4px
  DEFAULT: 8px
  md: 8px
  lg: 12px
  xl: 16px
  full: 9999px
```

## Motion & Micro-interaction

Snappy, mechanical transit feedback. Segment switches and seat taps respond in 90ms with zero bounce.

```yaml
motion:
  state-change:
    duration: 90ms
    delay: 0ms
    timingFunction: ease-out
  surface-enter:
    duration: 180ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0, 0, 1)
```

## Voice & Content Strategy

Direct, concise, action-oriented Turkish (`"Hızlı ara"`, `"Fiyat 15:00 dakika kilitlendi"`). Tabular numerals on all prices (`₺7.220,00`), dates (`04.12.2026`), and 24-hour schedules (`22:30 - 05:00`).

## Components

Component implementations live as individual HTML sticker sheet files in
`components/`. Each file contains the canonical, styled HTML components with
`<!-- COMPONENT: name -->` comment markers. Read each file before use.

- **Badge** → `@components/badge.html` — `badge-numeric`, `badge-dot`
- **Button** → `@components/button.html` — `button-primary`, `button-icon`, `button-with-icon`
- **Card** → `@components/card.html` — `card-elevated`, `card-action`, `card-interactive`, `card-interactive-2`
- **Input Field** → `@components/input-field.html` — `input-field-text`, `input-field-icon`

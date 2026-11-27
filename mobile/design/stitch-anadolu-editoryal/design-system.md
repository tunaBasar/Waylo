---
name: "Anadolu Editoryal"
description: "Warm editorial travel magazine meets tactile paper-ticket utility—generous whitespace, serif-led headings, terracotta & Aegean slate palette, and crisp hairline-bordered cards."
---

## Overview

Anadolu Editoryal brings the unhurried clarity of a curated Turkish travel journal to multi-modal booking. Designed for travellers planning stays and intercity journeys in Turkish, it pairs warm alabaster paper surfaces with terracotta accents and Aegean slate ink. The layout breathes with generous vertical rhythm, photo-forward hotel cards, and crisp 1px hairline borders that evoke printed boarding passes and boutique stationery.

## Colors

The palette pairs warm terracotta (`primary`) with deep Aegean slate (`secondary`) on a soft alabaster paper ground (`surface`). Semantic status tokens (`success`, `warning`, `error`) are strictly reserved for booking, payment, and voucher states, always paired with icons and text labels—never color alone.

```yaml
colors:
  primary: "#973319"
  on-primary: "#FFFFFF"
  primary-container: "#B84A2E"
  on-primary-container: "#FFECE8"
  secondary: "#466275"
  on-secondary: "#FFFFFF"
  secondary-container: "#C7E3FA"
  on-secondary-container: "#173345"
  tertiary: "#005E6E"
  on-tertiary: "#FFFFFF"
  tertiary-container: "#D8F6FF"
  on-tertiary-container: "#003640"
  success: "#1B6B44"
  on-success: "#FFFFFF"
  success-container: "#DCF5E7"
  on-success-container: "#0E3F27"
  warning: "#8C5100"
  on-warning: "#FFFFFF"
  warning-container: "#FFF0D6"
  on-warning-container: "#4D2B00"
  error: "#BA1A1A"
  on-error: "#FFFFFF"
  error-container: "#FFDAD6"
  on-error-container: "#690005"
  background: "#FAF7F2"
  on-background: "#241916"
  surface: "#FAF7F2"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#F5EFE6"
  surface-container: "#EDE5D8"
  surface-container-high: "#E5DACB"
  on-surface: "#241916"
  on-surface-variant: "#57423D"
  outline: "#8B716B"
  outline-variant: "#D9C9BE"
```

## Typography

Editorial contrast pairs **Crimson Pro** for expressive, authoritative headings with **Lato** for warm, highly legible Turkish UI copy and **Source Code Pro** for tabular prices, PNR codes, and timestamps.

```yaml
typography:
  display-lg:
    fontFamily: "Crimson Pro"
    fontSize: 36px
    fontWeight: 600
    lineHeight: 42px
    letterSpacing: -0.4px
  display-lg-mobile:
    fontFamily: "Crimson Pro"
    fontSize: 28px
    fontWeight: 600
    lineHeight: 34px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: "Crimson Pro"
    fontSize: 24px
    fontWeight: 600
    lineHeight: 30px
    letterSpacing: -0.2px
  headline-md:
    fontFamily: "Crimson Pro"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 26px
    letterSpacing: -0.1px
  title-md:
    fontFamily: "Lato"
    fontSize: 16px
    fontWeight: 700
    lineHeight: 22px
    letterSpacing: 0px
  body-lg:
    fontFamily: "Lato"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
    letterSpacing: 0px
  body-md:
    fontFamily: "Lato"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
    letterSpacing: 0.1px
  label-lg:
    fontFamily: "Lato"
    fontSize: 14px
    fontWeight: 700
    lineHeight: 20px
    letterSpacing: 0.2px
  label-sm:
    fontFamily: "Lato"
    fontSize: 12px
    fontWeight: 700
    lineHeight: 16px
    letterSpacing: 0.3px
  mono-md:
    fontFamily: "Source Code Pro"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 20px
    letterSpacing: 0px
  mono-sm:
    fontFamily: "Source Code Pro"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 16px
    letterSpacing: 0.2px
```

## Layout & Spacing

Mobile-first single-column fluid grid (390px target width) with generous 20px outer page margins and 16px–24px vertical section rhythm. Every interactive target maintains at least 44px height/width for comfortable thumb navigation.

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

Flat paper-first hierarchy using crisp 1px `outline-variant` hairline borders and subtle warm surface-container stepping (`surface` → `surface-container-lowest` → `surface-container-low`). No heavy drop shadows or glassmorphism blurs.

## Shapes

Softly rounded editorial corners (`16px` for primary cards, `12px` for inputs and inner media, `9999px` for mode pills and status badges).

```yaml
rounded:
  sm: 6px
  DEFAULT: 12px
  md: 12px
  lg: 16px
  xl: 20px
  full: 9999px
```

## Motion & Micro-interaction

Unobtrusive, crisp paper-like transitions. Interactive presses settle in 120ms; drawers and modal sheets decelerate smoothly over 240ms.

```yaml
motion:
  state-change:
    duration: 120ms
    delay: 0ms
    timingFunction: ease-out
  surface-enter:
    duration: 240ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0, 0, 1)
```

## Voice & Content Strategy

Warm, clear, and polite Turkish (`"Misafir olarak arayabilirsiniz"`, `"Nihai fiyat sipariş oluşturulduğunda kesinleşir"`). Sentence case for headings and buttons. Dates always `DD.MM.YYYY`, 24-hour times (`22:30`), and Turkish Lira formatted as `₺7.220,00`.

## Components

Component implementations live as individual HTML sticker sheet files in
`components/`. Each file contains the canonical, styled HTML components with
`<!-- COMPONENT: name -->` comment markers. Read each file before use.

- **Badge** → `@components/badge.html` — `badge-numeric`, `badge-dot`
- **Button** → `@components/button.html` — `button-primary`, `button-icon`, `button-with-icon`
- **Card** → `@components/card.html` — `card-elevated`, `card-action`, `card-interactive`, `card-interactive-2`
- **Input Field** → `@components/input-field.html` — `input-field-text`, `input-field-icon`

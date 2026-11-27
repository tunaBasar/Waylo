---
name: "Gece Ekspresi"
description: "Calm dark-mode night-traveler companion designed for late-night bus and flight bookings—deep obsidian surfaces, amber lantern accents, soft tonal layers, and thumb-friendly bottom sheets."
---

## Overview

Gece Ekspresi is crafted for evening and overnight travellers booking intercity buses, late flights, and last-minute stays without eye strain. Deep obsidian-slate surfaces replace harsh white backgrounds, while warm amber lantern accents highlight primary actions and active selections. Content sits in soft, borderless tonal containers with thumb-zone bottom action bars.

## Colors

Deep obsidian-navy surfaces (`#0F141B`, `#18202B`, `#202B38`) avoid pure `#000000` halation, paired with warm amber (`#F59E0B`) and dark ink (`#1A120B`) for high-contrast WCAG AA primary buttons, plus sky-cyan (`#7BD0FF`) for secondary transit accents.

```yaml
colors:
  primary: "#F59E0B"
  on-primary: "#1A120B"
  primary-container: "#4D2600"
  on-primary-container: "#FFDCC3"
  secondary: "#7BD0FF"
  on-secondary: "#00354A"
  secondary-container: "#004C69"
  on-secondary-container: "#E3F3FF"
  tertiary: "#FFB77D"
  on-tertiary: "#4D2600"
  tertiary-container: "#6E3900"
  on-tertiary-container: "#FFEDE3"
  success: "#4ADE80"
  on-success: "#052E16"
  success-container: "#14532D"
  on-success-container: "#DCFCE7"
  warning: "#FBBF24"
  on-warning: "#1A120B"
  warning-container: "#451A03"
  on-warning-container: "#FEF3C7"
  error: "#FCA5A5"
  on-error: "#450A0A"
  error-container: "#7F1D1D"
  on-error-container: "#FEE2E2"
  background: "#0F141B"
  on-background: "#F1F5F9"
  surface: "#0F141B"
  surface-container-lowest: "#0B0F15"
  surface-container-low: "#161D26"
  surface-container: "#1C2531"
  surface-container-high: "#253040"
  on-surface: "#F1F5F9"
  on-surface-variant: "#B6C2D1"
  outline: "#64748B"
  outline-variant: "#2B3748"
```

## Typography

A calm, distinctive humanist pairing: **Alegreya Sans** for clear, approachable Turkish headings, **Alegreya** for relaxed editorial body copy, and **Source Code Pro** for crisp monospace voucher codes and 24-hour times.

```yaml
typography:
  display-lg:
    fontFamily: "Alegreya Sans"
    fontSize: 34px
    fontWeight: 700
    lineHeight: 40px
    letterSpacing: -0.3px
  display-lg-mobile:
    fontFamily: "Alegreya Sans"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 34px
    letterSpacing: -0.2px
  headline-lg:
    fontFamily: "Alegreya Sans"
    fontSize: 22px
    fontWeight: 700
    lineHeight: 28px
    letterSpacing: -0.1px
  headline-md:
    fontFamily: "Alegreya Sans"
    fontSize: 18px
    fontWeight: 700
    lineHeight: 24px
    letterSpacing: 0px
  title-md:
    fontFamily: "Alegreya Sans"
    fontSize: 16px
    fontWeight: 700
    lineHeight: 22px
    letterSpacing: 0px
  body-lg:
    fontFamily: "Alegreya"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
    letterSpacing: 0px
  body-md:
    fontFamily: "Alegreya"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 21px
    letterSpacing: 0px
  label-lg:
    fontFamily: "Alegreya Sans"
    fontSize: 14px
    fontWeight: 700
    lineHeight: 20px
    letterSpacing: 0.2px
  label-sm:
    fontFamily: "Alegreya Sans"
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
    letterSpacing: 0.1px
```

## Layout & Spacing

Comfortable thumb-zone layout (390px mobile) with 16px–20px margins, rounded bottom-sheet-style content blocks, and sticky bottom CTAs. All interactive controls are at least 44px tall.

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

Tonal layering in dark mode: `surface` (`#0F141B`) → `surface-container-low` (`#161D26`) → `surface-container` (`#1C2531`) → `surface-container-high` (`#253040`). No drop shadows; subtle `outline-variant` borders only on interactive inputs and seat cells.

## Shapes

Generous, tactile curves (`20px` for primary cards and bottom sheets, `14px` for inputs and seat buttons, `9999px` for pills and status chips).

```yaml
rounded:
  sm: 8px
  DEFAULT: 14px
  md: 14px
  lg: 20px
  xl: 24px
  full: 9999px
```

## Motion & Micro-interaction

Calm, unhurried transitions. State changes settle over 150ms; bottom sheets glide in smoothly over 280ms.

```yaml
motion:
  state-change:
    duration: 150ms
    delay: 0ms
    timingFunction: ease-out
  surface-enter:
    duration: 280ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0, 0, 1)
```

## Voice & Content Strategy

Calm, reassuring Turkish (`"Yolculuğunuz hazır"`, `"Koltuk seçiminiz sepete eklendi"`). Clear, unambiguous error messages with immediate recovery actions.

## Components

Component implementations live as individual HTML sticker sheet files in
`components/`. Each file contains the canonical, styled HTML components with
`<!-- COMPONENT: name -->` comment markers. Read each file before use.

- **Badge** → `@components/badge.html` — `badge-numeric`, `badge-dot`
- **Button** → `@components/button.html` — `button-primary`, `button-icon`, `button-with-icon`
- **Card** → `@components/card.html` — `card-elevated`, `card-action`, `card-interactive`, `card-interactive-2`
- **Input Field** → `@components/input-field.html` — `input-field-text`, `input-field-icon`

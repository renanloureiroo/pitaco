# Binance Design System

## Overview

Binance reads like a financial trading platform that wants to feel both authoritative and energetic. The base atmosphere is **deep near-black canvas** (`{colors.canvas-dark}` — #0b0e11) holding white type and a single, ubiquitous accent: **Binance Yellow** (`{colors.primary}` — #FCD535). That yellow does almost all of the brand's heavy lifting.

Type runs Binance's custom **BinanceNova** (display + body) and **BinancePlex** (numerical / financial display) stack.
*Note: Inter and JetBrains Mono are used as substitutes in this project.*

The product is **multi-theme**: marketing surfaces (homepage, smart-money, futures arena) default to dark, while transactional surfaces (buy crypto, deposit, withdraw) flip to a light theme.

**Key Characteristics:**
- Single accent color: `{colors.primary}` (#FCD535) does all brand voltage.
- Custom type stack: `BinanceNova` (Inter) and `BinancePlex` (JetBrains Mono).
- Multi-theme: marketing pages default dark; transactional pages flip light.
- Light footer on dark body.
- Trading semantics: green up / red down for price changes, applied as text color.
- Card surfaces: Elevated cards on dark, flat color blocks.
- Border radius is small to medium (6px to 12px, pill for prominent CTAs).
- Spacing follows a 4-multiple scale.

## Colors

### Brand & Accent
- **Binance Yellow**: `#FCD535`
- **Binance Yellow Active**: `#f0b90b`
- **Binance Yellow Disabled**: `#3a3a1f`
- **Accent Turquoise**: `#2dbdb6`

### Surface
**Dark mode:**
- **Canvas Dark**: `#0b0e11`
- **Surface Card Dark**: `#1e2329`
- **Surface Elevated Dark**: `#2b3139`

**Light mode:**
- **Canvas Light**: `#ffffff`
- **Surface Soft Light**: `#fafafa`
- **Surface Strong Light**: `#f5f5f5`

### Hairlines & Borders
- **Hairline on Light**: `#eaecef`
- **Hairline on Dark**: `#2b3139`
- **Border Strong**: `#cdd1d6`

### Text
- **Ink**: `#181a20`
- **Body on Dark**: `#eaecef`
- **Body on Light**: `#181a20`
- **Muted**: `#707a8a`
- **Muted Strong**: `#929aa5`
- **On Primary**: `#181a20`
- **On Dark**: `#ffffff`

### Trading Semantics & Info
- **Trading Up**: `#0ecb81`
- **Trading Down**: `#f6465d`
- **Info**: `#3b82f6`

## Shapes (Border Radius)
- **xs**: 2px
- **sm**: 4px
- **md**: 6px
- **lg**: 8px
- **xl**: 12px
- **pill**: 9999px

## Spacing System
- **Base unit:** 4px.
- **Tokens:** `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 16px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 80px.

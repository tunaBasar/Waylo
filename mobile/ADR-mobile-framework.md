# ADR: Mobile framework (RM-32)

Status: **Temporary decision** (made autonomously, revisit with the product owner).
Decision: **Expo (React Native + TypeScript, expo-router)**. Rejected: **Flutter**.

## Context
Consumer app for hotels, flights, cars, bus (read-only), cart/checkout, orders with QR vouchers.
The backend is a REST gateway returning `{success,data,message}`. The existing web app is React/TypeScript.

## Comparison
| Criterion | Expo / React Native | Flutter |
|---|---|---|
| Language/skills | TypeScript, same as `frontend/` | Dart, new language for the team |
| Contract reuse | DTO types and helpers (`hotel-stay`, `cart`, `cancellation`) can be copied/shared as TS | Must be re-modelled in Dart |
| Secure storage | `expo-secure-store` (Keychain/Keystore) | `flutter_secure_storage` |
| QR rendering | `react-native-qrcode-svg` | `qr_flutter` |
| Routing | expo-router (file based, like Next.js app router) | go_router |
| Tooling | `expo start`, Expo Go, web target for quick checks, jest-expo | hot reload, strong widget tests, no browser target as light |
| Rendering/perf | Native views, enough for this CRUD/list app | Own renderer, best UI consistency and animation |
| Native-module risk | Some libs need dev builds outside Expo Go | Fewer third-party bridges |

## Decision
Expo, because the team already works in React/TS, the API contracts are TypeScript on the web, and the
app is form/list centric (no heavy animation or custom rendering), so Flutter's renderer advantage is small.

## Rejected alternative: Flutter
Better pixel-level consistency and performance headroom, but it costs a second language and toolchain,
no DTO sharing, and duplicated test infrastructure. Revisit if the app needs heavy custom graphics or
offline-first sync.

## Consequences
- Types are mirrored in `src/api/types.ts` (manual copy of web contracts). Extract a shared package later.
- No price/business logic on the device. Totals shown come from API responses, the server re-prices at checkout.
- Tokens only in SecureStore (web preview uses memory only).

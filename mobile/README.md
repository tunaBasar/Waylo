# Waylo mobile (Expo)

Expo SDK 58, React Native, TypeScript, expo-router. Framework rationale: `ADR-mobile-framework.md`.

## Run
```bash
cd mobile
npm install
npm start            # then press a (Android emulator), or scan the QR with Expo Go
```
Backend must be reachable through the API gateway (`./boot.sh start`, gateway on :8080).

## API URL
`src/config.ts` reads `EXPO_PUBLIC_API_URL`. Defaults:
- Android emulator: `http://10.0.2.2:8080`
- iOS simulator / web: `http://localhost:8080`
- Physical device: use your computer's LAN IP, e.g.
  `EXPO_PUBLIC_API_URL=http://192.168.1.20:8080 npm start`

Web preview (`npm run web`) is a browser, so the gateway CORS allow-list must include the dev origin
(the gateway default `WAYLO_CORS_ORIGINS` now includes `http://localhost:3000` and `http://localhost:8081`). Native builds are not affected by CORS.

## Checks
```bash
npm test            # jest-expo
npm run typecheck   # tsc --noEmit
npx expo-doctor
npx expo export --platform android
```

## Layout
`src/api` (typed client, refresh on 401, SecureStore tokens), `src/features/*` (auth, hotels, flights, cars,
bus, cart, orders, profile), `src/app` (expo-router screens), `src/components` (UI primitives).

## Notes
- Payment is the sandbox flow (TEST_OK / TEST_DECLINE). Success is shown only when the backend order status is CONFIRMED.
- Bus is read-only and hidden when `/api/bus-sales-status` says disabled.
- Cart persists in AsyncStorage (`waylo.cart.v1`, cart only; tokens stay in SecureStore and are never written to AsyncStorage). Prices/stock are always fetched online and re-priced by the server at checkout.
- Dates use a real picker (`DateField`: system picker on native, browser date input on web). Dates are local calendar values, never via toISOString.
- Hotel search has rooms, adults and children with ages like the web. Car search has separate pickup and return branches (different = one-way).
- Reviews (`/reviews`, hotel detail rating + list), online check-in for FLIGHT items on the order screen (window per CHECKIN-CONTRACT, voucher QR is the boarding credential), profile edit, change password (keeps the session with the fresh token pair), settings (logout, delete account), notifications (`GET /api/notifications/me`).
- Web preview has no SecureStore backend: the session is not kept across a browser reload (by design, no tokens in localStorage).
- Push (`src/features/push/registration.ts`): after login the app asks notification permission, gets the Expo push token and `POST /api/me/devices`; logout calls `DELETE /api/me/devices/{token}` before clearing the session. **An EAS projectId is required** (`expo.extra.eas.projectId` in app.json, created with `eas init`). Until it is set, registration is skipped with a console warning (user decision pending). Backend sending is off unless `EXPO_PUSH_ENABLED=true` on notification-service. Only works on physical devices.
- Jest: `jest.setup.js` mocks AsyncStorage globally.

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
(today only `http://localhost:3000`). Native builds are not affected by CORS.

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
- Cart is in memory (lost on app restart). Prices/stock are always fetched online.

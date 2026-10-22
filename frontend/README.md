# Waylo frontend

Next.js application for the Waylo gateway and partner workspace.

## Run locally

```bash
npm ci
npm run dev
```

The frontend runs on port 3000 and defaults to the gateway at `http://localhost:8080`.
Set `NEXT_PUBLIC_API_URL` before building when the gateway has a different address.

```bash
npm test
npx tsc --noEmit
npx eslint src/app/partner src/lib/partner.ts src/lib/partner-context.tsx src/components/partner src/components/Toast.tsx
npm run build
```

## Partner workspace

`/partner` provides organization and hotel creation. Room types and rate plans can be
created, edited, deactivated and reactivated. Management lists include inactive rows.
The calendar edits inventory and nightly prices for one room type over a 14-day window.
Prices currently use the room type's maximum occupancy. The backend remains authoritative
for access checks, validation and optimistic concurrency.

Inventory and prices are saved in separate requests, not a single cross-resource transaction.
If inventory succeeds but prices fail validation, only unsaved price edits remain.
A concurrency conflict reloads current server values instead of overwriting another editor.
Ordinary failures retain edits. Hotel selection is locked while calendar edits are pending,
and link navigation asks before discarding them. Browser Back/Forward navigation currently
bypasses that confirmation, so save or discard calendar edits before using browser history.

`/partner/orders` shows the signed-in account's own orders. It is not a hotel's incoming
reservation list and does not filter by the workspace hotel.

## Browser acceptance checklist

Use a dedicated test organization and hotel. Never alter unrelated catalog entries.

1. Create a room type and two rate plans. Verify invalid occupancy and three-decimal prices
   are rejected before saving. Verify comma decimals are accepted.
2. Save allotment, stop-sell and nightly prices. Reload and compare against the gateway API.
3. Deactivate a room type or rate plan. Confirm it stays visible and can be reactivated.
4. Make an unsaved calendar change. Verify the hotel selector is disabled. Cancel a tab
   navigation confirmation and verify the edit remains.
5. Return a temporary validation error for the save request in a browser test harness.
   Verify the error toast and subsequent grid refresh do not erase the edit.
6. Change an inventory row through a second client, then save the first client's stale
   version. Verify the current server value survives and a conflict is reported.
7. Discard edits and switch to a hotel without room types. Verify the empty state is shown
   without a request using the previous hotel's room type.
8. Repeat at a narrow viewport and verify horizontal calendar scrolling.

The browser checklist is manual acceptance coverage, not an automated test suite.
Native mobile applications and per-occupancy calendar editing are not included here.

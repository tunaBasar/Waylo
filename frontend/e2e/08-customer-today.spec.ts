import { expect, test } from '@playwright/test';
import { api, expectNoRawIcons, isoDate, loadState, login, patchState, psql, signIn, sleep } from './helpers';

test.beforeEach(async ({ context }) => { await signIn(context, loadState().customer); });

test('orders: review hint is shown for a CONFIRMED hotel order', async ({ page }) => {
  const s = loadState();
  expect(s.hotelOrderId, 'spec 1 order required').toBeTruthy();
  const t = (await login(s.customer)).token;
  const o = await api('GET', `/api/orders/${s.hotelOrderId}`, undefined, t);
  expect(o.data.status).toBe('CONFIRMED');
  await page.goto('/orders');
  await expect(page.getByTestId('review-when-hint')).toContainText('cikisiniz (check-out) kaydedildikten sonra degerlendirme yapabilirsiniz');
  await expectNoRawIcons(page);
});

test('orders: an unpaid order past its hold becomes EXPIRED, shows the note and no pay panel', async ({ page }) => {
  test.slow(); // the expiry job runs once a minute after a 30 s start delay
  test.setTimeout(300_000);
  const s = loadState();
  const cust = (await login(s.customer)).token;
  const hp = (await login(s.hotelPartner)).token;
  const rooms = await api<{ id: string; name: string }[]>('GET', `/api/hotels/${s.hotelId}/room-types`, undefined, hp);
  const plans = await api<{ id: string; name: string }[]>('GET', `/api/hotels/${s.hotelId}/rate-plans`, undefined, hp);
  const room = rooms.data.find(r => r.name === s.roomName)!;
  const plan = plans.data.find(p => p.name === s.planName)!;
  expect(room && plan).toBeTruthy();
  const quote = await api('POST', '/api/quotes', { hotelId: s.hotelId, roomTypeId: room.id, ratePlanId: plan.id, checkIn: s.checkIn, checkOut: s.checkOut, rooms: 1, adults: 2, childrenAges: [] }, cust);
  expect([200, 201]).toContain(quote.status);
  const created = await api('POST', '/api/orders/package', { items: [{ vertical: 'HOTEL', quoteId: quote.data.quoteId }] }, cust);
  expect(created.status).toBe(201);
  const orderId = created.data.id as string;
  patchState({ orderIds: [...loadState().orderIds, orderId] }); // cleaned up by global-teardown via its supplier org items

  // Fixture only: move the hold into the past (column is a timestamp without zone, one day clears any zone offset).
  psql('package_booking_db', `UPDATE orders SET hold_expires_at = (now() at time zone 'utc') - interval '1 day' WHERE id='${orderId}' AND status='PENDING'`);

  let status = '';
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    status = (await api('GET', `/api/orders/${orderId}`, undefined, cust)).data.status;
    if (status === 'EXPIRED') break;
    await sleep(5_000);
  }
  expect(status, 'expiry job did not run within 3 minutes').toBe('EXPIRED');

  await page.goto('/orders');
  const card = page.locator('div.rounded-xl', { hasText: `#${orderId}` }).last();
  await expect(card).toBeVisible();
  await expect(card.getByTestId('order-expired-note')).toContainText('Odeme suresi');
  await expect(card.getByRole('button', { name: /Odeme yap/ })).toHaveCount(0);
  await expect(card.getByText('Siparisi iptal et')).toHaveCount(0);
  await expectNoRawIcons(page);
});

test('hotel detail: a hotel that does not exist renders the not-published state', async ({ page }) => {
  const missing = '00000000-0000-4000-8000-' + String(isoDate(0).replace(/-/g, '')).padEnd(12, '0');
  const r = await api('GET', `/api/hotels/${missing}`);
  expect(r.status).toBe(404);
  await page.goto(`/hotels/${missing}`);
  await expect(page.getByTestId('hotel-not-published')).toBeVisible();
  await expect(page.getByText('Bu otel şu anda yayında değil')).toBeVisible();
  await expectNoRawIcons(page);
});

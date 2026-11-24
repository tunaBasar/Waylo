import { expect, test } from '@playwright/test';
import { adminAccount, api, expectNoRawIcons, isoDate, loadState, login, psql, signIn } from './helpers';

test.beforeEach(async ({ context }) => { await signIn(context, adminAccount()); });

test('admin organizations: approve a pending org', async ({ page }) => {
  const s = loadState();
  const name = `e2e-pending-${Date.now().toString(36)}`;
  const hp = (await login(s.hotelPartner)).token;
  const org = await api('POST', '/api/organizations', { name, businessType: 'HOTEL', legalType: 'COMPANY' }, hp);
  expect(org.status).toBe(201);
  const id = org.data.id as string;
  // track for teardown
  const { patchState } = await import('./helpers');
  patchState({ orgIds: [...s.orgIds, id] });

  await page.goto('/admin/organizations');
  const row = page.locator('div.rounded-xl', { hasText: id }).last();
  await expect(row).toContainText(name);
  await expect(row).toContainText('Onay bekleyen');
  await expectNoRawIcons(page);
  await row.getByRole('button', { name: 'Onayla' }).click();
  await expect(page.getByText(id)).toHaveCount(0); // left the pending list
  const admin = (await login(adminAccount())).token;
  const orgs = await api<unknown[]>('GET', '/api/admin/organizations?status=ACTIVE', undefined, admin);
  expect(JSON.stringify(orgs.data)).toContain(id);
});

test('admin payments page shows data', async ({ page }) => {
  const s = loadState();
  await page.goto('/admin/payments');
  await expect(page.getByRole('heading', { name: /Odeme|Komisyon|Muhasebe/i }).first()).toBeVisible();
  await page.getByPlaceholder('Siparis (order) UUID...').fill(s.hotelOrderId!);
  await page.getByPlaceholder('Siparis (order) UUID...').locator('xpath=following-sibling::button').click();
  await expect(page.locator('table tbody tr').first()).toBeVisible();
  await expectNoRawIcons(page);
});

test('admin reviews moderation: verified review is listed and can be hidden', async ({ page }) => {
  const s = loadState();
  expect(s.hotelItemId, 'spec 1 order and spec 3 redeem are required').toBeTruthy();
  // Fixture only: the stay of spec 1 (redeemed in spec 3) is moved into the past so the guest may review it.
  psql('package_booking_db', `UPDATE order_items SET check_in=current_date-3, check_out=current_date-1 WHERE id='${s.hotelItemId}'`);
  // RM-29: a review needs a recorded check-out. The partner records it through the real API (voucher version from the DB).
  const voucherVersion = Number(psql('package_booking_db', `SELECT coalesce(version,0) FROM vouchers WHERE order_item_id='${s.hotelItemId}'`).trim() || '0');
  const hp = (await login(s.hotelPartner)).token;
  const co = await api('POST', `/api/partner/organizations/${s.hotelOrgId}/order-items/${s.hotelItemId}/check-out`, { expectedVersion: voucherVersion }, hp);
  expect([200, 409]).toContain(co.status); // 409 VERSION_CONFLICT only when a kept fixture was already checked out
  const cust = (await login(s.customer)).token;
  const text = `E2E yorum ${s.hotelItemId!.slice(0, 8)}`;
  const r = await api('POST', '/api/me/hotel-reviews', { orderItemId: s.hotelItemId, score: 4, text }, cust);
  expect([201, 409]).toContain(r.status); // 409 when a kept fixture (E2E_KEEP) was already reviewed

  await page.goto('/admin/reviews');
  await expect(page.getByRole('heading', { name: 'Otel Degerlendirmeleri' })).toBeVisible();
  const row = page.locator('div.rounded-xl', { hasText: text });
  await expect(row).toBeVisible();
  await expect(row).toContainText('★★★★');
  await expectNoRawIcons(page);
  await row.getByPlaceholder('Gerekce (zorunlu)').fill('E2E moderasyon');
  await row.getByRole('button', { name: 'Gizle' }).click();
  await expect(row.getByRole('button', { name: 'Yayinla' })).toBeVisible();
  const pub = await api<unknown>('GET', `/api/hotels/${s.hotelId}/reviews`);
  expect(JSON.stringify(pub.data)).not.toContain(text);
});

test('admin notifications page loads', async ({ page }) => {
  await page.goto('/admin/notifications');
  await expect(page.getByRole('heading', { name: 'Bildirimler' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tumu' })).toBeVisible();
  // The page must render what the API returns: the newest notification's recipient is listed.
  const admin = (await login(adminAccount())).token;
  const list = await api<{ recipient: string }[]>('GET', '/api/notifications', undefined, admin);
  expect(list.status).toBe(200);
  expect(list.data.length, 'notification service has data (orders above send emails)').toBeGreaterThan(0);
  await expect(page.getByText(list.data[0].recipient, { exact: true }).first()).toBeVisible();
  await expectNoRawIcons(page);
});

test('admin hotel publications page lists the review queue', async ({ page }) => {
  const s = loadState();
  // A second hotel left SUBMITTED so the queue has a row to render.
  const hp = (await login(s.hotelPartner)).token;
  const name = `E2E Queue ${Date.now().toString(36)}`;
  const created = await api('POST', '/api/hotels', { name, city: s.hotelCity, country: 'TR', address: 'E2E only', starRating: 3, organizationId: s.hotelOrgId }, hp);
  expect(created.status).toBe(201);
  const hotelId = created.data.id as string;
  const room = await api('POST', `/api/hotels/${hotelId}/room-types`, { code: 'Q_DBL', name: 'Queue room', maxAdults: 2, maxChildren: 0, maxOccupancy: 2 }, hp);
  const plan = await api('POST', `/api/hotels/${hotelId}/rate-plans`, { code: 'Q_FLEX', name: 'Queue plan', mealPlan: 'ROOM_ONLY', cancellationPolicy: 'NON_REFUNDABLE' }, hp);
  const day = isoDate(60);
  await api('PUT', `/api/hotels/${hotelId}/inventory`, { items: [{ roomTypeId: room.data.id, stayDate: day, allotment: 1, expectedVersion: null }], reason: 'e2e queue' }, hp);
  await api('PUT', `/api/hotels/${hotelId}/rates`, { items: [{ roomTypeId: room.data.id, ratePlanId: plan.data.id, stayDate: day, occupancy: 2, currency: 'TRY', amount: 900 }] }, hp);
  const sub = await api('POST', `/api/hotels/${hotelId}/publication-requests`, {}, hp);
  expect(sub.status).toBe(201);

  await page.goto('/admin/hotel-publications');
  await expect(page.getByRole('heading', { name: 'Otel Yayinlari' })).toBeVisible();
  await expect(page.getByText(name)).toBeVisible();
  await expectNoRawIcons(page);
  await page.getByText(name).click();
  // the button carries a Material icon ligature, so its accessible name is "close Reddet"
  const reject = page.getByRole('button', { name: /^(close )?Reddet$/ });
  await expect(reject).toBeDisabled();
  await page.getByRole('textbox').fill('E2E eksik bilgi');
  await expect(reject).toBeEnabled();
  await reject.click();
  await expect(page.getByText(name)).toHaveCount(0);
});

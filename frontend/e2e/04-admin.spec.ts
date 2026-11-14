import { expect, test } from '@playwright/test';
import { adminAccount, api, expectNoRawIcons, loadState, login, psql, signIn } from './helpers';

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
  const orgs = await api<any[]>('GET', '/api/admin/organizations?status=ACTIVE', undefined, admin);
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
  const pub = await api<any>('GET', `/api/hotels/${s.hotelId}/reviews`);
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

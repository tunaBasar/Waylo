import { expect, test } from '@playwright/test';
import { api, expectNoRawIcons, isoDate, loadState, login, patchState, psql, signIn } from './helpers';

test('customer: one-way car search -> cart -> pay -> order', async ({ page, context }) => {
  const s = loadState();
  await signIn(context, s.customer);
  await page.goto('/vehicles');
  await expect(page.locator('select').first().locator('option', { hasText: s.carCity })).toHaveCount(1);
  const [city, pickup, ret] = await page.locator('select').all();
  await city.selectOption(s.carCity);
  // The fixture has one vehicle. A one-way rental moves it, so a reused fixture (E2E_KEEP) alternates A->B and B->A.
  const done = Number(psql('car_rental_db', `SELECT count(*) FROM rentals WHERE organization_id='${s.carOrgId}'`));
  const [from, to] = done % 2 === 0 ? [s.carBranchAName, s.carBranchBName] : [s.carBranchBName, s.carBranchAName];
  await pickup.selectOption({ label: `${from} (${s.carCity})` });
  await ret.selectOption({ label: `${to} (${s.carCity})` });
  const dates = page.locator('input[type=date]');
  const start = 10 + 3 * done;
  await dates.nth(0).fill(isoDate(start));
  await dates.nth(1).fill(isoDate(start + 2));
  await page.getByRole('button', { name: 'Ara', exact: true }).click();

  await expect(page.getByText('Fiat Egea').first()).toBeVisible();
  await expect(page.getByText(/Tek yon ucreti/)).toContainText('400,00');
  await expect(page.getByText('Sure:')).toContainText('2 gun');
  await expectNoRawIcons(page);

  await page.getByRole('button', { name: 'Sepete ekle' }).click();
  await page.getByRole('link', { name: /sepeti ac/ }).click();
  await page.waitForURL(/\/cart/);
  await expect(page.locator('li', { hasText: 'Fiat Egea' })).toHaveCount(1);
  await page.getByRole('button', { name: 'Sepeti satin al' }).click();

  await page.waitForURL(/\/orders\?created=/);
  const orderId = new URL(page.url()).searchParams.get('created')!;
  const card = page.locator('div.rounded-xl', { hasText: `#${orderId}` }).last();
  await expect(card).toContainText('Alis:');
  await card.getByRole('button', { name: 'Odeme yap (TEST ODEME)' }).click();
  await expect(card).toContainText('Onaylandi');
  await expect(card.getByAltText('Kupon QR kodu')).toBeVisible();
  await expectNoRawIcons(page);

  const t = await login(s.customer);
  const o = await api('GET', `/api/orders/${orderId}`, undefined, t.token);
  expect(o.data.status).toBe('CONFIRMED');
  expect(o.data.items[0].vertical).toBe('CAR');
  // 2 days * 1500 + 400 one-way fee
  expect(Number(o.data.totalAmount)).toBe(3400);
  patchState({ orderIds: [...loadState().orderIds, orderId] });
});

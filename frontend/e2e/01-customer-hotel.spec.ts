import { expect, test } from '@playwright/test';
import { api, expectNoRawIcons, loadState, login, patchState, psql, signIn } from './helpers';

test('customer: hotel search -> quote -> cart -> sandbox pay -> CONFIRMED with QR -> cancellation preview', async ({ page, context }) => {
  const s = loadState();
  await signIn(context, s.customer);

  // search
  await page.goto('/hotels');
  await page.getByPlaceholder('Sehir girin').fill(s.hotelCity);
  const dates = page.locator('input[type=date]');
  await dates.nth(0).fill(s.checkIn);
  await dates.nth(1).fill(s.checkOut);
  await page.getByRole('spinbutton').nth(1).fill('2'); // adults (fixture rates are for 2)
  await page.getByRole('button', { name: 'Ara' }).first().click();
  const result = page.getByRole('link', { name: new RegExp(s.hotelName) });
  await expect(result).toBeVisible();
  await expectNoRawIcons(page);

  // detail with availability
  await result.click();
  await expect(page.getByRole('heading', { name: s.hotelName }).first()).toBeVisible();
  await expect(page.getByText(s.roomName)).toBeVisible();
  await expect(page.getByText(s.planName)).toBeVisible();
  await expect(page.getByText('2 gece, 1 oda')).toBeVisible();
  await expectNoRawIcons(page);

  // quote
  await page.getByRole('button', { name: 'Fiyati sabitle' }).first().click();
  await page.waitForURL(/\/quotes\//);
  await expect(page.getByText(/Teklif #/)).toBeVisible();
  await expect(page.getByText(s.checkIn).first()).toBeVisible();
  await expect(page.getByText(/2\.500,00/).first()).toBeVisible();
  await expectNoRawIcons(page);

  // cart
  await page.getByRole('button', { name: 'Sepete ekle' }).click();
  await page.getByRole('link', { name: /sepeti ac/ }).click();
  await page.waitForURL(/\/cart/);
  await expect(page.locator('li', { hasText: s.roomName })).toHaveCount(1);
  await expect(page.getByText(/Toplam \(indirim oncesi\)/)).toContainText('2.500,00');
  await page.getByRole('button', { name: 'Sepeti satin al' }).click();

  // order + sandbox payment
  await page.waitForURL(/\/orders\?created=/);
  const orderId = new URL(page.url()).searchParams.get('created')!;
  patchState({ hotelOrderId: orderId });
  const card = page.locator('div.rounded-xl', { hasText: `#${orderId}` }).last();
  await expect(card).toContainText('Odeme bekleniyor');
  await expect(card).toContainText('2.500,00');
  await card.getByRole('button', { name: 'Odeme yap (TEST ODEME)' }).click();
  await expect(card).toContainText('Onaylandi');
  await expect(card.getByAltText('Kupon QR kodu')).toBeVisible();
  const code = await card.locator('code').first().textContent();
  expect(code).toMatch(/^WAYLO1:/);
  await expectNoRawIcons(page);

  // backend agrees
  const t = await login(s.customer);
  const o = await api('GET', `/api/orders/${orderId}`, undefined, t.token);
  expect(o.data.status).toBe('CONFIRMED');
  expect(o.data.items[0].vertical).toBe('HOTEL');
  const itemId = o.data.items[0].id as string;
  patchState({ hotelItemId: itemId });
  expect(psql('package_booking_db', `SELECT count(*) FROM vouchers WHERE order_item_id='${itemId}'`)).toBe('1');

  // cancellation preview (no cancel)
  await card.getByTestId('cancel-button').click();
  await expect(card.getByTestId('cancel-confirm')).toBeVisible();
  await expect(card.getByTestId('refund-preview')).toContainText(/\d/);
  await card.getByRole('button', { name: 'Vazgec' }).click();
  await expect(card.getByTestId('cancel-confirm')).toHaveCount(0);
  await expect(card).toContainText('Onaylandi');
});

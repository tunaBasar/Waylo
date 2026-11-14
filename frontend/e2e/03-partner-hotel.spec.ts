import { expect, test } from '@playwright/test';
import { api, expectNoRawIcons, isoDate, loadState, login, psql, signIn } from './helpers';

test.describe.serial('partner hotel', () => {
  test.beforeEach(async ({ context }) => { await signIn(context, loadState().hotelPartner); });

  test('room types and rate plans list the fixture', async ({ page }) => {
    const s = loadState();
    await page.goto('/partner/room-types');
    await expect(page.getByText(s.roomName)).toBeVisible();
    await expectNoRawIcons(page);
    await page.goto('/partner/rate-plans');
    await expect(page.getByText(s.planName)).toBeVisible();
    await expectNoRawIcons(page);
  });

  test('calendar update persists', async ({ page }) => {
    const s = loadState();
    await page.goto('/partner/calendar');
    await expect(page.getByRole('button', { name: 'Kaydet' })).toBeVisible();
    await expectNoRawIcons(page);
    const day = isoDate(3);
    const cell = page.getByLabel(`Kontenjan ${day}`);
    await expect(cell).toBeEnabled();
    const next = (await cell.inputValue()) === '7' ? '8' : '7';
    await cell.fill(next);
    await page.getByRole('button', { name: 'Kaydet' }).click();
    await expect.poll(() => psql('hotel_db', `SELECT allotment FROM daily_inventory WHERE hotel_id='${s.hotelId}' AND stay_date='${day}'`)).toBe(next);
    await page.reload();
    await expect(page.getByLabel(`Kontenjan ${day}`)).toHaveValue(next);
  });

  test('orders, earnings, team, redeem', async ({ page }) => {
    const s = loadState();
    expect(s.hotelOrderId, 'spec 1 must have created the order').toBeTruthy();
    await page.goto('/partner/orders');
    const fmt = (d: string) => new Date(`${d}T00:00:00Z`).toLocaleDateString('tr-TR', { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' });
    const card = page.locator('div.rounded-xl', { hasText: `${fmt(s.checkIn)} - ${fmt(s.checkOut)}` }).first();
    await expect(card).toContainText('2 gece, 1 oda');
    await expect(card).toContainText('Onaylandi');
    await expect(card).toContainText('2.500,00');
    await expectNoRawIcons(page);

    await page.goto('/partner/earnings');
    await expect(page.getByText('Bekleyen')).toBeVisible();
    // 2500 gross, 12.5% commission -> 2187.50 net held
    await expect(page.getByText(/2\.187,50/).first()).toBeVisible();
    await expectNoRawIcons(page);

    await page.goto('/partner/team');
    await expect(page.getByText(s.hotelPartner.email).first()).toBeVisible();
    await expectNoRawIcons(page);

    // redeem the voucher of spec 1 (move validity window into the past, fixture only)
    psql('package_booking_db', `UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE order_item_id='${s.hotelItemId}'`);
    const cust = (await login(s.customer)).token;
    const vs = await api<{ payload: string }[]>('GET', `/api/orders/${s.hotelOrderId}/vouchers`, undefined, cust);
    await page.goto('/partner/redeem');
    await expect(page.getByRole('heading', { name: 'QR Okut' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Kamerayla okut' }).or(page.getByText(/kamera ile QR okumayi desteklemiyor/))).toBeVisible();
    // The workspace (organization) loads after first paint and remounts the form, so fill until it sticks.
    await expect(async () => {
      await page.getByLabel(/Kupon kodu/).fill(vs.data[0].payload);
      await expect(page.getByRole('button', { name: 'Kuponu kullan' })).toBeEnabled({ timeout: 1_000 });
    }).toPass();
    await page.getByRole('button', { name: 'Kuponu kullan' }).click();
    await expect(page.getByRole('status')).toContainText('Kupon kullanildi');
    await expectNoRawIcons(page);
    const v = await api('GET', `/api/orders/${s.hotelOrderId}/vouchers`, undefined, cust);
    expect((v.data as any[])[0].status).toBe('REDEEMED');
  });
});

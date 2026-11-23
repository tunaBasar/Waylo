import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
// Shared fixture helpers from the web e2e suite (real backend data, nothing faked).
import { loadState } from '../../frontend/e2e/helpers';

const OUT = path.resolve(__dirname, '../e2e-results');
const shot = (page: Page, name: string) => page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });

async function signIn(page: Page, email: string, password: string) {
  await page.getByLabel('E-posta').fill(email);
  await page.getByLabel('Sifre', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Giris yap' }).click();
}

test('mobile web: login, hotel search, cart, checkout, QR, offline, deep link', async ({ page: first, context }) => {
  let page = first;
  const s = loadState();

  // login
  await page.goto('/');
  await expect(page.getByLabel('E-posta')).toBeVisible();
  await shot(page, '01-login');
  await signIn(page, s.customer.email, s.customer.password);

  // hotel search (fixture hotel has stock for 2 adults on checkIn..checkOut)
  await expect(page.getByLabel('Sehir (istege bagli)')).toBeVisible();
  await page.getByLabel('Sehir (istege bagli)').fill(s.hotelCity);
  await page.getByLabel('Cikis').fill(s.checkOut);
  await page.getByLabel('Giris', { exact: true }).fill(s.checkIn);
  await page.getByLabel('Cikis').fill(s.checkOut);
  await page.getByRole('button', { name: 'Ara', exact: true }).click();
  const hit = page.getByRole('button', { name: new RegExp(s.hotelName) });
  await expect(hit).toBeVisible();
  await shot(page, '02-hotel-search');

  // detail -> quote -> cart
  await hit.click();
  await expect(page.getByText(s.roomName).first()).toBeVisible();
  await page.getByRole('button', { name: 'Teklif al' }).first().click();
  await expect(page.getByText(/2\.500,00/).first()).toBeVisible();
  await page.getByRole('button', { name: 'Sepete ekle' }).click();
  await expect(page.getByText('Sepete eklendi')).toBeVisible();
  await shot(page, '03-quote-added');
  // header cart button lives on the tabs screen: go back (client-side history, keeps the in-memory session)
  await page.goBack(); await page.goBack();
  await page.getByRole('button', { name: 'Sepet', exact: true }).click();
  const buy = page.getByRole('button', { name: 'Siparisi olustur' });
  await expect(buy).toBeVisible();

  // offline: banner shown, checkout disabled
  await context.setOffline(true);
  await expect(page.getByText(/^Cevrimdisisiniz\. Odeme ve siparis/)).toBeVisible();
  await expect(buy).toBeDisabled();
  await shot(page, '04-cart-offline');
  await context.setOffline(false);
  await expect(page.getByText(/^Cevrimdisisiniz\. Odeme ve siparis/)).toHaveCount(0);
  await expect(buy).toBeEnabled();
  await shot(page, '05-cart-online');

  // checkout + sandbox pay -> CONFIRMED with QR
  await buy.click();
  await page.waitForURL(/\/order\/[0-9a-f-]{36}/);
  const orderId = /\/order\/([0-9a-f-]{36})/.exec(page.url())![1];
  await page.getByRole('button', { name: 'Ode (TEST ODEME)' }).click();
  await expect(page.getByText('Onaylandi').first()).toBeVisible();
  await expect(page.getByText(/^WAYLO1:/).last()).toBeVisible();
  await expect(page.locator('svg').filter({ has: page.locator('path') }).first()).toBeVisible();
  await shot(page, '06-order-confirmed-qr');

  // orders list online (fills the read-only cache), then offline shows it with the cevrimdisi note
  await page.goBack(); // order screen -> tabs (in-app history keeps the in-memory web session)
  await page.getByText('Siparisler', { exact: true }).last().click();
  await expect(page.getByRole('button', { name: new RegExp(`Siparis ${orderId.slice(0, 8)}`) })).toBeVisible();
  await context.setOffline(true);
  await page.getByText('Oteller', { exact: true }).last().click();
  await page.getByText('Siparisler', { exact: true }).last().click();
  await expect(page.getByText(/^Cevrimdisisiniz\. Odeme ve siparis/)).toBeVisible();
  await expect(page.getByText(/^Cevrimdisi: .*kayitli liste gosteriliyor/)).toBeVisible();
  await expect(page.getByRole('button', { name: new RegExp(`Siparis ${orderId.slice(0, 8)}`) })).toBeVisible();
  await shot(page, '07-orders-offline-cached');
  await context.setOffline(false);

  // deep link to the order (signed-out cold open -> login -> replayed to the order)
  const dl = await context.newPage(); // cold open in a new tab, no session
  page = dl;
  await page.goto(`/order/${orderId}`, { waitUntil: 'networkidle' });
  await expect(page.getByLabel('E-posta')).toBeVisible();
  await signIn(page, s.customer.email, s.customer.password);
  await page.waitForURL(new RegExp(`/order/${orderId}`));
  await expect(page.getByText(`Siparis #${orderId.slice(0, 8)}`).last()).toBeVisible();
  await expect(page.getByText(/^WAYLO1:/).last()).toBeVisible();
  await shot(page, '08-deeplink-order');

  // email links open their screens without a session
  await page.goto('/reset-password?token=e2e-invalid');
  await expect(page.getByLabel('Yeni sifre', { exact: true })).toBeVisible();
  await shot(page, '09-deeplink-reset-password');
  await page.goto('/verify?token=e2e-invalid');
  await expect(page.getByText('E-posta dogrulama').first()).toBeVisible();
  await expect(page.getByRole('alert').last()).toBeVisible();
  await shot(page, '10-deeplink-verify-invalid');
});

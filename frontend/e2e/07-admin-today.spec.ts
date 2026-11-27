import { expect, test } from '@playwright/test';
import { API, adminAccount, api, expectNoRawIcons, loadState, login, signIn } from './helpers';

test.beforeEach(async ({ context }) => { await signIn(context, adminAccount()); });

interface AdminMediaPage { items: { media: { id: string; hotelId: string; reviewStatus: string } }[] }

test('admin media review: the uploaded photo appears and approving it makes it approved', async ({ page }) => {
  const s = loadState();
  const admin = (await login(adminAccount())).token;
  const pending = await api<AdminMediaPage>('GET', `/api/admin/hotel-media?reviewStatus=PENDING&hotelId=${s.hotelId}&size=50`, undefined, admin);
  const photo = pending.data.items.find(i => i.media.hotelId === s.hotelId);
  expect(photo, 'spec 6 must have uploaded a PENDING photo').toBeTruthy();

  await page.goto('/admin/hotel-publications');
  await page.getByRole('button', { name: 'Fotograflar', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Incelemede', exact: true })).toBeVisible();
  const card = page.locator('div.rounded-xl', { hasText: s.hotelName }).first();
  await expect(card).toBeVisible();
  await expect(card).toContainText('800x600');
  await expect(card).toContainText('Incelemede');
  await expectNoRawIcons(page);

  await card.getByRole('button', { name: /^(check )?Onayla$/ }).click(); // icon ligature is part of the accessible name
  await expect(page.getByText('Fotograf onaylandi')).toBeVisible();

  await page.getByRole('button', { name: 'Onaylandi', exact: true }).click();
  const approved = page.locator('div.rounded-xl', { hasText: s.hotelName }).first();
  await expect(approved).toContainText('Onaylandi');
  const after = await api<AdminMediaPage>('GET', `/api/admin/hotel-media?reviewStatus=APPROVED&hotelId=${s.hotelId}&size=50`, undefined, admin);
  expect(after.data.items.some(i => i.media.id === photo!.media.id)).toBe(true);
});

test('admin payments: reconciliation section renders a table or the empty state without error', async ({ page }) => {
  await page.goto('/admin/payments');
  await expect(page.getByRole('heading', { name: 'Mutabakat' })).toBeVisible();
  const admin = (await login(adminAccount())).token;
  const rec = await api<{ total: number; items: unknown[] }>('GET', '/api/admin/payment-reconciliation?page=0&size=20', undefined, admin);
  expect(rec.status).toBe(200);
  const section = page.locator('section', { has: page.getByRole('heading', { name: 'Mutabakat' }) });
  if (rec.data.items.length > 0) await expect(section.locator('table tbody tr').first()).toBeVisible();
  else await expect(section.getByText('Kayıt yok')).toBeVisible();
  await expect(page.getByText('Mutabakat kayıtları yüklenemedi')).toHaveCount(0);
  await expectNoRawIcons(page);
});

test('admin notifications: FAILED and GAVE_UP tabs switch cleanly, retry explanation is visible', async ({ page }) => {
  await page.goto('/admin/notifications');
  await expect(page.getByRole('heading', { name: 'Bildirimler' })).toBeVisible();
  await expect(page.getByText(/otomatik olarak 1 dk, 5 dk, 30 dk, 2 sa ve 12 sa sonra yeniden denenir/)).toBeVisible();
  const admin = (await login(adminAccount())).token;
  for (const [label, status] of [['Başarısız', 'FAILED'], ['Vazgeçildi', 'GAVE_UP']] as const) {
    await page.getByRole('button', { name: label, exact: true }).click();
    const list = await api<{ recipient: string }[]>('GET', `/api/notifications/status/${status}`, undefined, admin);
    expect(list.status).toBe(200);
    if (list.data.length > 0) await expect(page.getByText(list.data[0].recipient, { exact: true }).first()).toBeVisible();
    else await expect(page.getByText('Bildirim bulunamadi')).toBeVisible();
    // Next.js keeps an empty route announcer with role=alert on every page: assert there is no error text, not no element
    await expect(page.locator('[role=alert]:not(#__next-route-announcer__)').filter({ hasText: /\S/ })).toHaveCount(0);
    await expect(page.getByText(/otomatik olarak 1 dk/)).toBeVisible();
    await expectNoRawIcons(page);
  }
});

test('admin flights: upcoming flights are paged with the server total, search narrows on the server', async ({ page }) => {
  const admin = (await login(adminAccount())).token;
  const count = async (q: string) => {
    const res = await fetch(`${API}/api/flights?size=1&${q}`, { headers: { Authorization: `Bearer ${admin}` } });
    expect(res.status).toBe(200);
    return Number(res.headers.get('X-Total-Count'));
  };
  const upcoming = await count('upcoming=true');
  const all = await count('');
  expect(all).toBeGreaterThanOrEqual(upcoming);

  await page.goto('/admin');
  await expect(page.locator('a[href="/admin/flights"]', { hasText: 'Yaklasan ucuslar' }).last()).toContainText(String(upcoming));

  await page.goto('/admin/flights');
  const shownFirst = Math.min(upcoming, 50);
  await expect(page.getByText(upcoming > 0 ? `Toplam ${upcoming} ucus (1-${shownFirst})` : 'Toplam 0 ucus')).toBeVisible();
  if (upcoming > 50) {
    await page.getByRole('button', { name: 'Sonraki' }).click();
    await expect(page.getByText(`Toplam ${upcoming} ucus (51-`)).toBeVisible();
    await expect(page.getByText(/^Sayfa 2 \/ \d+$/)).toBeVisible();
  }

  // Search by number runs on the server: the total is the matching count, not a filter over the loaded page.
  const first = await api<{ flightNumber: string }[]>('GET', '/api/flights?size=1&upcoming=true', undefined, admin);
  if (first.data.length > 0) {
    const no = first.data[0].flightNumber;
    const matching = await count(`upcoming=true&flightNumber=${encodeURIComponent(no)}`);
    const filters = page.getByTestId('flight-filters');
    await filters.getByLabel('Ucus no').fill(no.toLowerCase());
    await filters.getByRole('button', { name: /Ara$/ }).click();
    await expect(page.getByText(`Toplam ${matching} ucus (1-${Math.min(matching, 50)})`)).toBeVisible();
    await expect(page.getByText(no, { exact: false }).first()).toBeVisible();
    await filters.getByRole('button', { name: 'Temizle' }).click();
  }

  await page.getByLabel('Gecmis ve iptal edilenleri de goster').check();
  await expect(page.getByText(all > 0 ? `Toplam ${all} ucus (1-${Math.min(all, 50)})` : 'Toplam 0 ucus')).toBeVisible();
  await expectNoRawIcons(page);
});

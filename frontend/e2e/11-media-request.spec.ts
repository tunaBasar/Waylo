import { expect, test } from '@playwright/test';
import { adminAccount, api, isoDate, loadState, login, must, pngBuffer, signIn } from './helpers';

// Media phase 3: a PENDING photo rides along with the publication request and one admin decision approves both.
// The teardown removes every hotel of the fixture org, its request media rows and the stored files.
test('publication request carries a photo, admin approves request and photo in the UI', async ({ page, context }) => {
  const s = loadState();
  const hp = (await login(s.hotelPartner)).token;
  const name = `E2E Foto Otel ${Date.now().toString(36)}`;
  const hotel = await must<{ id: string }>('POST', '/api/hotels', { name, city: s.hotelCity, country: 'TR', address: 'E2E only', starRating: 3, organizationId: s.hotelOrgId }, hp);
  const room = await must<{ id: string }>('POST', `/api/hotels/${hotel.id}/room-types`, { code: 'E2E_MP', name: 'E2E Foto Oda', maxAdults: 2, maxChildren: 0, maxOccupancy: 2 }, hp);
  const plan = await must<{ id: string }>('POST', `/api/hotels/${hotel.id}/rate-plans`, { code: 'E2E_MPF', name: 'E2E Foto Plan', mealPlan: 'BREAKFAST', cancellationPolicy: 'FREE_CANCELLATION', freeCancellationDaysBefore: 2 }, hp);
  const night = isoDate(40);
  await must('PUT', `/api/hotels/${hotel.id}/inventory`, { items: [{ roomTypeId: room.id, stayDate: night, allotment: 1, expectedVersion: null }], reason: 'e2e media fixture' }, hp);
  await must('PUT', `/api/hotels/${hotel.id}/rates`, { items: [{ roomTypeId: room.id, ratePlanId: plan.id, stayDate: night, occupancy: 2, currency: 'TRY', amount: 900 }] }, hp);

  const form = new FormData();
  form.set('file', new Blob([new Uint8Array(pngBuffer(800, 600))], { type: 'image/png' }), 'e2e-foto.png');
  const up = await api<{ id: string }>('POST', `/api/hotels/${hotel.id}/media`, undefined, hp, form);
  expect(up.status).toBe(201);
  const mediaId = up.data.id;

  // partner UI: keep the photo checked and submit
  await signIn(context, s.hotelPartner);
  await page.addInitScript((orgId) => { localStorage.setItem('waylo_partner_org', orgId); }, s.hotelOrgId);
  await page.goto('/partner');
  await page.getByRole('button', { name: new RegExp(name) }).first().click();
  const list = page.getByTestId('publication-media-checklist');
  await expect(list).toContainText('Bu talebe eklenecek fotograflar');
  await expect(list.getByTestId('publication-media-check')).toHaveCount(1);
  await expect(list.getByTestId('publication-media-check')).toBeChecked();
  await page.getByRole('button', { name: /Yayin icin gonder$/ }).click();
  await expect(page.getByText('Otel incelemeye gonderildi')).toBeVisible();

  const hist = await api<{ id: string; mediaCount: number }[]>('GET', `/api/hotels/${hotel.id}/publication-requests`, undefined, hp);
  expect(hist.status).toBe(200);
  expect(hist.data[0].mediaCount).toBe(1);

  // admin UI: the request lists the photo, approving covers it
  await signIn(context, adminAccount());
  await page.goto('/admin/hotel-publications');
  await page.getByRole('button', { name: new RegExp(name) }).click();
  await expect(page.getByTestId('request-photos')).toContainText('Fotograflar (1)');
  await page.getByRole('button', { name: /Onayla ve yayinla$/ }).click();
  await expect(page.getByText('Otel yayinlandi')).toBeVisible();

  const mine = await api<{ id: string; reviewStatus: string }[]>('GET', `/api/hotels/${hotel.id}/media/mine`, undefined, hp);
  expect(mine.data.find(m => m.id === mediaId)?.reviewStatus).toBe('APPROVED');
});

// F28: a photo uploaded in the UI shows up in the publication checklist without a reload.
test('photo uploaded on the partner page appears in the publication checklist without reload', async ({ page, context }) => {
  const s = loadState();
  const hp = (await login(s.hotelPartner)).token;
  const name = `E2E Foto Canli ${Date.now().toString(36)}`;
  await must('POST', '/api/hotels', { name, city: s.hotelCity, country: 'TR', address: 'E2E only', starRating: 3, organizationId: s.hotelOrgId }, hp);

  await signIn(context, s.hotelPartner);
  await page.addInitScript((orgId) => { localStorage.setItem('waylo_partner_org', orgId); }, s.hotelOrgId);
  await page.goto('/partner');
  await page.getByRole('button', { name: new RegExp(name) }).first().click();
  await expect(page.getByTestId('publication-media-checklist')).toHaveCount(0);

  await page.locator('input[type=file]').setInputFiles({ name: 'e2e-canli.png', mimeType: 'image/png', buffer: pngBuffer(800, 600) });
  await expect(page.getByText('Fotograf yuklendi, incelemeye alindi')).toBeVisible();

  const list = page.getByTestId('publication-media-checklist');
  await expect(list.getByTestId('publication-media-check')).toHaveCount(1);
  await expect(page.getByTestId('publication-readiness')).toContainText('Fotograf: 1 (incelemede 1)');
});

import { expect, test } from '@playwright/test';
import { expectNoRawIcons, loadState, psql, signIn } from './helpers';

// 2026-11-26 live walk: car and bus partners could not upload any document from the panel. The page sent the
// FormData through api.post (JSON.stringify + JSON Content-Type) and the services answered 415. The setup uploads
// its documents through the API with a real multipart body, so nothing exercised the UI path until this spec.
const PDF = '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';

test('car partner uploads a document from the panel and it is stored as PENDING', async ({ page, context }) => {
  const s = loadState();
  await signIn(context, s.carPartner);
  const number = `UI-E2E-${Date.now().toString(36)}`;

  await page.goto('/partner/car/documents');
  await page.getByRole('button', { name: 'Belge yukle' }).first().click();
  await page.getByLabel('Belge no').fill(number);
  await page.getByLabel('Dosya').setInputFiles({ name: 'yetki-belgesi.pdf', mimeType: 'application/pdf', buffer: Buffer.from(PDF) });
  // F74: the authorization has an expiry; without it the form must not submit (the backend would answer 422)
  const submit = page.getByRole('button', { name: /^upload_file Yukle$/ });
  await expect(submit).toBeDisabled();
  await expect(page.getByText('Bu belge icin gecerlilik tarihi gerekli')).toBeVisible();
  await page.getByLabel('Gecerlilik bitis').fill('2027-11-30');

  const upload = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/organizations/${s.carOrgId}/documents`));
  await submit.click();
  const res = await upload;
  expect(res.status(), 'the panel upload must be a real multipart request').toBe(201);
  expect(res.request().headers()['content-type'] ?? '').toContain('multipart/form-data');

  await expect(page.getByText(number)).toBeVisible();
  await expect.poll(() => psql('car_rental_db', `SELECT status FROM documents WHERE document_number='${number}'`)).toBe('PENDING');
  await expectNoRawIcons(page);
});

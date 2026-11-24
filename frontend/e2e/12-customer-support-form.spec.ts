import { expect, test } from '@playwright/test';
import { api, loadState, login, signIn } from './helpers';

// RM-36 customer UI: open a support case from the orders page form (09 opens one via the API), then follow it on
// /support and close it there. Real fixture order from spec 01; the teardown removes the fixture customer's cases.
test('customer support: open a case from the orders page and close it on /support', async ({ page, context }) => {
  const s = loadState();
  expect(s.hotelOrderId, 'spec 1 order is required').toBeTruthy();
  const subject = `E2E siparis destek ${Date.now().toString(36)}`;

  await signIn(context, s.customer);
  await page.goto('/orders');
  const card = page.locator(`[data-testid="order-card"][data-order-id="${s.hotelOrderId}"]`);
  await card.getByTestId('support-case-open').click();
  await card.getByTestId('support-case-subject').fill(subject);
  await card.getByTestId('support-case-message').fill('Odada bebek yatagi olabilir mi?');
  await card.getByTestId('support-case-submit').click();
  await expect(card.getByTestId('support-case-success')).toBeVisible();

  const cust = (await login(s.customer)).token;
  const mine = await api<{ id: string; subject: string; orderId: string; status: string }[]>('GET', '/api/me/support-cases', undefined, cust);
  const created = mine.data.find(c => c.subject === subject);
  expect(created, 'case created through the orders page form').toBeTruthy();
  expect(created!.orderId).toBe(s.hotelOrderId);
  expect(created!.status).toBe('OPEN');

  await page.goto('/support');
  await page.getByTestId('support-case-row').filter({ hasText: subject }).click();
  await expect(page.getByTestId('support-thread-subject')).toHaveText(subject);
  await page.getByTestId('support-close').click();
  await expect(page.getByTestId('support-closed-note')).toBeVisible();

  const after = await api<{ status: string }>('GET', `/api/me/support-cases/${created!.id}`, undefined, cust);
  expect(after.data.status).toBe('CLOSED');
});

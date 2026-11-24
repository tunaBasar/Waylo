import { expect, test } from '@playwright/test';
import { adminAccount, api, loadState, login, signIn } from './helpers';

// RM-36 support cases: the customer opens a case on the fixture order (spec 01), the admin answers it from the UI and the
// customer sees the answer on /support. Real API data only; the case is removed by the teardown (customer rows of crm_db).
test('support: customer opens a case, admin answers in the UI, customer sees the answer', async ({ page, context }) => {
  const s = loadState();
  expect(s.hotelItemId, 'spec 1 order is required').toBeTruthy();
  const cust = (await login(s.customer)).token;
  const orders = await api<{ id: string; items?: { id: string }[] }[]>('GET', '/api/orders', undefined, cust);
  const order = orders.data.find(o => (o.items ?? []).some(i => i.id === s.hotelItemId));
  expect(order, 'fixture order of spec 1').toBeTruthy();
  const subject = `E2E destek ${Date.now().toString(36)}`;
  const opened = await api<{ id: string; status: string }>('POST', '/api/me/support-cases',
    { orderId: order!.id, orderItemId: s.hotelItemId, subject, message: 'Fatura bilgisi rica ediyorum.' }, cust);
  expect(opened.status).toBe(201);
  expect(opened.data.status).toBe('OPEN');

  const cap = await api<{ liveChat: boolean; decisionPending: boolean }>('GET', '/api/support/capabilities');
  expect(cap.status).toBe(200);
  expect(cap.data.liveChat).toBe(false);

  await signIn(context, adminAccount());
  await page.goto('/admin/support');
  await page.getByText(subject).first().click();
  await expect(page.getByTestId('support-thread-subject')).toHaveText(subject);
  await page.getByTestId('support-reply-input').fill('Faturaniz e-posta ile gonderildi.');
  await page.getByTestId('support-reply-send').click();
  await expect(page.getByTestId('support-message').filter({ hasText: 'Faturaniz e-posta ile gonderildi.' })).toBeVisible();

  const after = await api<{ status: string; messages: { body: string }[] }>('GET', `/api/me/support-cases/${opened.data.id}`, undefined, cust);
  expect(after.data.status).toBe('ANSWERED');
  expect(after.data.messages.map(m => m.body)).toContain('Faturaniz e-posta ile gonderildi.');

  // init scripts run in order on each navigation, so the customer's (added last) wins over the admin's
  await signIn(context, s.customer);
  await page.goto('/support');
  await expect(page.getByText(/henüz belirlenmedi/)).toBeVisible(); // honest capabilities text, no invented hours
  await page.getByText(subject).first().click();
  await expect(page.getByTestId('support-message').filter({ hasText: 'Faturaniz e-posta ile gonderildi.' })).toBeVisible();
});

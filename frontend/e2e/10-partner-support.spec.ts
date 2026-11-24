import { expect, test } from '@playwright/test';
import { api, loadState, login, signIn } from './helpers';

// Partner side of RM-36: the customer opens a case on the fixture order item (supplier = fixture hotel org),
// the partner owner answers from /partner/support. The case belongs to the fixture customer, so the teardown removes it.
test('partner support: owner answers a customer case in the UI, other org gets 404', async ({ page, context }) => {
  const s = loadState();
  expect(s.hotelItemId, 'spec 1 order is required').toBeTruthy();
  const cust = (await login(s.customer)).token;
  const orders = await api<{ id: string; items?: { id: string }[] }[]>('GET', '/api/orders', undefined, cust);
  const order = orders.data.find(o => (o.items ?? []).some(i => i.id === s.hotelItemId));
  expect(order, 'fixture order of spec 1').toBeTruthy();
  const subject = `E2E partner destek ${Date.now().toString(36)}`;
  const opened = await api<{ id: string; status: string }>('POST', '/api/me/support-cases',
    { orderId: order!.id, orderItemId: s.hotelItemId, subject, message: 'Check-in saatini ogrenebilir miyim?' }, cust);
  expect(opened.status).toBe(201);
  const caseId = opened.data.id;
  const reply = 'Check-in saati 14:00.';

  await signIn(context, s.hotelPartner);
  await page.addInitScript((orgId) => { localStorage.setItem('waylo_partner_org', orgId); }, s.hotelOrgId);
  await page.goto('/partner/support');
  await page.getByTestId('support-case-row').filter({ hasText: subject }).click();
  await expect(page.getByTestId('support-thread-subject')).toHaveText(subject);
  await page.getByTestId('support-reply-input').fill(reply);
  await page.getByTestId('support-reply-send').click();
  await expect(page.getByTestId('support-message').filter({ hasText: reply })).toBeVisible();

  const after = await api<{ status: string; messages: { body: string }[] }>('GET', `/api/me/support-cases/${caseId}`, undefined, cust);
  expect(after.data.status).toBe('ANSWERED');
  expect(after.data.messages.map(m => m.body)).toContain(reply);

  // The fixture has a second org (car rental): its owner must not see the hotel org's case.
  const other = (await login(s.carPartner)).token;
  const foreign = await api('GET', `/api/partner/organizations/${s.carOrgId}/support-cases/${caseId}`, undefined, other);
  expect(foreign.status).toBe(404);
});

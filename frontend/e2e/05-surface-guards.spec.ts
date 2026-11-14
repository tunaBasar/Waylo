import { expect, test } from '@playwright/test';
import { loadState, signIn } from './helpers';

test('customer cannot open /admin or /partner', async ({ page, context }) => {
  await signIn(context, loadState().customer);
  for (const p of ['/admin', '/admin/payments', '/partner', '/partner/orders']) {
    await page.goto(p);
    await expect.poll(() => new URL(page.url()).pathname, { message: p }).not.toMatch(/^\/(admin|partner)/);
  }
});

test('partner cannot open /admin', async ({ page, context }) => {
  await signIn(context, loadState().hotelPartner);
  for (const p of ['/admin', '/admin/organizations']) {
    await page.goto(p);
    await expect.poll(() => new URL(page.url()).pathname, { message: p }).not.toMatch(/^\/admin/);
  }
  await page.goto('/partner');
  await expect(page).toHaveURL(/\/partner/);
});

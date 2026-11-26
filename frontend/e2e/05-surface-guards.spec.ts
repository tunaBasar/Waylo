import { expect, test } from '@playwright/test';
import { loadState, signIn } from './helpers';

test('customer cannot open /admin or /partner', async ({ page, context }) => {
  await signIn(context, loadState().customer);
  for (const p of ['/admin', '/admin/payments', '/partner/orders', '/partner/calendar']) {
    await page.goto(p);
    await expect.poll(() => new URL(page.url()).pathname, { message: p }).not.toMatch(/^\/(admin|partner)/);
  }
  // F20 (2026-11-26): a customer without an organization may open the /partner overview, which only offers
  // "Isletme olustur"; every other partner page still redirects (spec 13 covers the onboarding flow).
  await page.goto('/partner');
  await expect(page).toHaveURL(/\/partner$/);
  await expect(page.getByText('Henuz bir isletmeniz yok')).toBeVisible();
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

import { expect, test } from '@playwright/test';
import { createUser, signIn } from './helpers';

test('a new user without an organization can start a business and sees the pending status', async ({ page, context }) => {
  const account = await createUser('onboard', 'Kaya');
  await signIn(context, account);

  // Without an organization only the overview is reachable
  await page.goto('/partner/orders');
  await expect.poll(() => new URL(page.url()).pathname).toBe('/');

  await page.goto('/');
  await page.getByRole('link', { name: 'Isletmeni ekle' }).first().click();
  await expect(page).toHaveURL(/\/partner$/);
  await expect(page.getByText('Henuz bir isletmeniz yok')).toBeVisible();

  await page.getByRole('button', { name: 'Isletme olustur' }).click();
  await page.getByLabel('Isletme adi').fill('Kaya Otelcilik Ltd. Sti.');
  await page.getByRole('button', { name: 'Olustur' }).click();

  await expect(page.getByText('Kaya Otelcilik Ltd. Sti.').first()).toBeVisible();
  await expect(page.getByText('Onay bekliyor').first()).toBeVisible();
});

import { expect, test } from '@playwright/test';
import { createUser, loadState, patchState, psql, signIn } from './helpers';

test('a new user without an organization can start a business and sees the pending status', async ({ page, context }) => {
  const account = await createUser('onboard', 'Kaya');
  // teardown removes the user and the organization it creates (live-test hygiene, F26)
  patchState({ emails: [...loadState().emails, account.email] });
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

  const orgId = psql('auth_db', `SELECT o.id FROM organizations o JOIN organization_memberships m ON m.organization_id = o.id
    JOIN users u ON u.id = m.user_id WHERE u.email = '${account.email}' LIMIT 1`);
  expect(orgId).toMatch(/^[0-9a-f-]{36}$/);
  patchState({ orgIds: [...loadState().orgIds, orgId] });
});

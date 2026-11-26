import { deflateSync } from 'node:zlib';
import { expect, test } from '@playwright/test';
import { api, expectNoRawIcons, loadState, login, signIn } from './helpers';

const VALID_TR_IBAN = 'TR330006100519786457841326';

/** Solid colour RGB PNG built with node zlib (same layout as png() in scripts/test-hotel-media.py). */
function pngBuffer(w: number, h: number): Buffer {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc32 = (b: Buffer) => {
    let c = 0xffffffff;
    for (const x of b) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const chunk = (type: string, data: Buffer) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2; // 8 bit, truecolour
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array.from({ length: w }, () => [0x80, 0x40, 0xc0]).flat())]);
  const raw = Buffer.concat(Array.from({ length: h }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]);
}

const fmtMoney = (v: number | string, cur: string) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: cur }).format(Number(v));

interface Balance { currency: string; payable: number | string; held: number | string; reserved: number | string; paidTotal: number | string }

test.describe.serial('partner payouts and hotel photos', () => {
  test.beforeEach(async ({ context }) => { await signIn(context, loadState().hotelPartner); });

  test('earnings: balance card shows the API numbers, IBAN validated, bank account pending verification', async ({ page }) => {
    const s = loadState();
    const token = (await login(s.hotelPartner)).token;
    const bal = await api<{ balances: Balance[] }>('GET', `/api/partner/organizations/${s.hotelOrgId}/payouts/balance`, undefined, token);
    expect(bal.status).toBe(200);
    expect(bal.data.balances.length, 'spec 1 order gives the org a balance row').toBeGreaterThan(0);

    await page.addInitScript((orgId) => { localStorage.setItem('waylo_partner_org', orgId); }, s.hotelOrgId);
    await page.goto('/partner/earnings');
    for (const b of bal.data.balances) {
      const card = page.getByTestId(`payout-balance-${b.currency}`);
      await expect(card).toBeVisible();
      await expect(card.getByTestId('payout-payable')).toHaveText(fmtMoney(b.payable, b.currency));
      await expect(card.getByTestId('payout-held')).toHaveText(fmtMoney(b.held, b.currency));
      await expect(card.getByTestId('payout-reserved')).toHaveText(fmtMoney(b.reserved, b.currency));
      await expect(card.getByTestId('payout-paid-total')).toHaveText(fmtMoney(b.paidTotal, b.currency));
    }
    await expectNoRawIcons(page);

    // A kept fixture may already hold a bank account: open the replace form then.
    const change = page.getByRole('button', { name: 'Degistir' });
    await expect(page.getByLabel('IBAN').or(change)).toBeVisible();
    if (await change.isVisible()) await change.click();
    const holder = page.getByLabel('Hesap sahibi');
    const iban = page.getByLabel('IBAN');
    const save = page.getByRole('button', { name: 'Kaydet' });
    await holder.fill('E2E Otelcilik A.S.');

    // invalid IBAN (right shape, wrong mod-97 check digits): client-side message, save disabled, nothing sent.
    // F48 (2026-11-26): this case names the check digits instead of blaming the length.
    let sent = 0;
    page.on('request', r => { if (r.method() === 'PUT' && r.url().includes('/bank-account')) sent++; });
    await iban.fill('TR120006200000000000000001');
    await expect(page.getByText('IBAN kontrol hanesi tutmuyor')).toBeVisible();
    await expect(save).toBeDisabled();
    expect(sent).toBe(0);

    // wrong shape (too short): the length/format message
    await iban.fill('TR12 0006 2000');
    await expect(page.getByText('IBAN TR ile baslamali ve 24 rakam icermeli')).toBeVisible();
    await expect(save).toBeDisabled();
    expect(sent).toBe(0);

    // valid IBAN
    await iban.fill(VALID_TR_IBAN);
    await expect(page.getByText('IBAN kontrol hanesi tutmuyor')).toHaveCount(0);
    await expect(page.getByText('IBAN TR ile baslamali')).toHaveCount(0);
    await expect(save).toBeEnabled();
    await save.click();
    await expect(page.getByText('Banka hesabi kaydedildi')).toBeVisible();
    const current = page.getByTestId('bank-account-current');
    await expect(current).toContainText('Onay bekliyor');
    await expect(current).toContainText('E2E Otelcilik A.S.');
    await expect(current).toContainText('26'); // masked IBAN shows the last two digits only
    const after = await api<{ bankAccount: { status: string } | null }>('GET', `/api/partner/organizations/${s.hotelOrgId}/payouts/balance`, undefined, token);
    expect(after.data.bankAccount?.status).toBe('PENDING_VERIFICATION');
    await expectNoRawIcons(page);
  });

  test('hotel photos panel: upload PNG shows PENDING review, fake .jpg is rejected', async ({ page }) => {
    const s = loadState();
    const token = (await login(s.hotelPartner)).token;
    await page.addInitScript((orgId) => { localStorage.setItem('waylo_partner_org', orgId); }, s.hotelOrgId);
    await page.goto('/partner');
    await page.getByRole('button', { name: new RegExp(s.hotelName) }).first().click();
    await expect(page.getByRole('heading', { name: 'Fotograflar' })).toBeVisible();
    const input = page.locator('input[type=file]');
    await expect(input).toBeEnabled();
    await expectNoRawIcons(page);

    const before = await api<unknown[]>('GET', `/api/hotels/${s.hotelId}/media/mine`, undefined, token);
    const count = before.data.length;

    await input.setInputFiles({ name: 'e2e-oda.png', mimeType: 'image/png', buffer: pngBuffer(800, 600) });
    await expect(page.getByText('Fotograf yuklendi, incelemeye alindi')).toBeVisible();
    await expect(page.getByText('800x600').first()).toBeVisible();
    await expect(page.getByText('Incelemede').first()).toBeVisible();
    const mine = await api<{ reviewStatus: string; width: number; height: number }[]>('GET', `/api/hotels/${s.hotelId}/media/mine`, undefined, token);
    expect(mine.data.length).toBe(count + 1);
    expect(mine.data.some(m => m.reviewStatus === 'PENDING' && m.width === 800 && m.height === 600)).toBe(true);

    // text pretending to be a jpg: passes the client type check, the server must refuse it
    await input.setInputFiles({ name: 'sahte.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('bu bir fotograf degil, duz metin\n'.repeat(40)) });
    await expect(page.locator('div.fixed.bottom-4 p').filter({ hasNotText: 'Fotograf yuklendi' }).first()).toBeVisible();
    const final = await api<unknown[]>('GET', `/api/hotels/${s.hotelId}/media/mine`, undefined, token);
    expect(final.data.length, 'the fake jpg was not stored').toBe(count + 1);
  });
});

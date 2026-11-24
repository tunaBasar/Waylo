import { execFileSync } from 'node:child_process';
import { loadState, psql, removeState, stateExists } from './helpers';

/** Removes everything the suite created (only rows tied to its own orgs, orders and users). */
export default async function globalTeardown(force = false) {
  if (!stateExists() || (process.env.E2E_KEEP && force !== true)) return;
  const s = loadState();
  const q = (a: string[]) => a.map(x => `'${x}'`).join(',');
  const orgs = q(s.orgIds);
  psql('payment_db', `DELETE FROM commission_rates WHERE supplier_organization_id IN (${orgs})`);
  const orders = psql('package_booking_db', `SELECT string_agg(DISTINCT order_id::text, ',') FROM order_items WHERE supplier_organization_id IN (${orgs})`);
  const ids = orders ? orders.split(',').map(x => `'${x}'`).join(',') : (s.orderIds.length ? q(s.orderIds) : '');
  if (ids) {
    psql('payment_db', 'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete');
    psql('payment_db', `DELETE FROM ledger_entries WHERE order_id IN (${ids})`);
    psql('payment_db', `DELETE FROM escrow_items WHERE order_id IN (${ids})`);
    psql('payment_db', `DELETE FROM payment_intents WHERE order_id IN (${ids})`);
    psql('payment_db', 'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete');
    psql('package_booking_db', `DELETE FROM vouchers WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id IN (${ids}))`);
    psql('package_booking_db', `DELETE FROM order_items WHERE order_id IN (${ids})`);
    psql('package_booking_db', `DELETE FROM orders WHERE id IN (${ids})`);
  }
  const keys = psql('car_rental_db', `SELECT storage_key FROM documents WHERE organization_id IN (${orgs})`).split(/\s+/);
  for (const k of keys) {
    if (/^[0-9a-f-]+$/.test(k)) execFileSync('docker', ['exec', 'waylo-car-rental-service', 'rm', '-f', `/data/car-documents/${k}`]);
  }
  for (const t of ['rentals', 'documents', 'one_way_routes', 'category_rates', 'vehicles', 'branches']) {
    psql('car_rental_db', `DELETE FROM ${t} WHERE organization_id IN (${orgs})`);
  }
  // Hotel photos uploaded by 06-partner-payouts-media (rows, decisions and the files on the hotel-service volume).
  const mediaKeys = psql('hotel_db', `SELECT storage_key FROM hotel_media WHERE hotel_id='${s.hotelId}'`).split(/\s+/);
  psql('hotel_db', `DELETE FROM hotel_media_decisions WHERE hotel_id='${s.hotelId}'`);
  psql('hotel_db', `DELETE FROM hotel_media WHERE hotel_id='${s.hotelId}'`);
  for (const k of mediaKeys) {
    if (/^[0-9a-f-]+$/.test(k)) execFileSync('docker', ['exec', 'waylo-hotel-service', 'rm', '-f', `/data/hotel-media/${k}`]);
  }
  // Bank account and wallet rows of the fixture orgs (06-partner-payouts-media). The change log is append-only, so its trigger is lifted briefly.
  psql('payment_db', 'ALTER TABLE partner_bank_account_changes DISABLE TRIGGER trg_bank_changes_row');
  psql('payment_db', `DELETE FROM partner_bank_account_changes WHERE organization_id IN (${orgs})`);
  psql('payment_db', 'ALTER TABLE partner_bank_account_changes ENABLE TRIGGER trg_bank_changes_row');
  psql('payment_db', `DELETE FROM partner_bank_accounts WHERE organization_id IN (${orgs})`);
  psql('payment_db', `DELETE FROM payout_wallets WHERE organization_id IN (${orgs})`);
  // Every hotel of the fixture orgs (the main fixture hotel and the review-queue hotel of 04-admin), children first.
  const hotels = `SELECT id FROM hotels WHERE organization_id IN (${orgs}) OR id='${s.hotelId}'`;
  psql('hotel_db', `DELETE FROM hotel_review_moderation_events WHERE review_id IN (SELECT id FROM hotel_reviews WHERE hotel_id IN (${hotels}))`);
  psql('hotel_db', `DELETE FROM hotel_reviews WHERE hotel_id IN (${hotels})`);
  for (const t of ['hotel_holds', 'hotel_quotes', 'inventory_adjustments', 'daily_rates', 'daily_inventory', 'rate_plans', 'room_types']) {
    psql('hotel_db', `DELETE FROM ${t} WHERE hotel_id IN (${hotels})`);
  }
  // Publication rows (V10) reference the hotel with ON DELETE RESTRICT: decisions -> requests -> revisions, in FK order.
  psql('hotel_db', `UPDATE hotels SET published_revision_id=NULL WHERE id IN (${hotels})`);
  psql('hotel_db', `DELETE FROM hotel_publication_decisions WHERE hotel_id IN (${hotels})`);
  psql('hotel_db', `UPDATE hotel_publication_requests SET revision_id=NULL WHERE hotel_id IN (${hotels})`);
  psql('hotel_db', `DELETE FROM hotel_publication_requests WHERE hotel_id IN (${hotels})`);
  psql('hotel_db', `DELETE FROM hotel_content_revisions WHERE hotel_id IN (${hotels})`);
  psql('hotel_db', `DELETE FROM hotels WHERE organization_id IN (${orgs}) OR id='${s.hotelId}'`);
  psql('auth_db', `DELETE FROM role_assignments WHERE organization_id IN (${orgs})`);
  psql('auth_db', `DELETE FROM organization_memberships WHERE organization_id IN (${orgs})`);
  psql('auth_db', `DELETE FROM organizations WHERE id IN (${orgs})`);
  const emails = q(s.emails);
  // Support cases of the fixture users (09-support). Messages are append-only, so the row trigger is lifted briefly.
  const custIds = `SELECT id::text FROM users WHERE email IN (${emails})`;
  const crmCustomers = psql('auth_db', custIds).split(/\s+/).filter(x => /^[0-9a-f-]{36}$/.test(x)).map(x => `'${x}'`).join(',');
  if (crmCustomers) {
    psql('crm_db', 'ALTER TABLE support_messages DISABLE TRIGGER trg_support_messages_row');
    psql('crm_db', `DELETE FROM support_messages WHERE case_id IN (SELECT id FROM support_cases WHERE customer_id IN (${crmCustomers}))`);
    psql('crm_db', 'ALTER TABLE support_messages ENABLE TRIGGER trg_support_messages_row');
    psql('crm_db', `DELETE FROM support_cases WHERE customer_id IN (${crmCustomers})`);
  }
  psql('crm_db', `DELETE FROM customers WHERE email IN (${emails})`);
  psql('auth_db', `DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN (${emails}))`);
  psql('auth_db', `DELETE FROM users WHERE email IN (${emails})`);
  removeState();
}

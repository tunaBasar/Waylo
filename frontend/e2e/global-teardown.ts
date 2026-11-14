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
  psql('hotel_db', `DELETE FROM hotel_review_moderation_events WHERE review_id IN (SELECT id FROM hotel_reviews WHERE hotel_id='${s.hotelId}')`);
  psql('hotel_db', `DELETE FROM hotel_reviews WHERE hotel_id='${s.hotelId}'`);
  for (const t of ['hotel_holds', 'hotel_quotes', 'inventory_adjustments', 'daily_rates', 'daily_inventory', 'rate_plans', 'room_types']) {
    psql('hotel_db', `DELETE FROM ${t} WHERE hotel_id='${s.hotelId}'`);
  }
  psql('hotel_db', `DELETE FROM hotels WHERE id='${s.hotelId}'`);
  psql('auth_db', `DELETE FROM role_assignments WHERE organization_id IN (${orgs})`);
  psql('auth_db', `DELETE FROM organization_memberships WHERE organization_id IN (${orgs})`);
  psql('auth_db', `DELETE FROM organizations WHERE id IN (${orgs})`);
  const emails = q(s.emails);
  psql('crm_db', `DELETE FROM customers WHERE email IN (${emails})`);
  psql('auth_db', `DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN (${emails}))`);
  psql('auth_db', `DELETE FROM users WHERE email IN (${emails})`);
  removeState();
}

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// Compile partner-car.ts, strip the api import, and import via data: URL.
let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/partner-car.ts', import.meta.url), 'utf8');
  // Strip the import that pulls in api/types - replace with a stub.
  const stripped = source.replace(
    /import \{[^}]+\} from '\.\/api';\n?/,
    '',
  );
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const m = await mod();

// ── formatTime ───────────────────────────────────────────────────────────────

test('formatTime basic HH:mm', () => {
  assert.strictEqual(m.formatTime('09:00'), '9:00');
  assert.strictEqual(m.formatTime('14:30'), '14:30');
  assert.strictEqual(m.formatTime('00:00'), '0:00');
  assert.strictEqual(m.formatTime('23:59'), '23:59');
});

test('formatTime invalid input returns raw', () => {
  assert.strictEqual(m.formatTime(''), '');
  assert.strictEqual(m.formatTime(null), '');
  assert.strictEqual(m.formatTime(undefined), '');
  assert.strictEqual(m.formatTime('abc'), 'abc');
  assert.strictEqual(m.formatTime('25:00'), '25:00');
  assert.strictEqual(m.formatTime('12:60'), '12:60');
});

// ── isValidTime ──────────────────────────────────────────────────────────────

test('isValidTime valid times', () => {
  assert.strictEqual(m.isValidTime('09:00'), true);
  assert.strictEqual(m.isValidTime('00:00'), true);
  assert.strictEqual(m.isValidTime('23:59'), true);
});

test('isValidTime invalid times', () => {
  assert.strictEqual(m.isValidTime(''), false);
  assert.strictEqual(m.isValidTime(null), false);
  assert.strictEqual(m.isValidTime(undefined), false);
  assert.strictEqual(m.isValidTime('abc'), false);
  assert.strictEqual(m.isValidTime('25:00'), false);
  assert.strictEqual(m.isValidTime('12:60'), false);
  assert.strictEqual(m.isValidTime('9:0'), false);
});

// ── computeChargeableDays ────────────────────────────────────────────────────

test('computeChargeableDays 1 day', () => {
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-02T10:00'), 1);
});

test('computeChargeableDays 2 days', () => {
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-03T10:00'), 2);
});

test('computeChargeableDays less than 1 day rounds up to 1', () => {
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-01T20:00'), 1);
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-02T09:59'), 1);
});

test('computeChargeableDays partial day rounds up', () => {
  // 1 day + 1 minute => ceil(1441/1440) = 2
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-02T10:01'), 2);
});

test('computeChargeableDays max 30', () => {
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-02-10T10:00'), 30);
});

test('computeChargeableDays exact 30 days', () => {
  assert.strictEqual(m.computeChargeableDays('2027-01-01T10:00', '2027-01-31T10:00'), 30);
});

// ── vehicleDocumentTypes ─────────────────────────────────────────────────────

test('vehicleDocumentTypes returns exactly 3 types', () => {
  const types = m.vehicleDocumentTypes();
  assert.strictEqual(types.length, 3);
  assert.deepStrictEqual(types, ['VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE']);
});

// ── orgDocumentTypesFor ──────────────────────────────────────────────────────

test('orgDocumentTypesFor COMPANY', () => {
  const types = m.orgDocumentTypesFor('COMPANY');
  assert.deepStrictEqual(types, ['RENTAL_AUTHORIZATION', 'TAX_CERTIFICATE']);
});

test('orgDocumentTypesFor INDIVIDUAL', () => {
  const types = m.orgDocumentTypesFor('INDIVIDUAL');
  assert.deepStrictEqual(types, ['IDENTITY_DOCUMENT']);
});

test('orgDocumentTypesFor undefined', () => {
  const types = m.orgDocumentTypesFor(undefined);
  assert.deepStrictEqual(types, []);
});

// ── documentTypeLabel ────────────────────────────────────────────────────────

test('documentTypeLabel all values', () => {
  assert.strictEqual(m.documentTypeLabel('RENTAL_AUTHORIZATION'), 'Arac kiralama yetki belgesi');
  assert.strictEqual(m.documentTypeLabel('TAX_CERTIFICATE'), 'Vergi levhasi');
  assert.strictEqual(m.documentTypeLabel('IDENTITY_DOCUMENT'), 'Nufus cüzdañi / kimlik');
  assert.strictEqual(m.documentTypeLabel('VEHICLE_REGISTRATION'), 'Araç ruhsati');
  assert.strictEqual(m.documentTypeLabel('RENTAL_INSURANCE'), 'Araç kiralama sigortasi (kasko)');
  assert.strictEqual(m.documentTypeLabel('TRAFFIC_INSURANCE'), 'Zorunlu trafik sigortasi');
});

// ── CATEGORY_LABEL ───────────────────────────────────────────────────────────

test('CATEGORY_LABEL has all 8 entries', () => {
  const keys = Object.keys(m.CATEGORY_LABEL).sort();
  assert.deepStrictEqual(keys, ['COMPACT', 'ECONOMY', 'FULLSIZE', 'LUXURY', 'MIDSIZE', 'MINIVAN', 'SUV', 'VAN']);
});

// ── TRANSMISSION_LABEL ───────────────────────────────────────────────────────

test('TRANSMISSION_LABEL has 2 entries', () => {
  assert.strictEqual(m.TRANSMISSION_LABEL['MANUAL'], 'Manuel');
  assert.strictEqual(m.TRANSMISSION_LABEL['AUTOMATIC'], 'Otomatik');
});

// ── FUEL_LABEL ───────────────────────────────────────────────────────────────

test('FUEL_LABEL has 5 entries', () => {
  assert.strictEqual(m.FUEL_LABEL['PETROL'], 'Benzin');
  assert.strictEqual(m.FUEL_LABEL['DIESEL'], 'Dizel');
  assert.strictEqual(m.FUEL_LABEL['HYBRID'], 'Hibrit');
  assert.strictEqual(m.FUEL_LABEL['ELECTRIC'], 'Elektrikli');
  assert.strictEqual(m.FUEL_LABEL['LPG'], 'LPG');
});

// ── ORG_DOCUMENT_TYPES ───────────────────────────────────────────────────────

test('ORG_DOCUMENT_TYPES structure', () => {
  assert.deepStrictEqual(m.ORG_DOCUMENT_TYPES['COMPANY'], ['RENTAL_AUTHORIZATION', 'TAX_CERTIFICATE']);
  assert.deepStrictEqual(m.ORG_DOCUMENT_TYPES['INDIVIDUAL'], ['IDENTITY_DOCUMENT']);
});

// ── CATEGORY_ORDER ───────────────────────────────────────────────────────────

test('CATEGORY_ORDER has all 8 categories in order', () => {
  assert.deepStrictEqual(m.CATEGORY_ORDER, [
    'ECONOMY', 'COMPACT', 'MIDSIZE', 'FULLSIZE', 'SUV', 'MINIVAN', 'VAN', 'LUXURY',
  ]);
});

// ── TRANSMISSION_ORDER ───────────────────────────────────────────────────────

test('TRANSMISSION_ORDER has 2 values', () => {
  assert.deepStrictEqual(m.TRANSMISSION_ORDER, ['MANUAL', 'AUTOMATIC']);
});

// ── rateKey ──────────────────────────────────────────────────────────────────

test('rateKey uses id when present', () => {
  assert.strictEqual(
    m.rateKey({ id: 'abc-123', category: 'COMPACT', transmission: 'MANUAL' }),
    'abc-123',
  );
});

test('rateKey falls back to category~transmission', () => {
  assert.strictEqual(
    m.rateKey({ category: 'COMPACT', transmission: 'AUTOMATIC' }),
    'COMPACT~AUTOMATIC',
  );
});

// ── availableCombinations ────────────────────────────────────────────────────

test('availableCombinations empty list returns all 16 combos', () => {
  const combos = m.availableCombinations([]);
  assert.strictEqual(combos.length, 16);
  assert.deepStrictEqual(combos[0], { category: 'ECONOMY', transmission: 'MANUAL' });
  assert.deepStrictEqual(combos[1], { category: 'ECONOMY', transmission: 'AUTOMATIC' });
});

test('availableCombinations excludes existing branch rates', () => {
  const existing = [
    { id: '1', branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY', dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
    { id: '2', branchId: 'b1', category: 'COMPACT', transmission: 'AUTOMATIC', currency: 'TRY', dailyRate: 150, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
  ];
  const combos = m.availableCombinations(existing);
  assert.strictEqual(combos.length, 14); // 16 - 2
  const keys = combos.map(c => `${c.category}~${c.transmission}`);
  assert.ok(!keys.includes('COMPACT~MANUAL'));
  assert.ok(!keys.includes('COMPACT~AUTOMATIC'));
  assert.ok(keys.includes('ECONOMY~MANUAL'));
});

test('availableCombinations respects CATEGORY_ORDER + TRANSMISSION_ORDER', () => {
  const existing = [
    { id: '1', branchId: 'b1', category: 'SUV', transmission: 'MANUAL', currency: 'TRY', dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
  ];
  const combos = m.availableCombinations(existing);
  assert.strictEqual(combos.length, 15);
  assert.strictEqual(combos[0].category, 'ECONOMY');
  assert.strictEqual(combos[0].transmission, 'MANUAL');
});

// ── sortRatesByOrder ─────────────────────────────────────────────────────────

test('sortRatesByOrder sorts by category then transmission', () => {
  const items = [
    { id: '3', branchId: 'b1', category: 'LUXURY', transmission: 'AUTOMATIC', currency: 'TRY', dailyRate: 500, depositAmount: 1000, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 25, minLicenseYears: 5 },
    { id: '1', branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY', dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
    { id: '2', branchId: 'b1', category: 'COMPACT', transmission: 'AUTOMATIC', currency: 'TRY', dailyRate: 150, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
    { id: '4', branchId: 'b1', category: 'ECONOMY', transmission: 'MANUAL', currency: 'TRY', dailyRate: 80, depositAmount: 400, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
  ];
  const sorted = m.sortRatesByOrder(items);
  assert.strictEqual(sorted[0].category, 'ECONOMY');
  assert.strictEqual(sorted[1].category, 'COMPACT');
  assert.strictEqual(sorted[1].transmission, 'MANUAL');
  assert.strictEqual(sorted[2].transmission, 'AUTOMATIC');
  assert.strictEqual(sorted[3].category, 'LUXURY');
});

test('sortRatesByOrder does not mutate original', () => {
  const items = [
    { id: '1', branchId: 'b1', category: 'SUV', transmission: 'MANUAL', currency: 'TRY', dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
    { id: '2', branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY', dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null, minDriverAge: 21, minLicenseYears: 2 },
  ];
  const sorted = m.sortRatesByOrder(items);
  // Original order: SUV before COMPACT
  assert.strictEqual(items[0].category, 'SUV');
  // Sorted: COMPACT before SUV
  assert.strictEqual(sorted[0].category, 'COMPACT');
});

// ── validateRateItem ─────────────────────────────────────────────────────────

test('validateRateItem valid item has no errors', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: 200, extraKmFee: 10,
    minDriverAge: 21, minLicenseYears: 2,
  };
  assert.deepStrictEqual(m.validateRateItem(item), {});
});

test('validateRateItem rejects dailyRate <= 0', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 0, depositAmount: 500, kmLimitPerDay: 200, extraKmFee: 10,
    minDriverAge: 21, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Gunluk fiyat')));
});

test('validateRateItem rejects negative depositAmount', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: -1, kmLimitPerDay: 200, extraKmFee: 10,
    minDriverAge: 21, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Garanti')));
});

test('validateRateItem rejects negative kmLimitPerDay', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: -1, extraKmFee: 10,
    minDriverAge: 21, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('km')));
});

test('validateRateItem allows null kmLimitPerDay', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 21, minLicenseYears: 2,
  };
  assert.deepStrictEqual(m.validateRateItem(item), {});
});

test('validateRateItem rejects minDriverAge < 18', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 17, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Min surucu yasi')));
});

test('validateRateItem rejects minDriverAge > 99', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 100, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Min surucu yasi')));
});

test('validateRateItem rejects minLicenseYears > 50', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 21, minLicenseYears: 51,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Min ehliyet yili')));
});

test('validateRateItem rejects negative extraKmFee', () => {
  const item = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: -1,
    minDriverAge: 21, minLicenseYears: 2,
  };
  const errs = m.validateRateItem(item);
  assert.ok(Object.values(errs).some(e => e.includes('Ek km ucreti')));
});

test('validateRateItem allows boundary minDriverAge 18 and 99', () => {
  const valid18 = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 18, minLicenseYears: 0,
  };
  assert.deepStrictEqual(m.validateRateItem(valid18), {});
  const valid99 = {
    branchId: 'b1', category: 'COMPACT', transmission: 'MANUAL', currency: 'TRY',
    dailyRate: 100, depositAmount: 500, kmLimitPerDay: null, extraKmFee: null,
    minDriverAge: 99, minLicenseYears: 50,
  };
  assert.deepStrictEqual(m.validateRateItem(valid99), {});
});

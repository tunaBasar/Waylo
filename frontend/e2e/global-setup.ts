import { randomBytes } from 'node:crypto';
import globalTeardown from './global-teardown';
import { adminAccount, createUser, isoDate, login, must, api, saveState, stateExists, type State } from './helpers';

const PDF = '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';

/** Creates a customer, a hotel partner (approved org + hotel with stock) and a car partner (approved org, two branches, one-way route). */
export default async function globalSetup() {
  // E2E_KEEP=1 keeps the fixture after a run so the next run can reuse it (faster local iteration).
  if (process.env.E2E_KEEP && stateExists()) return;
  // A fixture left behind by an interrupted or kept run is removed before creating a new one.
  if (stateExists()) await globalTeardown(true);
  const suffix = randomBytes(3).toString('hex');
  const customer = await createUser('cust', 'Musteri');
  const hotelPartner = await createUser('hotel', 'Otelci');
  const carPartner = await createUser('car', 'Filocu');
  const admin = await login(adminAccount());
  const hp = (await login(hotelPartner)).token;
  const cp = (await login(carPartner)).token;

  const hotelCity = `E2ECity${suffix}`;
  const hotelName = `E2E Hotel ${suffix}`;
  const hotelOrg = (await must('POST', '/api/organizations', { name: `e2e-hotel-org-${suffix}`, businessType: 'HOTEL', legalType: 'COMPANY' }, hp)).id;
  const hotelId = (await must('POST', '/api/hotels', { name: hotelName, city: hotelCity, country: 'TR', address: 'E2E only', starRating: 4, organizationId: hotelOrg }, hp)).id;
  await must('POST', `/api/admin/organizations/${hotelOrg}/approve`, undefined, admin.token);
  const roomName = `E2E Double ${suffix}`;
  const planName = `E2E Flex ${suffix}`;
  const room = await must('POST', `/api/hotels/${hotelId}/room-types`, { code: 'E2E_DBL', name: roomName, maxAdults: 2, maxChildren: 0, maxOccupancy: 2 }, hp);
  const plan = await must('POST', `/api/hotels/${hotelId}/rate-plans`, { code: 'E2E_FLEX', name: planName, mealPlan: 'BREAKFAST', cancellationPolicy: 'FREE_CANCELLATION', freeCancellationDaysBefore: 2 }, hp);
  const nights = [isoDate(50), isoDate(51)];
  const checkOut = isoDate(52);
  await must('PUT', `/api/hotels/${hotelId}/inventory`, { items: nights.map(n => ({ roomTypeId: room.id, stayDate: n, allotment: 5, expectedVersion: null })), reason: 'e2e fixture' }, hp);
  const amounts = [1000, 1500];
  await must('PUT', `/api/hotels/${hotelId}/rates`, { items: nights.map((n, i) => ({ roomTypeId: room.id, ratePlanId: plan.id, stayDate: n, occupancy: 2, currency: 'TRY', amount: amounts[i] })) }, hp);
  await must('POST', '/api/admin/commission-rates', { vertical: 'HOTEL', supplierOrganizationId: hotelOrg, rate: 0.125 }, admin.token);

  const carCity = `E2ECar${suffix}`;
  const carOrg = (await must('POST', '/api/organizations', { name: `e2e-car-org-${suffix}`, businessType: 'CAR_RENTAL', legalType: 'COMPANY' }, cp)).id;
  await must('POST', `/api/admin/organizations/${carOrg}/approve`, undefined, admin.token);
  const P = `/api/partner/organizations/${carOrg}`;
  const br = { city: carCity, address: 'E2E cad. 1', phone: '+905550000000', openTime: '00:00:00', closeTime: '23:59:00' };
  const carBranchAName = `E2E Sube A ${suffix}`;
  const carBranchBName = `E2E Sube B ${suffix}`;
  const branchA = (await must('POST', `${P}/branches`, { ...br, name: carBranchAName }, cp)).id;
  const branchB = (await must('POST', `${P}/branches`, { ...br, name: carBranchBName }, cp)).id;
  const vehicle = (await must('POST', `${P}/vehicles`, { homeBranchId: branchA, plateNumber: `34 E2E${100 + Math.floor(Math.random() * 900)}`, brand: 'Fiat', model: 'Egea', year: 2024, category: 'ECONOMY', transmission: 'MANUAL', fuelType: 'DIESEL', seatCount: 5 }, cp)).id;
  await must('PUT', `${P}/rates`, { items: [branchA, branchB].map(b => ({ branchId: b, category: 'ECONOMY', transmission: 'MANUAL', currency: 'TRY', dailyRate: 1500, depositAmount: 5000, kmLimitPerDay: 300, extraKmFee: 5, minDriverAge: 21, minLicenseYears: 2 })) }, cp);
  await must('PUT', `${P}/one-way-routes`, { routes: [{ fromBranchId: branchA, toBranchId: branchB, fee: 400, currency: 'TRY' }, { fromBranchId: branchB, toBranchId: branchA, fee: 400, currency: 'TRY' }] }, cp);
  const far = isoDate(400);
  const docs: [string, string, string | null][] = [
    ['RENTAL_AUTHORIZATION', 'YB-E2E', null], ['TAX_CERTIFICATE', 'VL-E2E', null],
    ['VEHICLE_REGISTRATION', 'RU-E2E', vehicle], ['RENTAL_INSURANCE', 'KS-E2E', vehicle], ['TRAFFIC_INSURANCE', 'TR-E2E', vehicle],
  ];
  for (const [type, num, veh] of docs) {
    const form = new FormData();
    form.set('type', type); form.set('documentNumber', num); form.set('validUntil', far);
    if (veh) form.set('vehicleId', veh);
    form.set('file', new Blob([PDF], { type: 'application/octet-stream' }), 'belge.pdf');
    const r = await api('POST', `${P}/documents`, undefined, cp, form);
    if (r.status !== 201) throw new Error(`document upload ${type} -> ${r.status}`);
    await must('POST', `/api/admin/car-documents/${r.data.id}/approve`, undefined, admin.token);
  }
  const status = await must('GET', `${P}/status`, undefined, cp);
  if (!status?.sellable) throw new Error('car org not sellable');
  await must('POST', '/api/admin/commission-rates', { vertical: 'CAR', supplierOrganizationId: carOrg, rate: 0.1 }, admin.token);

  const state: State = {
    customer, hotelPartner, carPartner, hotelOrgId: hotelOrg, carOrgId: carOrg, hotelId, hotelName, hotelCity, roomName, planName,
    checkIn: nights[0], checkOut, nightsTotal: 2500, carCity, branchA, branchB, carBranchAName, carBranchBName,
    orgIds: [hotelOrg, carOrg], emails: [customer.email, hotelPartner.email, carPartner.email], orderIds: [],
  };
  saveState(state);
}

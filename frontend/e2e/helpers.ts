import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import { deflateSync } from 'node:zlib';
import path from 'node:path';
import { expect, type BrowserContext, type Page } from '@playwright/test';

export const API = process.env.E2E_API_URL || 'http://localhost:8080';
const ROOT = path.resolve(__dirname, '..', '..');
const STATE_FILE = path.join(__dirname, '.state.json');

export interface Account { email: string; password: string }
/** Untyped JSON default for fixture calls. Callers that assert on fields pass an explicit type argument. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Loose = any;
/** Body of POST /api/auth/login (auth-service AuthResponse). */
export interface Session { token: string; refreshToken: string; userId: string; email: string; firstName: string; lastName: string; role: string; emailVerified: boolean }
export interface State {
  customer: Account;
  hotelPartner: Account;
  carPartner: Account;
  hotelOrgId: string;
  carOrgId: string;
  hotelId: string;
  hotelName: string;
  hotelCity: string;
  roomName: string;
  planName: string;
  checkIn: string;
  checkOut: string;
  nightsTotal: number;
  carCity: string;
  branchA: string;
  branchB: string;
  carBranchAName: string;
  carBranchBName: string;
  orgIds: string[];
  emails: string[];
  orderIds: string[];
  hotelOrderId?: string;
  hotelItemId?: string;
}

export function loadState(): State { return JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')); }
export function saveState(s: State) { fs.writeFileSync(STATE_FILE, JSON.stringify(s), { mode: 0o600 }); }
export function stateExists() { return fs.existsSync(STATE_FILE); }
export function removeState() { fs.rmSync(STATE_FILE, { force: true }); }
export function patchState(p: Partial<State>) { saveState({ ...loadState(), ...p }); }

export function envValue(key: string): string {
  for (const line of fs.readFileSync(path.join(ROOT, '.env'), 'utf8').split('\n')) {
    if (line.startsWith(key + '=')) return line.slice(key.length + 1).trim().replace(/^["']|["']$/g, '');
  }
  throw new Error(`${key} missing in .env`);
}

export function adminAccount(): Account {
  return { email: envValue('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), password: envValue('WAYLO_BOOTSTRAP_ADMIN_PASSWORD') };
}

export function psql(db: string, sql: string): string {
  return execFileSync('docker', ['exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql], { encoding: 'utf8' }).trim();
}

export async function api<T = Loose>(method: string, p: string, body?: unknown, token?: string, form?: FormData): Promise<{ status: number; data: T }> {
  const headers: Record<string, string> = {};
  if (!form) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(API + p, { method, headers, body: form ?? (body === undefined ? undefined : JSON.stringify(body)) });
  const text = await res.text();
  let json: { data?: unknown } | null = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not json */ }
  return { status: res.status, data: json?.data as T };
}

export async function must<T = Loose>(method: string, p: string, body?: unknown, token?: string): Promise<T> {
  const r = await api<T>(method, p, body, token);
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status}`);
  return r.data;
}

const sessions = new Map<string, { at: number; data: Session }>();

/** Logs in once per account per 5 minutes (the gateway limits /api/auth/login per IP), retrying on 429. */
export async function login(a: Account): Promise<Session> {
  const hit = sessions.get(a.email);
  if (hit && Date.now() - hit.at < 300_000) return hit.data;
  for (let i = 0; ; i++) {
    const r = await api<Session>('POST', '/api/auth/login', a);
    if (r.status === 429 && i < 8) { await sleep(10_000); continue; }
    if (r.status >= 300) throw new Error(`login -> ${r.status}`);
    sessions.set(a.email, { at: Date.now(), data: r.data });
    return r.data;
  }
}

export const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

/** Registers + verifies a user. The gateway limits register to 5/min, so callers space them out. */
export async function createUser(prefix: string, first: string): Promise<Account> {
  const email = `e2e-${prefix}-${randomBytes(5).toString('hex')}@example.test`;
  const password = 'E2e1' + randomBytes(14).toString('base64url');
  await must('POST', '/api/auth/register', { firstName: first, lastName: 'E2E', email, password });
  psql('auth_db', `UPDATE users SET email_verified = true WHERE email = '${email}'`);
  await sleep(13_000);
  return { email, password };
}

/** Puts the user's tokens into localStorage before any page script runs. */
export async function signIn(context: BrowserContext, a: Account) {
  const t = await login(a);
  const user = JSON.stringify({ id: t.userId, firstName: t.firstName, lastName: t.lastName, email: t.email, role: t.role, emailVerified: t.emailVerified });
  await context.addInitScript(([tok, ref, u]) => {
    localStorage.setItem('waylo_token', tok);
    localStorage.setItem('waylo_refresh', ref);
    localStorage.setItem('waylo_user', u);
  }, [t.token, t.refreshToken, user]);
  return t.token;
}

/** Fails when a material symbol ligature name is visible as words (icon font not applied). */
export async function expectNoRawIcons(page: Page) {
  await page.waitForLoadState('load');
  const scan = () => page.evaluate(async () => {
    await document.fonts.ready;
    const out: string[] = [];
    document.querySelectorAll('.material-symbols-outlined').forEach(el => {
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) return;
      const size = parseFloat(getComputedStyle(el).fontSize) || 24;
      const text = (el.textContent || '').trim();
      // A ligature renders about one em wide. Words like "calendar_month" in a text font are much wider.
      if (text.length > 2 && r.width > size * 1.8) out.push(text);
    });
    return out;
  });
  // Allow the icon font a few seconds to swap in, then the list must be empty.
  await expect.poll(scan, { message: 'icons rendered as raw text', timeout: 8_000 }).toEqual([]);
}

export function isoDate(daysAhead: number): string {
  const d = new Date(Date.now() + daysAhead * 86_400_000);
  return d.toISOString().slice(0, 10);
}

/** Solid colour RGB PNG built with node zlib (same layout as png() in scripts/test-hotel-media.py). */
export function pngBuffer(w: number, h: number): Buffer {
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
  ihdr[8] = 8; ihdr[9] = 2;
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array.from({ length: w }, () => [0x80, 0x40, 0xc0]).flat())]);
  const raw = Buffer.concat(Array.from({ length: h }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]);
}

import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { api } from '@/api';

const STORAGE_KEY = 'waylo.push.token.v1';

export interface PushDeps {
  /** Resolves the EAS projectId, or null when the app is not linked to an EAS project yet. */
  projectId(): string | null;
  isDevice(): boolean;
  platform(): 'ios' | 'android' | 'web';
  /** Asks for permission if needed; true when granted. */
  requestPermission(): Promise<boolean>;
  getExpoToken(projectId: string): Promise<string>;
  post(path: string, body: unknown): Promise<unknown>;
  del(path: string): Promise<unknown>;
  load(): Promise<string | null>;
  save(token: string | null): Promise<void>;
  warn(msg: string): void;
}

/**
 * Registers this device for push after login. Never throws: push is optional and must not break login.
 * Returns the registered token, or null when skipped.
 */
export async function registerForPush(d: PushDeps): Promise<string | null> {
  try {
    const projectId = d.projectId();
    if (!projectId) {
      d.warn('[push] no EAS projectId configured (extra.eas.projectId), skipping push registration');
      return null;
    }
    if (!d.isDevice()) return null;
    if (!(await d.requestPermission())) return null;
    const token = await d.getExpoToken(projectId);
    await d.post('/api/me/devices', { token, platform: d.platform() });
    await d.save(token);
    return token;
  } catch (e) {
    d.warn(`[push] registration failed: ${e instanceof Error ? e.message : String(e)}`);
    return null;
  }
}

/** Removes the stored token from the backend. Call BEFORE the session tokens are cleared. Never throws. */
export async function unregisterFromPush(d: PushDeps): Promise<void> {
  try {
    const token = await d.load();
    if (!token) return;
    await d.save(null);
    await d.del(`/api/me/devices/${encodeURIComponent(token)}`);
  } catch (e) {
    d.warn(`[push] unregister failed: ${e instanceof Error ? e.message : String(e)}`);
  }
}

export const defaultPushDeps: PushDeps = {
  projectId: () => {
    const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
    return extra?.eas?.projectId ?? Constants.easConfig?.projectId ?? null;
  },
  isDevice: () => Device.isDevice,
  platform: () => (Platform.OS === 'ios' || Platform.OS === 'android' ? Platform.OS : 'web'),
  requestPermission: async () => {
    const cur = await Notifications.getPermissionsAsync();
    if (cur.granted) return true;
    return (await Notifications.requestPermissionsAsync()).granted;
  },
  getExpoToken: async (projectId) => (await Notifications.getExpoPushTokenAsync({ projectId })).data,
  post: (path, body) => api.post(path, body),
  del: (path) => api.del(path),
  load: () => AsyncStorage.getItem(STORAGE_KEY),
  save: (t) => (t ? AsyncStorage.setItem(STORAGE_KEY, t) : AsyncStorage.removeItem(STORAGE_KEY)),
  warn: (m) => console.warn(m),
};

import { registerForPush, unregisterFromPush, type PushDeps } from '../src/features/push/registration';

function deps(over: Partial<PushDeps> = {}): PushDeps & { stored: { v: string | null } } {
  const stored = { v: null as string | null };
  return {
    stored,
    projectId: () => 'proj-1',
    isDevice: () => true,
    platform: () => 'android',
    requestPermission: jest.fn().mockResolvedValue(true),
    getExpoToken: jest.fn().mockResolvedValue('ExponentPushToken[abc]'),
    post: jest.fn().mockResolvedValue({}),
    del: jest.fn().mockResolvedValue({}),
    load: async () => stored.v,
    save: async (t) => { stored.v = t; },
    warn: jest.fn(),
    ...over,
  };
}

describe('push registration', () => {
  it('registers the Expo token with the backend and remembers it', async () => {
    const d = deps();
    await expect(registerForPush(d)).resolves.toBe('ExponentPushToken[abc]');
    expect(d.post).toHaveBeenCalledWith('/api/me/devices', { token: 'ExponentPushToken[abc]', platform: 'android' });
    expect(d.stored.v).toBe('ExponentPushToken[abc]');
  });

  it('skips with a warning when the EAS projectId is missing', async () => {
    const d = deps({ projectId: () => null });
    await expect(registerForPush(d)).resolves.toBeNull();
    expect(d.warn).toHaveBeenCalledWith(expect.stringContaining('projectId'));
    expect(d.requestPermission).not.toHaveBeenCalled();
    expect(d.post).not.toHaveBeenCalled();
  });

  it('does nothing when permission is denied or not a physical device', async () => {
    const denied = deps({ requestPermission: jest.fn().mockResolvedValue(false) });
    await expect(registerForPush(denied)).resolves.toBeNull();
    expect(denied.post).not.toHaveBeenCalled();
    const sim = deps({ isDevice: () => false });
    await expect(registerForPush(sim)).resolves.toBeNull();
    expect(sim.requestPermission).not.toHaveBeenCalled();
  });

  it('never throws when the backend call fails', async () => {
    const d = deps({ post: jest.fn().mockRejectedValue(new Error('boom')) });
    await expect(registerForPush(d)).resolves.toBeNull();
    expect(d.warn).toHaveBeenCalledWith(expect.stringContaining('boom'));
  });

  it('unregisters the stored token (url-encoded) and clears it', async () => {
    const d = deps();
    d.stored.v = 'ExponentPushToken[abc]';
    await unregisterFromPush(d);
    expect(d.del).toHaveBeenCalledWith('/api/me/devices/ExponentPushToken%5Babc%5D');
    expect(d.stored.v).toBeNull();
  });

  it('unregister is a no-op without a stored token and swallows errors', async () => {
    const none = deps();
    await unregisterFromPush(none);
    expect(none.del).not.toHaveBeenCalled();
    const bad = deps({ del: jest.fn().mockRejectedValue(new Error('x')) });
    bad.stored.v = 't';
    await expect(unregisterFromPush(bad)).resolves.toBeUndefined();
  });
});

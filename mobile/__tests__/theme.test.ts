import { darkTheme, lightTheme, parsePreference, resolveScheme } from '@/theme/theme';

describe('resolveScheme', () => {
  it('explicit choice wins over the device', () => {
    expect(resolveScheme('light', 'dark')).toBe('light');
    expect(resolveScheme('dark', 'light')).toBe('dark');
    expect(resolveScheme('dark', null)).toBe('dark');
  });
  it('system follows the device and defaults to light', () => {
    expect(resolveScheme('system', 'dark')).toBe('dark');
    expect(resolveScheme('system', 'light')).toBe('light');
    expect(resolveScheme('system', null)).toBe('light');
    expect(resolveScheme('system', undefined)).toBe('light');
  });
});

describe('parsePreference', () => {
  it('accepts only known values', () => {
    expect(parsePreference('light')).toBe('light');
    expect(parsePreference('dark')).toBe('dark');
    expect(parsePreference('system')).toBe('system');
    expect(parsePreference('blue')).toBe('system');
    expect(parsePreference(null)).toBe('system');
  });
});

describe('themes', () => {
  it('have the same color keys', () => {
    expect(Object.keys(darkTheme.color).sort()).toEqual(Object.keys(lightTheme.color).sort());
  });
  it('cards: light hairline, dark borderless', () => {
    expect(lightTheme.card.borderWidth).toBe(1);
    expect(darkTheme.card.borderWidth).toBe(0);
  });
});

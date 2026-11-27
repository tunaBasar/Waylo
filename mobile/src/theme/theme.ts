import type { TextStyle } from 'react-native';

export type Scheme = 'light' | 'dark';
export type ThemePreference = 'system' | 'light' | 'dark';

export type ThemeColor = {
  primary: string; onPrimary: string; primaryContainer: string; onPrimaryContainer: string;
  secondary: string; onSecondary: string; secondaryContainer: string; onSecondaryContainer: string;
  tertiary: string; onTertiary: string; tertiaryContainer: string; onTertiaryContainer: string;
  success: string; onSuccess: string; successContainer: string; onSuccessContainer: string;
  warning: string; onWarning: string; warningContainer: string; onWarningContainer: string;
  error: string; onError: string; errorContainer: string; onErrorContainer: string;
  background: string; surface: string; surfaceLowest: string; surfaceLow: string; surfaceContainer: string; surfaceHigh: string;
  onSurface: string; onSurfaceVariant: string; outline: string; outlineVariant: string;
  backdrop: string;
  seatEmptyBg: string; seatEmptyBorder: string; seatSelectedBg: string; seatSelectedFg: string;
  seatFemaleBg: string; seatFemaleFg: string; seatMaleBg: string; seatMaleFg: string;
};

export type ThemeFont = { display: string; body: string; bodyBold: string; mono: string; monoSemi: string };
export type TypeKey = 'displayLgMobile' | 'headlineLg' | 'headlineMd' | 'titleMd' | 'bodyLg' | 'bodyMd' | 'labelLg' | 'labelSm' | 'monoMd' | 'monoSm';
export type ThemeRadius = { sm: number; md: number; lg: number; xl: number; full: number };

export type Theme = {
  name: Scheme;
  color: ThemeColor;
  font: ThemeFont;
  type: Record<TypeKey, TextStyle>;
  radius: ThemeRadius;
  /** Card surface: light = surfaceLowest + hairline, dark = surfaceLow tonal, no border. */
  card: { backgroundColor: string; borderWidth: number; borderColor: string };
  /** Dark floats the bottom bar, light docks it. */
  floatingBottomBar: boolean;
};

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32 } as const;
export const MIN_TOUCH = 44;
export const PAGE_MARGIN = 20;

const t = (fontFamily: string, fontSize: number, lineHeight: number, letterSpacing = 0, extra?: TextStyle): TextStyle =>
  ({ fontFamily, fontSize, lineHeight, letterSpacing, ...extra });
const tab: TextStyle = { fontVariant: ['tabular-nums'] };

const lightColor: ThemeColor = {
  primary: '#973319', onPrimary: '#FFFFFF', primaryContainer: '#B84A2E', onPrimaryContainer: '#FFECE8',
  secondary: '#466275', onSecondary: '#FFFFFF', secondaryContainer: '#C7E3FA', onSecondaryContainer: '#173345',
  tertiary: '#005E6E', onTertiary: '#FFFFFF', tertiaryContainer: '#D8F6FF', onTertiaryContainer: '#003640',
  success: '#1B6B44', onSuccess: '#FFFFFF', successContainer: '#DCF5E7', onSuccessContainer: '#0E3F27',
  warning: '#8C5100', onWarning: '#FFFFFF', warningContainer: '#FFF0D6', onWarningContainer: '#4D2B00',
  error: '#BA1A1A', onError: '#FFFFFF', errorContainer: '#FFDAD6', onErrorContainer: '#690005',
  background: '#FAF7F2', surface: '#FAF7F2', surfaceLowest: '#FFFFFF', surfaceLow: '#F5EFE6',
  surfaceContainer: '#EDE5D8', surfaceHigh: '#E5DACB',
  onSurface: '#241916', onSurfaceVariant: '#57423D', outline: '#8B716B', outlineVariant: '#D9C9BE',
  backdrop: '#EDE5D8',
  seatEmptyBg: '#FFFFFF', seatEmptyBorder: '#D9C9BE', seatSelectedBg: '#973319', seatSelectedFg: '#FFFFFF',
  seatFemaleBg: '#D8F6FF', seatFemaleFg: '#003640', seatMaleBg: '#C7E3FA', seatMaleFg: '#173345',
};

const darkColor: ThemeColor = {
  primary: '#F59E0B', onPrimary: '#1A120B', primaryContainer: '#4D2600', onPrimaryContainer: '#FFDCC3',
  secondary: '#7BD0FF', onSecondary: '#00354A', secondaryContainer: '#004C69', onSecondaryContainer: '#E3F3FF',
  tertiary: '#FFB77D', onTertiary: '#4D2600', tertiaryContainer: '#6E3900', onTertiaryContainer: '#FFEDE3',
  success: '#4ADE80', onSuccess: '#052E16', successContainer: '#14532D', onSuccessContainer: '#DCFCE7',
  warning: '#FBBF24', onWarning: '#1A120B', warningContainer: '#451A03', onWarningContainer: '#FEF3C7',
  error: '#FCA5A5', onError: '#450A0A', errorContainer: '#7F1D1D', onErrorContainer: '#FEE2E2',
  background: '#0F141B', surface: '#0F141B', surfaceLowest: '#0B0F15', surfaceLow: '#161D26',
  surfaceContainer: '#1C2531', surfaceHigh: '#253040',
  onSurface: '#F1F5F9', onSurfaceVariant: '#B6C2D1', outline: '#64748B', outlineVariant: '#2B3748',
  backdrop: '#0B0F15',
  seatEmptyBg: '#161D26', seatEmptyBorder: '#2B3748', seatSelectedBg: '#F59E0B', seatSelectedFg: '#1A120B',
  seatFemaleBg: '#6E3900', seatFemaleFg: '#FFEDE3', seatMaleBg: '#004C69', seatMaleFg: '#E3F3FF',
};

const lightFont: ThemeFont = {
  display: 'CrimsonPro_600SemiBold', body: 'Carlito_400Regular', bodyBold: 'Carlito_700Bold',
  mono: 'SourceCodePro_500Medium', monoSemi: 'SourceCodePro_600SemiBold',
};
const darkFont: ThemeFont = {
  display: 'AlegreyaSans_700Bold', body: 'Alegreya_400Regular', bodyBold: 'AlegreyaSans_700Bold',
  mono: 'SourceCodePro_500Medium', monoSemi: 'SourceCodePro_600SemiBold',
};

export const lightTheme: Theme = {
  name: 'light',
  color: lightColor,
  font: lightFont,
  type: {
    displayLgMobile: t(lightFont.display, 28, 34, -0.25),
    headlineLg: t(lightFont.display, 24, 30),
    headlineMd: t(lightFont.display, 20, 26),
    titleMd: t(lightFont.bodyBold, 16, 22),
    bodyLg: t(lightFont.body, 16, 24),
    bodyMd: t(lightFont.body, 14, 20),
    labelLg: t(lightFont.bodyBold, 14, 20),
    labelSm: t(lightFont.bodyBold, 12, 16),
    monoMd: t(lightFont.monoSemi, 14, 20, 0, tab),
    monoSm: t(lightFont.mono, 12, 16, 0, tab),
  },
  radius: { sm: 6, md: 12, lg: 16, xl: 20, full: 9999 },
  card: { backgroundColor: lightColor.surfaceLowest, borderWidth: 1, borderColor: lightColor.outlineVariant },
  floatingBottomBar: false,
};

export const darkTheme: Theme = {
  name: 'dark',
  color: darkColor,
  font: darkFont,
  type: {
    displayLgMobile: t(darkFont.display, 28, 34, -0.2),
    headlineLg: t(darkFont.display, 22, 28, -0.1),
    headlineMd: t(darkFont.display, 18, 24),
    titleMd: t(darkFont.bodyBold, 16, 22),
    bodyLg: t(darkFont.body, 16, 24),
    bodyMd: t(darkFont.body, 15, 21),
    labelLg: t(darkFont.bodyBold, 14, 20, 0.2),
    labelSm: t(darkFont.bodyBold, 12, 16, 0.3),
    monoMd: t(darkFont.monoSemi, 14, 20, 0, tab),
    monoSm: t(darkFont.mono, 12, 16, 0.1, tab),
  },
  radius: { sm: 8, md: 14, lg: 20, xl: 24, full: 9999 },
  card: { backgroundColor: darkColor.surfaceLow, borderWidth: 0, borderColor: 'transparent' },
  floatingBottomBar: true,
};

export const THEME_STORAGE_KEY = 'waylo_theme';

/** The scheme to show: an explicit choice wins, 'system' follows the device (unknown -> light). */
export function resolveScheme(preference: ThemePreference, systemScheme: string | null | undefined): Scheme {
  if (preference === 'light' || preference === 'dark') return preference;
  return systemScheme === 'dark' ? 'dark' : 'light';
}

/** Parses a stored value; anything unknown means 'system'. */
export function parsePreference(raw: string | null | undefined): ThemePreference {
  return raw === 'light' || raw === 'dark' ? raw : 'system';
}

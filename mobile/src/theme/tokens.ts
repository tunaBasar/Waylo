import type { TextStyle } from 'react-native';

export const color = {
  primary: '#003772', onPrimary: '#FFFFFF', primaryContainer: '#124E96', onPrimaryContainer: '#ECF0FF',
  secondary: '#006C53', secondaryContainer: '#D8F6EA', onSecondaryContainer: '#00382A',
  tertiary: '#743500', tertiaryContainer: '#FFEDE5', onTertiaryContainer: '#522300',
  success: '#006C53', successContainer: '#D8F6EA',
  warning: '#8C4A00', warningContainer: '#FFF0D9', onWarningContainer: '#4D2600',
  error: '#BA1A1A', errorContainer: '#FFDAD6', onErrorContainer: '#690005',
  background: '#F4F7FA', surface: '#F4F7FA', surfaceLowest: '#FFFFFF', surfaceLow: '#EDF1F7',
  surfaceContainer: '#E4E9F2', surfaceHigh: '#DCE2EC',
  onSurface: '#191C20', onSurfaceVariant: '#424751', outline: '#737782', outlineVariant: '#C8D0DC',
} as const;

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32 } as const;
export const radius = { sm: 4, md: 8, lg: 12, xl: 16, full: 9999 } as const;

export const font = {
  display: 'WixMadeforDisplay_700Bold',
  displaySemi: 'WixMadeforDisplay_600SemiBold',
  body: 'InstrumentSans_400Regular',
  bodySemi: 'InstrumentSans_600SemiBold',
  bodyBold: 'InstrumentSans_700Bold',
  mono: 'SplineSansMono_500Medium',
  monoSemi: 'SplineSansMono_600SemiBold',
} as const;

const t = (fontFamily: string, fontSize: number, lineHeight: number, letterSpacing: number, extra?: TextStyle): TextStyle =>
  ({ fontFamily, fontSize, lineHeight, letterSpacing, ...extra });

export const type = {
  displayLgMobile: t(font.display, 26, 32, -0.4),
  headlineLg: t(font.display, 22, 28, -0.3),
  headlineMd: t(font.displaySemi, 18, 24, -0.2),
  titleMd: t(font.bodyBold, 15, 20, 0),
  bodyLg: t(font.body, 15, 22, 0),
  bodyMd: t(font.body, 13, 18, 0),
  labelLg: t(font.bodySemi, 14, 18, 0.1),
  labelSm: t(font.bodySemi, 12, 16, 0.2),
  monoMd: t(font.monoSemi, 14, 18, 0, { fontVariant: ['tabular-nums'] }),
  monoSm: t(font.mono, 12, 16, 0.1, { fontVariant: ['tabular-nums'] }),
} as const;

export const MIN_TOUCH = 44;

import { lightTheme, MIN_TOUCH as MT, space as sp } from './theme';

export const space = sp;
export const MIN_TOUCH = MT;

/** @deprecated module-level colors cannot follow the theme: use useTheme()/useStyles(). Light values only. */
export const color = lightTheme.color;
/** @deprecated use useTheme().type */
export const type = lightTheme.type;
/** @deprecated use useTheme().radius */
export const radius = lightTheme.radius;
/** @deprecated use useTheme().font */
export const font = {
  ...lightTheme.font,
  displaySemi: lightTheme.font.display,
  bodySemi: lightTheme.font.bodyBold,
} as const;

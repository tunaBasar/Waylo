import { THEME_FONTS } from '@/theme/fonts';
import { darkTheme, lightTheme } from '@/theme/theme';

describe('theme fonts', () => {
  it.each([lightTheme, darkTheme])('every fontFamily of the $name theme is loaded', (theme) => {
    for (const style of Object.values(theme.type)) {
      expect(Object.keys(THEME_FONTS)).toContain(style.fontFamily);
    }
  });
});

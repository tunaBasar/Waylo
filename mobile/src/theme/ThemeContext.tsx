import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { darkTheme, lightTheme, parsePreference, resolveScheme, THEME_STORAGE_KEY, type Theme, type ThemePreference } from './theme';

type Ctx = { theme: Theme; preference: ThemePreference; setPreference: (p: ThemePreference) => void };

const ThemeCtx = createContext<Ctx>({ theme: lightTheme, preference: 'system', setPreference: () => {} });

export function ThemeProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const [preference, setPref] = useState<ThemePreference>('system');
  // loaded once at start; a choice made before the read finishes wins
  useEffect(() => {
    let alive = true;
    AsyncStorage.getItem(THEME_STORAGE_KEY).then((raw) => { if (alive) setPref((cur) => (cur === 'system' ? parsePreference(raw) : cur)); }).catch(() => {});
    return () => { alive = false; };
  }, []);
  const setPreference = useCallback((p: ThemePreference) => {
    setPref(p);
    AsyncStorage.setItem(THEME_STORAGE_KEY, p).catch(() => {});
  }, []);
  const value = useMemo<Ctx>(
    () => ({ theme: resolveScheme(preference, system) === 'dark' ? darkTheme : lightTheme, preference, setPreference }),
    [preference, system, setPreference],
  );
  return <ThemeCtx.Provider value={value}>{children}</ThemeCtx.Provider>;
}

/** The active theme; the light theme when no provider is mounted (tests). */
export const useTheme = (): Theme => useContext(ThemeCtx).theme;

export function useThemePreference() {
  const { preference, setPreference } = useContext(ThemeCtx);
  return { preference, setPreference };
}

/** Styles built from the theme, recreated only when the theme changes. */
export function useStyles<T extends object>(factory: (t: Theme) => T): T {
  const theme = useTheme();
  return useMemo(() => factory(theme), [factory, theme]);
}

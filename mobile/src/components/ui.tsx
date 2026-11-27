import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';
import type { ReactNode } from 'react';
import type { AsyncState } from '@/features/useAsync';
import { space } from '@/theme/tokens';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { lightTheme, type Theme } from '@/theme/theme';

/** @deprecated light-only snapshot; use useTheme().color. */
export const colors = { primary: lightTheme.color.primary, bg: lightTheme.color.background, card: lightTheme.color.surfaceLowest, text: lightTheme.color.onSurface, muted: lightTheme.color.onSurfaceVariant, error: lightTheme.color.error, errorBg: lightTheme.color.errorContainer, ok: lightTheme.color.success };

export function Screen({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  const s = useStyles(makeStyles);
  return scroll
    ? <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">{children}</ScrollView>
    : <View style={[s.screen, s.content]}>{children}</View>;
}

export const Title = ({ children }: { children: ReactNode }) => { const s = useStyles(makeStyles); return <Text style={s.title}>{children}</Text>; };
export const Muted = ({ children }: { children: ReactNode }) => { const s = useStyles(makeStyles); return <Text style={s.muted}>{children}</Text>; };
export const Card = ({ children }: { children: ReactNode }) => { const s = useStyles(makeStyles); return <View style={s.card}>{children}</View>; };

export function Button({ title, onPress, disabled, kind = 'primary' }: { title: string; onPress: () => void; disabled?: boolean; kind?: 'primary' | 'secondary' | 'danger' }) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const bg = kind === 'primary' ? color.primary : kind === 'danger' ? color.error : color.surfaceContainer;
  const fg = kind === 'primary' ? color.onPrimary : kind === 'danger' ? color.onError : color.onSurface;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{ disabled: !!disabled }} disabled={disabled} onPress={onPress}
      style={[s.btn, { backgroundColor: bg, opacity: disabled ? 0.5 : 1 }]}>
      <Text style={[s.btnText, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function Field({ label, onFocus, onBlur, style, ...p }: { label: string } & TextInputProps) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ gap: space.xs }}>
      <Text style={s.label}>{label}</Text>
      <TextInput accessibilityLabel={label} placeholderTextColor={color.outline} autoCapitalize="none" selectionColor={color.primary}
        style={[s.input, focused && { borderColor: color.primary }, style]}
        onFocus={(e) => { setFocused(true); onFocus?.(e); }}
        onBlur={(e) => { setFocused(false); onBlur?.(e); }}
        {...p} />
    </View>
  );
}

export const ErrorBanner = ({ message }: { message: string | null }) => {
  const s = useStyles(makeStyles);
  return message ? <Text accessibilityRole="alert" style={s.errorBanner}>{message}</Text> : null;
};

export const TestBanner = () => { const s = useStyles(makeStyles); return <Text style={s.testBanner}>TEST ÖDEME: gerçek para çekilmez.</Text>; };

/** Renders loading / offline / error / empty states, otherwise children(data). */
export function AsyncView<T>({ state, isEmpty, emptyText, children }: {
  state: AsyncState<T>; isEmpty?: (d: T) => boolean; emptyText: string; children: (d: T) => ReactNode;
}) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  if (state.loading) return <View style={s.center}><ActivityIndicator color={color.primary} accessibilityLabel="Yükleniyor" /><Muted>Yükleniyor...</Muted></View>;
  if (state.error) {
    return (
      <View style={s.center}>
        <ErrorBanner message={state.offline ? 'Çevrimdışısınız. Fiyat ve müsaitlik için internet bağlantısı gerekir.' : state.error} />
        <Button title="Tekrar dene" kind="secondary" onPress={state.reload} />
      </View>
    );
  }
  if (state.data == null || (isEmpty && isEmpty(state.data))) return <View style={s.center}><Muted>{emptyText}</Muted></View>;
  return <>{children(state.data)}</>;
}

const makeStyles = ({ color, type, radius, card }: Theme) => StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.md },
  title: { ...type.headlineLg, color: color.onSurface },
  muted: { ...type.bodyMd, color: color.onSurfaceVariant },
  card: { backgroundColor: card.backgroundColor, borderRadius: radius.lg, borderWidth: card.borderWidth, borderColor: card.borderColor, padding: space.lg, gap: space.sm },
  btn: { minHeight: 48, borderRadius: radius.md, paddingVertical: space.md, paddingHorizontal: space.lg, alignItems: 'center', justifyContent: 'center' },
  btnText: { ...type.labelLg },
  label: { ...type.labelSm, color: color.onSurfaceVariant },
  input: { ...type.bodyLg, minHeight: 48, backgroundColor: color.surfaceLowest, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant, paddingHorizontal: space.md, paddingVertical: space.sm, color: color.onSurface },
  errorBanner: { ...type.bodyMd, backgroundColor: color.errorContainer, color: color.onErrorContainer, padding: space.md, borderRadius: radius.md, overflow: 'hidden' },
  testBanner: { ...type.labelLg, backgroundColor: color.warningContainer, color: color.onWarningContainer, padding: space.sm, borderRadius: radius.md, overflow: 'hidden' },
  center: { alignItems: 'center', gap: space.md, paddingVertical: space.xxl },
});

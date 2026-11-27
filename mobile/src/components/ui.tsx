import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';
import type { ReactNode } from 'react';
import type { AsyncState } from '@/features/useAsync';
import { color, radius, space, type } from '@/theme/tokens';

export const colors = { primary: color.primary, bg: color.background, card: color.surfaceLowest, text: color.onSurface, muted: color.onSurfaceVariant, error: color.error, errorBg: color.errorContainer, ok: color.success };

export function Screen({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  return scroll
    ? <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">{children}</ScrollView>
    : <View style={[s.screen, s.content]}>{children}</View>;
}

export const Title = ({ children }: { children: ReactNode }) => <Text style={s.title}>{children}</Text>;
export const Muted = ({ children }: { children: ReactNode }) => <Text style={s.muted}>{children}</Text>;
export const Card = ({ children }: { children: ReactNode }) => <View style={s.card}>{children}</View>;

export function Button({ title, onPress, disabled, kind = 'primary' }: { title: string; onPress: () => void; disabled?: boolean; kind?: 'primary' | 'secondary' | 'danger' }) {
  const bg = kind === 'primary' ? color.primary : kind === 'danger' ? color.error : color.surfaceContainer;
  const fg = kind === 'secondary' ? color.onSurface : color.onPrimary;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{ disabled: !!disabled }} disabled={disabled} onPress={onPress}
      style={[s.btn, { backgroundColor: bg, opacity: disabled ? 0.5 : 1 }]}>
      <Text style={[type.labelLg, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function Field({ label, onFocus, onBlur, style, ...p }: { label: string } & TextInputProps) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ gap: space.xs }}>
      <Text style={s.label}>{label}</Text>
      <TextInput accessibilityLabel={label} placeholderTextColor={color.outline} autoCapitalize="none"
        style={[s.input, focused && { borderColor: color.primary }, style]}
        onFocus={(e) => { setFocused(true); onFocus?.(e); }}
        onBlur={(e) => { setFocused(false); onBlur?.(e); }}
        {...p} />
    </View>
  );
}

export const ErrorBanner = ({ message }: { message: string | null }) =>
  message ? <Text accessibilityRole="alert" style={s.errorBanner}>{message}</Text> : null;

export const TestBanner = () => <Text style={s.testBanner}>TEST ÖDEME: gerçek para çekilmez.</Text>;

/** Renders loading / offline / error / empty states, otherwise children(data). */
export function AsyncView<T>({ state, isEmpty, emptyText, children }: {
  state: AsyncState<T>; isEmpty?: (d: T) => boolean; emptyText: string; children: (d: T) => ReactNode;
}) {
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

const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.lg, gap: space.md },
  title: { ...type.headlineLg, color: color.onSurface },
  muted: { ...type.bodyMd, color: color.onSurfaceVariant },
  card: { backgroundColor: color.surfaceLowest, borderRadius: radius.lg, borderWidth: 1, borderColor: color.outlineVariant, padding: space.lg, gap: space.sm },
  btn: { minHeight: 44, borderRadius: radius.md, paddingVertical: space.md, paddingHorizontal: space.lg, alignItems: 'center', justifyContent: 'center' },
  label: { ...type.labelSm, color: color.onSurfaceVariant },
  input: { ...type.bodyLg, minHeight: 44, backgroundColor: color.surfaceLowest, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant, paddingHorizontal: space.md, paddingVertical: space.sm, color: color.onSurface },
  errorBanner: { ...type.bodyMd, backgroundColor: color.errorContainer, color: color.onErrorContainer, padding: space.md, borderRadius: radius.md, overflow: 'hidden' },
  testBanner: { ...type.labelLg, backgroundColor: color.warningContainer, color: color.onWarningContainer, padding: space.sm, borderRadius: radius.md, overflow: 'hidden' },
  center: { alignItems: 'center', gap: space.md, paddingVertical: space.xxl },
});

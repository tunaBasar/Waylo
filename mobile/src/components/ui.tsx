import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';
import type { ReactNode } from 'react';
import type { AsyncState } from '@/features/useAsync';

export const colors = { primary: '#0b57d0', bg: '#f6f7f9', card: '#fff', text: '#1b1b1f', muted: '#5f6368', error: '#b3261e', errorBg: '#fde7e9', ok: '#1b6e3a' };

export function Screen({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  return scroll
    ? <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">{children}</ScrollView>
    : <View style={[s.screen, s.content]}>{children}</View>;
}

export const Title = ({ children }: { children: ReactNode }) => <Text style={s.title}>{children}</Text>;
export const Muted = ({ children }: { children: ReactNode }) => <Text style={s.muted}>{children}</Text>;
export const Card = ({ children }: { children: ReactNode }) => <View style={s.card}>{children}</View>;

export function Button({ title, onPress, disabled, kind = 'primary' }: { title: string; onPress: () => void; disabled?: boolean; kind?: 'primary' | 'secondary' | 'danger' }) {
  const bg = kind === 'primary' ? colors.primary : kind === 'danger' ? colors.error : '#e3e6ec';
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={title} disabled={disabled} onPress={onPress}
      style={[s.btn, { backgroundColor: bg, opacity: disabled ? 0.5 : 1 }]}>
      <Text style={{ color: kind === 'secondary' ? colors.text : '#fff', fontWeight: '600' }}>{title}</Text>
    </Pressable>
  );
}

export function Field({ label, ...p }: { label: string } & TextInputProps) {
  return (
    <View style={{ gap: 4 }}>
      <Text style={s.label}>{label}</Text>
      <TextInput accessibilityLabel={label} placeholderTextColor="#9aa0a6" autoCapitalize="none" style={s.input} {...p} />
    </View>
  );
}

export const ErrorBanner = ({ message }: { message: string | null }) =>
  message ? <Text accessibilityRole="alert" style={s.errorBanner}>{message}</Text> : null;

export const TestBanner = () => <Text style={s.testBanner}>TEST ODEME: gercek para cekilmez.</Text>;

/** Renders loading / offline / error / empty states, otherwise children(data). */
export function AsyncView<T>({ state, isEmpty, emptyText, children }: {
  state: AsyncState<T>; isEmpty?: (d: T) => boolean; emptyText: string; children: (d: T) => ReactNode;
}) {
  if (state.loading) return <View style={s.center}><ActivityIndicator accessibilityLabel="Yukleniyor" /><Muted>Yukleniyor...</Muted></View>;
  if (state.error) {
    return (
      <View style={s.center}>
        <ErrorBanner message={state.offline ? 'Cevrimdisisiniz. Fiyat ve musaitlik icin internet baglantisi gerekir.' : state.error} />
        <Button title="Tekrar dene" kind="secondary" onPress={state.reload} />
      </View>
    );
  }
  if (state.data == null || (isEmpty && isEmpty(state.data))) return <View style={s.center}><Muted>{emptyText}</Muted></View>;
  return <>{children(state.data)}</>;
}

const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 16, gap: 12 },
  title: { fontSize: 22, fontWeight: '700', color: colors.text },
  muted: { color: colors.muted, fontSize: 13 },
  card: { backgroundColor: colors.card, borderRadius: 12, padding: 14, gap: 6 },
  btn: { borderRadius: 10, paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center' },
  label: { fontSize: 12, color: colors.muted },
  input: { backgroundColor: '#fff', borderRadius: 8, borderWidth: 1, borderColor: '#d0d5dd', paddingHorizontal: 12, paddingVertical: 10, color: colors.text },
  errorBanner: { backgroundColor: colors.errorBg, color: colors.error, padding: 10, borderRadius: 8, overflow: 'hidden' },
  testBanner: { backgroundColor: '#fff3cd', color: '#664d03', padding: 8, borderRadius: 8, fontWeight: '600', overflow: 'hidden' },
  center: { alignItems: 'center', gap: 10, paddingVertical: 24 },
});

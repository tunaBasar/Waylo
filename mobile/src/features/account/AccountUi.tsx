import type { ComponentProps, ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { space, MIN_TOUCH } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { AppHeader } from '@/components/AppHeader';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

export function Icon({ name, size = 22, tint }: { name: IconName; size?: number; tint?: string }) {
  const { color } = useTheme();
  return <MaterialCommunityIcons name={name} size={size} color={tint ?? color.primary} accessibilityElementsHidden importantForAccessibility="no" />;
}

/** Themed card (hairline in light, tonal in dark) with an optional heading. */
export function Section({ title, children }: { title?: string; children: ReactNode }) {
  const s = useStyles(makeStyles);
  return (
    <View style={s.section}>
      {title ? <Text accessibilityRole="header" style={s.sectionTitle}>{title}</Text> : null}
      {children}
    </View>
  );
}

export function Hint({ children }: { children: ReactNode }) {
  const s = useStyles(makeStyles);
  return <Text style={s.hint}>{children}</Text>;
}

export function SuccessNote({ text }: { text: string }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View accessibilityRole="alert" accessibilityLiveRegion="polite" style={s.ok}>
      <Icon name="check-circle-outline" size={18} tint={color.onSuccessContainer} />
      <Text style={[type.bodyMd, { color: color.onSuccessContainer, flex: 1 }]}>{text}</Text>
    </View>
  );
}

export function NavRow({ icon, label, onPress, last }: { icon: IconName; label: string; onPress: () => void; last?: boolean }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={[s.row, !last && s.rowDivider]}>
      <Icon name={icon} />
      <Text style={[type.bodyLg, { color: color.onSurface, flex: 1 }]}>{label}</Text>
      <Icon name="chevron-right" tint={color.outline} />
    </Pressable>
  );
}

export function EmptyNote({ icon, text }: { icon: IconName; text: string }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View style={s.empty}>
      <Icon name={icon} size={32} tint={color.outline} />
      <Text style={[type.bodyMd, { color: color.onSurfaceVariant, textAlign: 'center' }]}>{text}</Text>
    </View>
  );
}

/** Stack-variant header (hides the navigator header) + scrolling body. */
export function StackScreen({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const router = useRouter();
  const s = useStyles(makeStyles);
  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title={title} subtitle={subtitle} onBack={() => (router.canGoBack() ? router.back() : router.replace('/hotels'))} />
      <ScrollView style={s.root} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">{children}</ScrollView>
    </View>
  );
}

/** Stack header with the title + a centered card under the Waylo wordmark. */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const s = useStyles(makeStyles);
  return (
    <StackScreen title={title}>
      <View style={s.shell}>
        <Text accessibilityElementsHidden importantForAccessibility="no" style={s.wordmark}>Waylo</Text>
        <View style={s.section}>{children}</View>
      </View>
    </StackScreen>
  );
}

export function LinkText({ children }: { children: string }) {
  const s = useStyles(makeStyles);
  return <Text style={s.link}>{children}</Text>;
}

/** Themed text styles used by the account screens. */
export function useAccountStyles() {
  return useStyles(({ color, type }: Theme) => StyleSheet.create({
    meta: { ...type.monoSm, color: color.onSurfaceVariant },
    strong: { ...type.titleMd, color: color.onSurface },
    body: { ...type.bodyLg, color: color.onSurface },
  }));
}

const makeStyles = ({ color, type, radius, card }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.md },
  section: { backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor, borderRadius: radius.lg, padding: space.lg, gap: space.md },
  sectionTitle: { ...type.headlineMd, color: color.onSurface },
  hint: { ...type.bodyMd, color: color.onSurfaceVariant },
  ok: { flexDirection: 'row', gap: space.sm, alignItems: 'center', backgroundColor: color.successContainer, borderRadius: radius.md, padding: space.md },
  row: { flexDirection: 'row', alignItems: 'center', gap: space.md, minHeight: MIN_TOUCH + 4, paddingVertical: space.sm },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.outlineVariant },
  empty: { alignItems: 'center', gap: space.sm, padding: space.xxl, backgroundColor: card.backgroundColor, borderRadius: radius.lg, borderWidth: card.borderWidth, borderColor: card.borderColor },
  shell: { gap: space.xl, paddingVertical: space.xl },
  wordmark: { ...type.displayLgMobile, color: color.primary, textAlign: 'center' },
  link: { ...type.labelLg, color: color.primary, textAlign: 'center', paddingVertical: space.md },
});

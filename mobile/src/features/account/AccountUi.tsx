import type { ComponentProps, ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { color, radius, space, type, MIN_TOUCH } from '@/theme/tokens';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

export function Icon({ name, size = 22, tint = color.primary }: { name: IconName; size?: number; tint?: string }) {
  return <MaterialCommunityIcons name={name} size={size} color={tint} accessibilityElementsHidden importantForAccessibility="no" />;
}

/** Hairline-bordered surface, same look as ui.Card but with optional heading. */
export function Section({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <View style={s.section}>
      {title ? <Text accessibilityRole="header" style={s.sectionTitle}>{title}</Text> : null}
      {children}
    </View>
  );
}

export function Hint({ children }: { children: ReactNode }) {
  return <Text style={s.hint}>{children}</Text>;
}

export function SuccessNote({ text }: { text: string }) {
  return (
    <View accessibilityRole="alert" accessibilityLiveRegion="polite" style={s.ok}>
      <Icon name="check-circle-outline" size={18} tint={color.onSecondaryContainer} />
      <Text style={[type.bodyMd, { color: color.onSecondaryContainer, flex: 1 }]}>{text}</Text>
    </View>
  );
}

export function NavRow({ icon, label, onPress, last }: { icon: IconName; label: string; onPress: () => void; last?: boolean }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={[s.row, !last && s.rowDivider]}>
      <Icon name={icon} />
      <Text style={[type.bodyLg, { color: color.onSurface, flex: 1 }]}>{label}</Text>
      <Icon name="chevron-right" tint={color.outline} />
    </Pressable>
  );
}

export function EmptyNote({ icon, text }: { icon: IconName; text: string }) {
  return (
    <View style={s.empty}>
      <Icon name={icon} size={32} tint={color.outline} />
      <Text style={[type.bodyMd, { color: color.onSurfaceVariant, textAlign: 'center' }]}>{text}</Text>
    </View>
  );
}

/** Centered auth card with the Waylo wordmark above. */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={s.shell}>
      <Text accessibilityElementsHidden importantForAccessibility="no" style={s.wordmark}>Waylo</Text>
      <View style={s.section}>
        <Text accessibilityRole="header" style={[type.headlineLg, { color: color.onSurface }]}>{title}</Text>
        {children}
      </View>
    </View>
  );
}

export function LinkText({ children }: { children: string }) {
  return <Text style={s.link}>{children}</Text>;
}

export const accountStyles = StyleSheet.create({
  meta: { ...type.monoSm, color: color.onSurfaceVariant },
  strong: { ...type.titleMd, color: color.onSurface },
  body: { ...type.bodyLg, color: color.onSurface },
});

const s = StyleSheet.create({
  section: { backgroundColor: color.surfaceLowest, borderWidth: StyleSheet.hairlineWidth, borderColor: color.outlineVariant, borderRadius: radius.lg, padding: space.lg, gap: space.md },
  sectionTitle: { ...type.headlineMd, color: color.onSurface },
  hint: { ...type.bodyMd, color: color.onSurfaceVariant },
  ok: { flexDirection: 'row', gap: space.sm, alignItems: 'center', backgroundColor: color.successContainer, borderRadius: radius.md, padding: space.md },
  row: { flexDirection: 'row', alignItems: 'center', gap: space.md, minHeight: MIN_TOUCH + 4, paddingVertical: space.sm },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.outlineVariant },
  empty: { alignItems: 'center', gap: space.sm, padding: space.xxl, backgroundColor: color.surfaceLowest, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, borderColor: color.outlineVariant },
  shell: { gap: space.xl, paddingVertical: space.xxl },
  wordmark: { ...type.displayLgMobile, color: color.primary, textAlign: 'center' },
  link: { ...type.labelLg, color: color.primary, textAlign: 'center', paddingVertical: space.md },
});

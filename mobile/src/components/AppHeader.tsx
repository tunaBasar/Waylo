import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { color, space, type } from '@/theme/tokens';

export function AppHeader({ title, subtitle, onBack, right }: { title: string; subtitle?: string; onBack?: () => void; right?: ReactNode }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[s.bar, { paddingTop: insets.top + space.sm }]}>
      {onBack ? (
        <Pressable accessibilityRole="button" accessibilityLabel="Geri" onPress={onBack} hitSlop={8} style={s.back}>
          <MaterialCommunityIcons name="arrow-left" size={24} color={color.onPrimary} accessibilityElementsHidden importantForAccessibility="no" />
        </Pressable>
      ) : null}
      <View style={s.text}>
        <Text accessibilityRole="header" numberOfLines={1} style={onBack ? s.titleSm : s.title}>{title}</Text>
        {subtitle ? <Text numberOfLines={1} style={s.subtitle}>{subtitle}</Text> : null}
      </View>
      {right ? <View style={s.right}>{right}</View> : null}
    </View>
  );
}

const s = StyleSheet.create({
  bar: { backgroundColor: color.primary, paddingHorizontal: space.lg, paddingBottom: space.md, flexDirection: 'row', alignItems: 'center', gap: space.sm },
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -space.sm },
  text: { flex: 1 },
  title: { ...type.displayLgMobile, color: color.onPrimary },
  titleSm: { ...type.headlineMd, color: color.onPrimary },
  subtitle: { ...type.monoSm, color: color.onPrimaryContainer },
  right: { flexDirection: 'row', alignItems: 'center' },
});

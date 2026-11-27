import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space, MIN_TOUCH } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

/** No colored bar. Tab variant (no onBack): 'Waylo' wordmark + optional right node. Stack variant: round back button, title, mono subtitle. */
export function AppHeader({ title, subtitle, onBack, right }: { title: string; subtitle?: string; onBack?: () => void; right?: ReactNode }) {
  const insets = useSafeAreaInsets();
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View style={[s.bar, { paddingTop: insets.top + space.sm }]}>
      {onBack ? (
        <Pressable accessibilityRole="button" accessibilityLabel="Geri" onPress={onBack} hitSlop={8} style={s.back}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={color.onSurface} accessibilityElementsHidden importantForAccessibility="no" />
        </Pressable>
      ) : null}
      <View style={s.text}>
        {onBack ? (
          <>
            <Text accessibilityRole="header" numberOfLines={1} style={s.titleSm}>{title}</Text>
            {subtitle ? <Text numberOfLines={1} style={s.subtitle}>{subtitle}</Text> : null}
          </>
        ) : (
          <View style={s.brand}>
            <View style={s.dot} />
            <Text accessibilityRole="header" numberOfLines={1} style={s.title}>Waylo</Text>
          </View>
        )}
        {!onBack && subtitle ? <Text numberOfLines={1} style={s.subtitle}>{subtitle}</Text> : null}
      </View>
      {right ? <View style={s.right}>{right}</View> : null}
    </View>
  );
}

const makeStyles = ({ color, type }: Theme) => StyleSheet.create({
  bar: { backgroundColor: color.surface, paddingHorizontal: space.xl, paddingBottom: space.md, flexDirection: 'row', alignItems: 'center', gap: space.md },
  back: { width: MIN_TOUCH, height: MIN_TOUCH, borderRadius: 22, borderWidth: 1, borderColor: color.outlineVariant, alignItems: 'center', justifyContent: 'center' },
  text: { flex: 1 },
  brand: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: color.primary },
  title: { ...type.displayLgMobile, color: color.onSurface },
  titleSm: { ...type.headlineMd, color: color.onSurface },
  subtitle: { ...type.monoSm, color: color.onSurfaceVariant },
  right: { flexDirection: 'row', alignItems: 'center' },
});

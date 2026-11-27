import { Pressable, StyleSheet, Text } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

/** Header login pill shown to guests: outlined in light, tonal in dark. */
export function GuestLoginPill({ onPress }: { onPress: () => void }) {
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel="Giriş yap" onPress={onPress} style={s.pill}>
      <MaterialCommunityIcons name="account-outline" size={16} color={color.onSurface} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={s.text}>Giriş yap</Text>
    </Pressable>
  );
}

const makeStyles = ({ color, type, radius, name }: Theme) => StyleSheet.create({
  pill: {
    minHeight: MIN_TOUCH, paddingHorizontal: space.lg, borderRadius: radius.full, flexDirection: 'row', alignItems: 'center', gap: space.sm,
    backgroundColor: name === 'dark' ? color.surfaceContainer : 'transparent',
    borderWidth: name === 'dark' ? 0 : 1, borderColor: color.outlineVariant,
  },
  text: { ...type.labelLg, color: color.onSurface },
});

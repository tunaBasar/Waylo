import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

export type Mode = 'hotels' | 'flights' | 'cars' | 'bus';
const MODES: { key: Mode; label: string; icon: React.ComponentProps<typeof MaterialCommunityIcons>['name'] }[] = [
  { key: 'hotels', label: 'Otel', icon: 'bed' },
  { key: 'flights', label: 'Uçuş', icon: 'airplane' },
  { key: 'cars', label: 'Araç', icon: 'car' },
  { key: 'bus', label: 'Otobüs', icon: 'bus' },
];

export function ModeTabs({ active, onChange }: { active: Mode; onChange?: (m: Mode) => void }) {
  const router = useRouter();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const go = (m: Mode) => { if (onChange) onChange(m); else if (m !== active) router.replace(`/${m}`); };
  return (
    <View accessibilityRole="tablist" style={s.row}>
      {MODES.map((m) => {
        const on = m.key === active;
        const fg = on ? color.onPrimary : color.onSurfaceVariant;
        return (
          <Pressable key={m.key} accessibilityRole="tab" accessibilityLabel={m.label} accessibilityState={{ selected: on }} onPress={() => go(m.key)}
            style={[s.tile, on && s.tileOn]}>
            <MaterialCommunityIcons name={m.icon} size={24} color={fg} accessibilityElementsHidden importantForAccessibility="no" />
            <Text style={[type.labelSm, { color: fg }]}>{m.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const makeStyles = ({ color, radius, card }: Theme) => StyleSheet.create({
  row: { flexDirection: 'row', gap: space.sm, backgroundColor: color.surface, paddingHorizontal: space.xl, paddingVertical: space.sm },
  tile: { flex: 1, aspectRatio: 1, maxHeight: 84, minHeight: 64, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', gap: space.xs, backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor },
  tileOn: { backgroundColor: color.primary, borderColor: color.primary },
});

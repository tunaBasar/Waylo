import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { color, radius, space, type } from '@/theme/tokens';

export type Mode = 'hotels' | 'flights' | 'cars' | 'bus';
const MODES: { key: Mode; label: string; icon: React.ComponentProps<typeof MaterialCommunityIcons>['name'] }[] = [
  { key: 'hotels', label: 'Otel', icon: 'bed' },
  { key: 'flights', label: 'Uçuş', icon: 'airplane' },
  { key: 'cars', label: 'Araç', icon: 'car' },
  { key: 'bus', label: 'Otobüs', icon: 'bus' },
];

export function ModeTabs({ active, onChange }: { active: Mode; onChange?: (m: Mode) => void }) {
  const router = useRouter();
  const go = (m: Mode) => { if (onChange) onChange(m); else if (m !== active) router.replace(`/${m}`); };
  return (
    <View accessibilityRole="tablist" style={s.row}>
      {MODES.map((m) => {
        const on = m.key === active;
        return (
          <Pressable key={m.key} accessibilityRole="tab" accessibilityLabel={m.label} accessibilityState={{ selected: on }} onPress={() => go(m.key)}
            style={[s.pill, on && s.pillOn]}>
            <MaterialCommunityIcons name={m.icon} size={20} color={on ? color.onPrimary : color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
            <Text style={[type.labelSm, { color: on ? color.onPrimary : color.onSurfaceVariant }]}>{m.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const s = StyleSheet.create({
  row: { flexDirection: 'row', gap: space.xs, backgroundColor: color.surfaceLowest, paddingHorizontal: space.lg, paddingVertical: space.sm, borderBottomWidth: 1, borderBottomColor: color.outlineVariant },
  pill: { flex: 1, minHeight: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: space.xs, backgroundColor: color.surfaceLow },
  pillOn: { backgroundColor: color.primary },
});

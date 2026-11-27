import { Pressable, Text, View } from 'react-native';
import { space } from '@/theme/tokens';
import { useTheme } from '@/theme/ThemeContext';

export function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (n: number) => void }) {
  const { color, type, radius } = useTheme();
  const btn = (t: string, d: number, ok: boolean) => (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label} ${d > 0 ? 'artır' : 'azalt'}`} disabled={!ok} onPress={() => onChange(value + d)}
      style={{ width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surfaceContainer, alignItems: 'center', justifyContent: 'center', opacity: ok ? 1 : 0.4 }}>
      <Text style={[type.titleMd, { color: color.primary, fontSize: 20 }]}>{t}</Text>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text style={[type.bodyLg, { color: color.onSurface }]}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.md }}>
        {btn('-', -1, value > min)}
        <Text accessibilityLabel={`${label} sayısı`} style={[type.monoMd, { minWidth: 24, textAlign: 'center', color: color.onSurface }]}>{value}</Text>
        {btn('+', 1, value < max)}
      </View>
    </View>
  );
}

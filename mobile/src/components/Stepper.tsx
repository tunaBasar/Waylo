import { Pressable, Text, View } from 'react-native';
import { colors } from '@/components/ui';

export function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (n: number) => void }) {
  const btn = (t: string, d: number, ok: boolean) => (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label} ${d > 0 ? 'artir' : 'azalt'}`} disabled={!ok} onPress={() => onChange(value + d)}
      style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: '#e3e6ec', alignItems: 'center', justifyContent: 'center', opacity: ok ? 1 : 0.4 }}>
      <Text style={{ fontSize: 18 }}>{t}</Text>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text style={{ color: colors.text }}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        {btn('-', -1, value > min)}
        <Text accessibilityLabel={`${label} sayisi`} style={{ minWidth: 20, textAlign: 'center', fontWeight: '700' }}>{value}</Text>
        {btn('+', 1, value < max)}
      </View>
    </View>
  );
}

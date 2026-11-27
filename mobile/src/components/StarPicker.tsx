import { Pressable, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space } from '@/theme/tokens';
import { useTheme } from '@/theme/ThemeContext';

export function StarPicker({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const { color } = useTheme();
  return (
    <View style={{ flexDirection: 'row', gap: space.xs }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} accessibilityRole="button" accessibilityLabel={`${n} puan`} accessibilityState={{ selected: n <= value }} onPress={() => onChange(n)}
          style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}>
          <MaterialCommunityIcons name={n <= value ? 'star' : 'star-outline'} size={30} color={n <= value ? color.warning : color.outline} accessibilityElementsHidden importantForAccessibility="no" />
        </Pressable>
      ))}
    </View>
  );
}

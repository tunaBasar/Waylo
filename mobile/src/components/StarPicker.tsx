import { Pressable, Text, View } from 'react-native';

export function StarPicker({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return (
    <View style={{ flexDirection: 'row', gap: 8 }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} accessibilityRole="button" accessibilityLabel={`${n} puan`} onPress={() => onChange(n)}>
          <Text style={{ fontSize: 28, color: n <= value ? '#f5a623' : '#c5c9d0' }}>★</Text>
        </Pressable>
      ))}
    </View>
  );
}

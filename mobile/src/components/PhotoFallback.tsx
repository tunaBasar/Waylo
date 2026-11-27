import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { color, space, type } from '@/theme/tokens';

export function PhotoFallback({ style }: { style?: StyleProp<ViewStyle> }) {
  return (
    <View accessible accessibilityLabel="Fotoğraf yok" style={[s.box, style]}>
      <MaterialCommunityIcons name="image-off-outline" size={28} color={color.outline} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>Fotoğraf yok</Text>
    </View>
  );
}

const s = StyleSheet.create({
  box: { backgroundColor: color.surfaceContainer, alignItems: 'center', justifyContent: 'center', gap: space.xs, minHeight: 96 },
});

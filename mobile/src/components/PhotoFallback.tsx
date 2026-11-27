import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

export function PhotoFallback({ style }: { style?: StyleProp<ViewStyle> }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View accessible accessibilityLabel="Kapak fotoğrafı yok" style={[s.box, style]}>
      <MaterialCommunityIcons name="image-off-outline" size={28} color={color.outline} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>Kapak fotoğrafı yok</Text>
    </View>
  );
}

const makeStyles = ({ color }: Theme) => StyleSheet.create({
  box: { backgroundColor: color.surfaceContainer, alignItems: 'center', justifyContent: 'center', gap: space.xs, minHeight: 96 },
});

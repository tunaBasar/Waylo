import { StyleSheet, View } from 'react-native';
import { color, radius, space } from '@/theme/tokens';

/** Placeholder shown only while a request is really running. */
export function SkeletonCard() {
  return (
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={s.card}>
      <View style={s.cover} />
      <View style={{ flex: 1, gap: space.sm }}>
        <View style={[s.line, { width: '70%' }]} />
        <View style={[s.line, { width: '45%' }]} />
        <View style={[s.line, { width: '30%' }]} />
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  card: { flexDirection: 'row', gap: space.md, padding: space.md, backgroundColor: color.surfaceLowest, borderRadius: radius.lg, borderWidth: 1, borderColor: color.outlineVariant },
  cover: { width: 96, height: 96, borderRadius: radius.md, backgroundColor: color.surfaceContainer },
  line: { height: 12, borderRadius: radius.sm, backgroundColor: color.surfaceContainer },
});

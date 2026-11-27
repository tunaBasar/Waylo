import { StyleSheet, View } from 'react-native';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles } from '@/theme/ThemeContext';

/** Placeholder shown only while a request is really running. */
export function SkeletonCard() {
  const s = useStyles(makeStyles);
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

const makeStyles = ({ color, radius, card }: Theme) => StyleSheet.create({
  card: { flexDirection: 'row', gap: space.md, padding: space.md, backgroundColor: card.backgroundColor, borderRadius: radius.lg, borderWidth: card.borderWidth, borderColor: card.borderColor },
  cover: { width: 96, height: 96, borderRadius: radius.md, backgroundColor: color.surfaceContainer },
  line: { height: 12, borderRadius: radius.sm, backgroundColor: color.surfaceContainer },
});

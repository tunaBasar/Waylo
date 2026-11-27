import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { color, radius, space, type } from '@/theme/tokens';

export type Tone = 'success' | 'warning' | 'error' | 'neutral';
const TONES: Record<Tone, { bg: string; fg: string; icon: React.ComponentProps<typeof MaterialCommunityIcons>['name'] }> = {
  success: { bg: color.successContainer, fg: color.onSecondaryContainer, icon: 'check-circle-outline' },
  warning: { bg: color.warningContainer, fg: color.onWarningContainer, icon: 'clock-outline' },
  error: { bg: color.errorContainer, fg: color.onErrorContainer, icon: 'alert-circle-outline' },
  neutral: { bg: color.surfaceContainer, fg: color.onSurface, icon: 'information-outline' },
};

export function StatusPill({ tone = 'neutral', text }: { tone?: Tone; text: string }) {
  const t = TONES[tone];
  return (
    <View accessible accessibilityLabel={text} style={[s.pill, { backgroundColor: t.bg }]}>
      <MaterialCommunityIcons name={t.icon} size={14} color={t.fg} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelSm, { color: t.fg }]}>{text}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  pill: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', gap: space.xs, paddingHorizontal: space.sm, paddingVertical: space.xs, borderRadius: radius.full },
});

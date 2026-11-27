import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

export type Tone = 'success' | 'warning' | 'error' | 'neutral';
const ICONS: Record<Tone, React.ComponentProps<typeof MaterialCommunityIcons>['name']> = {
  success: 'check-circle-outline', warning: 'clock-outline', error: 'alert-circle-outline', neutral: 'information-outline',
};

export function StatusPill({ tone = 'neutral', text }: { tone?: Tone; text: string }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const tones: Record<Tone, { bg: string; fg: string }> = {
    success: { bg: color.successContainer, fg: color.onSuccessContainer },
    warning: { bg: color.warningContainer, fg: color.onWarningContainer },
    error: { bg: color.errorContainer, fg: color.onErrorContainer },
    neutral: { bg: color.surfaceContainer, fg: color.onSurface },
  };
  const t = tones[tone];
  return (
    <View accessible accessibilityLabel={text} style={[s.pill, { backgroundColor: t.bg }]}>
      <MaterialCommunityIcons name={ICONS[tone]} size={14} color={t.fg} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelSm, { color: t.fg }]}>{text}</Text>
    </View>
  );
}

const makeStyles = ({ radius }: Theme) => StyleSheet.create({
  pill: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', gap: space.xs, paddingHorizontal: space.sm, paddingVertical: space.xs, borderRadius: radius.full },
});

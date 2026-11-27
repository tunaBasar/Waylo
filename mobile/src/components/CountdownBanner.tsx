import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { formatCountdown } from '@/components/countdown';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

/** Live mm:ss until an ISO instant, ticking every second. */
export function CountdownBanner({ until, label }: { until: string; label: string }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const left = formatCountdown(until, now);
  const expired = left == null;
  const bg = expired ? color.errorContainer : color.warningContainer;
  const fg = expired ? color.onErrorContainer : color.onWarningContainer;
  return (
    <View accessible accessibilityRole="timer" accessibilityLabel={expired ? `${label}: Süre doldu` : `${label}: ${left}`} style={[s.box, { backgroundColor: bg }]}>
      <MaterialCommunityIcons name={expired ? 'timer-off-outline' : 'timer-outline'} size={20} color={fg} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelLg, { color: fg, flex: 1 }]}>{label}</Text>
      <Text style={[type.monoMd, { color: fg }]}>{expired ? 'Süre doldu' : left}</Text>
    </View>
  );
}

const makeStyles = ({ radius }: Theme) => StyleSheet.create({
  box: { flexDirection: 'row', alignItems: 'center', gap: space.sm, padding: space.md, borderRadius: radius.md },
});

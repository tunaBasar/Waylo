import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '@/components/ui';
import { Price } from '@/components/Price';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

/** Content height of the bar without the safe-area inset: screens add this (plus insets) as bottom padding. */
export const STICKY_ACTION_BAR_HEIGHT = 76;

export function StickyActionBar({ totalLabel = 'Toplam', amount, currency, actionLabel, onAction, disabled }: {
  totalLabel?: string; amount: number; currency: string; actionLabel: string; onAction: () => void; disabled?: boolean;
}) {
  const insets = useSafeAreaInsets();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  return (
    <View style={[s.bar, { paddingBottom: space.sm + insets.bottom, minHeight: STICKY_ACTION_BAR_HEIGHT + insets.bottom }]}>
      <View style={{ flex: 1 }}>
        <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>{totalLabel}</Text>
        <Price amount={amount} currency={currency} />
      </View>
      <Button title={actionLabel} onPress={onAction} disabled={disabled} />
    </View>
  );
}

const makeStyles = ({ color, name }: Theme) => StyleSheet.create({
  bar: {
    flexDirection: 'row', alignItems: 'center', gap: space.md, paddingHorizontal: space.xl, paddingTop: space.sm,
    backgroundColor: name === 'light' ? color.surfaceLowest : color.surfaceContainer,
    borderTopWidth: name === 'light' ? 1 : 0, borderTopColor: color.outlineVariant,
  },
});

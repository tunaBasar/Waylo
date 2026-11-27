import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '@/components/ui';
import { Price } from '@/components/Price';
import { color, space, type } from '@/theme/tokens';

/** Content height of the bar without the safe-area inset: screens add this (plus insets) as bottom padding. */
export const STICKY_ACTION_BAR_HEIGHT = 76;

export function StickyActionBar({ totalLabel = 'Toplam', amount, currency, actionLabel, onAction, disabled }: {
  totalLabel?: string; amount: number; currency: string; actionLabel: string; onAction: () => void; disabled?: boolean;
}) {
  const insets = useSafeAreaInsets();
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

const s = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', gap: space.md, paddingHorizontal: space.lg, paddingTop: space.sm, backgroundColor: color.surfaceLowest, borderTopWidth: 1, borderTopColor: color.outlineVariant },
});

import { Text, type StyleProp, type TextStyle } from 'react-native';
import { money } from '@/features/format';
import { color, type } from '@/theme/tokens';

export function Price({ amount, currency, size = 'md', style }: { amount: number; currency: string; size?: 'sm' | 'md'; style?: StyleProp<TextStyle> }) {
  return <Text style={[size === 'sm' ? type.monoSm : type.monoMd, { color: color.onSurface }, style]}>{money(amount, currency)}</Text>;
}

import { createElement, useState } from 'react';
import { Platform, Pressable, Text, View, StyleSheet } from 'react-native';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { fmtDate, fmtTime, isoDate } from '@/features/dates';

const p2 = (n: number) => String(n).padStart(2, '0');

/**
 * Date or date+time picker. Native: system picker. Web: the browser's native <input type=date|datetime-local>
 * (the community picker has no web implementation).
 */
export function DateField({ label, value, onChange, mode = 'date', minimumDate }: {
  label: string; value: Date; onChange: (d: Date) => void; mode?: 'date' | 'datetime'; minimumDate?: Date;
}) {
  const { color, type, radius } = useTheme();
  const s = useStyles(makeStyles);
  const [show, setShow] = useState<null | 'date' | 'time'>(null);

  if (Platform.OS === 'web') {
    const v = mode === 'date' ? isoDate(value) : `${isoDate(value)}T${p2(value.getHours())}:${p2(value.getMinutes())}`;
    return (
      <View style={{ gap: space.xs }}>
        <Text style={s.label}>{label}</Text>
        {createElement('input', {
          type: mode === 'date' ? 'date' : 'datetime-local', value: v, 'aria-label': label,
          min: minimumDate ? isoDate(minimumDate) : undefined,
          style: { padding: 10, minHeight: 44, boxSizing: 'border-box', borderRadius: radius.md, border: `1px solid ${color.outlineVariant}`, fontSize: 15, color: color.onSurface, backgroundColor: color.surfaceLowest },
          onChange: (e: { target: { value: string } }) => {
            const t = e.target.value;
            if (!t) return;
            const [dp, tp] = t.split('T');
            const [y, m, d] = dp.split('-').map(Number);
            const [hh, mm] = (tp ?? '00:00').split(':').map(Number);
            onChange(new Date(y, m - 1, d, hh, mm));
          },
        })}
      </View>
    );
  }

  const handle = (kind: 'date' | 'time') => (e: DateTimePickerEvent, picked?: Date) => {
    setShow(null);
    if (e.type !== 'set' || !picked) return;
    const next = new Date(value);
    if (kind === 'date') next.setFullYear(picked.getFullYear(), picked.getMonth(), picked.getDate());
    else next.setHours(picked.getHours(), picked.getMinutes(), 0, 0);
    onChange(next);
    if (kind === 'date' && mode === 'datetime') setTimeout(() => setShow('time'), 0);
  };

  return (
    <View style={{ gap: space.xs }}>
      <Text style={s.label}>{label}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={() => setShow('date')} style={s.input}>
        <Text style={[type.bodyLg, { color: color.onSurface }]}>{mode === 'date' ? fmtDate(value) : `${fmtDate(value)} ${fmtTime(value)}`}</Text>
      </Pressable>
      {show && (
        <DateTimePicker value={value} mode={show} minimumDate={minimumDate} is24Hour onChange={handle(show)} />
      )}
    </View>
  );
}

const makeStyles = ({ color, type, radius }: Theme) => StyleSheet.create({
  label: { ...type.labelSm, color: color.onSurfaceVariant },
  input: { minHeight: 48, justifyContent: 'center', backgroundColor: color.surfaceLowest, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant, paddingHorizontal: space.md, paddingVertical: space.sm },
});

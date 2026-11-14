import { createElement, useState } from 'react';
import { Platform, Pressable, Text, View, StyleSheet } from 'react-native';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { colors } from '@/components/ui';
import { fmtDate, fmtTime, isoDate } from '@/features/dates';

const p2 = (n: number) => String(n).padStart(2, '0');

/**
 * Date or date+time picker. Native: system picker. Web: the browser's native <input type=date|datetime-local>
 * (the community picker has no web implementation).
 */
export function DateField({ label, value, onChange, mode = 'date', minimumDate }: {
  label: string; value: Date; onChange: (d: Date) => void; mode?: 'date' | 'datetime'; minimumDate?: Date;
}) {
  const [show, setShow] = useState<null | 'date' | 'time'>(null);

  if (Platform.OS === 'web') {
    const v = mode === 'date' ? isoDate(value) : `${isoDate(value)}T${p2(value.getHours())}:${p2(value.getMinutes())}`;
    return (
      <View style={{ gap: 4 }}>
        <Text style={s.label}>{label}</Text>
        {createElement('input', {
          type: mode === 'date' ? 'date' : 'datetime-local', value: v, 'aria-label': label,
          min: minimumDate ? isoDate(minimumDate) : undefined,
          style: { padding: 10, borderRadius: 8, border: '1px solid #d0d5dd', fontSize: 14 },
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
    <View style={{ gap: 4 }}>
      <Text style={s.label}>{label}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={() => setShow('date')} style={s.input}>
        <Text>{mode === 'date' ? fmtDate(value) : `${fmtDate(value)} ${fmtTime(value)}`}</Text>
      </Pressable>
      {show && (
        <DateTimePicker value={value} mode={show} minimumDate={minimumDate} is24Hour onChange={handle(show)} />
      )}
    </View>
  );
}

const s = StyleSheet.create({
  label: { fontSize: 12, color: colors.muted },
  input: { backgroundColor: '#fff', borderRadius: 8, borderWidth: 1, borderColor: '#d0d5dd', paddingHorizontal: 12, paddingVertical: 12 },
});

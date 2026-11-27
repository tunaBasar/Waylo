import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Tabs } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { barItemForRoute, isSearchRoute, type BarItem, type SearchRoute } from '@/components/bottomBarMap';
import { space } from '@/theme/tokens';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

type TabBarFn = NonNullable<React.ComponentProps<typeof Tabs>['tabBar']>;
export type BottomTabBarProps = Parameters<TabBarFn>[0];
type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];
const ITEMS: { key: BarItem; label: string; icon: IconName; iconOn: IconName }[] = [
  { key: 'search', label: 'Arama', icon: 'magnify', iconOn: 'magnify' },
  { key: 'cart', label: 'Sepet', icon: 'cart-outline', iconOn: 'cart' },
  { key: 'orders', label: 'Siparişler', icon: 'receipt-text-outline', iconOn: 'receipt-text' },
  { key: 'profile', label: 'Hesap', icon: 'account-outline', iconOn: 'account' },
];

/** Custom tab bar: four items over the six tab routes. Cart is a stack screen, so the parent supplies onCart. */
export function BottomBar({ state, emitter, navigateToTab, cartCount, onCart }: BottomTabBarProps & { cartCount: number; onCart: () => void }) {
  const insets = useSafeAreaInsets();
  const { color, type, floatingBottomBar } = useTheme();
  const s = useStyles(makeStyles);
  const [lastSearch, setLastSearch] = useState<SearchRoute>('hotels');
  const current = state.routes[state.index]?.name ?? '';
  const [seen, setSeen] = useState(current);
  // remember the last used search mode (derived during render, no effect)
  if (seen !== current) {
    setSeen(current);
    if (isSearchRoute(current)) setLastSearch(current);
  }
  const active = barItemForRoute(current);

  const press = (key: BarItem) => {
    if (key === 'cart') { onCart(); return; }
    const target = key === 'search' ? (isSearchRoute(current) ? current : lastSearch) : key;
    const route = state.routes.find((r) => r.name === target);
    if (!route) return;
    const e = emitter.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
    if (!e.defaultPrevented) navigateToTab(route.key);
  };

  return (
    <View style={[s.bar, floatingBottomBar ? { marginBottom: 12 + insets.bottom, paddingBottom: space.xs } : { paddingBottom: space.xs + insets.bottom }]}>
      {ITEMS.map((it) => {
        const on = active === it.key;
        const badge = it.key === 'cart' && cartCount > 0;
        const label = badge ? `${it.label}, ${cartCount} ürün` : it.label;
        return (
          <Pressable key={it.key} accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ selected: on }} onPress={() => press(it.key)} style={s.item}>
            <View style={[s.iconWrap, on && s.iconWrapOn]}>
              <MaterialCommunityIcons name={on ? it.iconOn : it.icon} size={22} color={on ? color.primary : color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
              {badge ? <View style={s.badge}><Text style={s.badgeText}>{cartCount > 99 ? '99+' : cartCount}</Text></View> : null}
            </View>
            <Text style={[type.labelSm, { color: on ? color.primary : color.onSurfaceVariant }]}>{it.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const makeStyles = ({ color, type, radius, floatingBottomBar }: Theme) => StyleSheet.create({
  bar: floatingBottomBar
    ? { flexDirection: 'row', backgroundColor: color.surfaceContainer, borderRadius: radius.xl, marginHorizontal: 12, paddingTop: space.xs }
    : { flexDirection: 'row', backgroundColor: color.surfaceLowest, borderTopWidth: 1, borderTopColor: color.outlineVariant, paddingTop: space.xs },
  item: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', gap: 2 },
  iconWrap: { width: 56, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  iconWrapOn: { backgroundColor: floatingBottomBar ? color.primaryContainer : color.surfaceContainer },
  badge: { position: 'absolute', top: -2, right: 6, minWidth: 16, height: 16, borderRadius: 8, paddingHorizontal: 3, backgroundColor: color.error, alignItems: 'center', justifyContent: 'center' },
  badgeText: { ...type.labelSm, fontSize: 10, lineHeight: 12, color: color.onError },
});

import { Tabs, useRouter } from 'expo-router';
import { Pressable, Text } from 'react-native';
import { useCart } from '@/features/cart/CartContext';

export default function TabsLayout() {
  const router = useRouter();
  const { items } = useCart();
  const cartButton = () => (
    <Pressable accessibilityRole="button" accessibilityLabel="Sepet" onPress={() => router.push('/cart')} style={{ paddingHorizontal: 14 }}>
      <Text>{`Sepet${items.length ? ` (${items.length})` : ''}`}</Text>
    </Pressable>
  );
  return (
    <Tabs screenOptions={{ headerRight: cartButton }}>
      <Tabs.Screen name="hotels" options={{ title: 'Oteller' }} />
      <Tabs.Screen name="flights" options={{ title: 'Ucuslar' }} />
      <Tabs.Screen name="cars" options={{ title: 'Arac' }} />
      <Tabs.Screen name="bus" options={{ title: 'Otobus' }} />
      <Tabs.Screen name="orders" options={{ title: 'Siparisler' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profil' }} />
    </Tabs>
  );
}

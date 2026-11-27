import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { Platform, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { space } from '@/theme/tokens';
import { useTheme } from '@/theme/ThemeContext';
import NetInfo from '@react-native-community/netinfo';

const Ctx = createContext(true);

/** Online unless NetInfo explicitly reports no connection (unknown/null counts as online). */
export function NetworkProvider({ children }: { children: ReactNode }) {
  const [online, setOnline] = useState(true);
  useEffect(() => NetInfo.addEventListener((s) => setOnline(s.isConnected !== false && s.isInternetReachable !== false)), []);
  // NetInfo's web module listens to navigator.connection 'change' when it exists (Chromium), which does not
  // fire for online/offline transitions, so also follow the window events on web.
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') return;
    const sync = () => setOnline(navigator.onLine);
    window.addEventListener('online', sync);
    window.addEventListener('offline', sync);
    return () => { window.removeEventListener('online', sync); window.removeEventListener('offline', sync); };
  }, []);
  return <Ctx.Provider value={online}>{children}</Ctx.Provider>;
}

export const useOnline = () => useContext(Ctx);

export function OfflineBanner() {
  const online = useOnline();
  const { color, type } = useTheme();
  if (online) return null;
  return (
    <View accessibilityRole="alert" accessibilityLiveRegion="assertive" testID="offline-banner"
      style={{ backgroundColor: color.warningContainer, flexDirection: 'row', alignItems: 'center', gap: space.sm, padding: space.sm, paddingHorizontal: space.lg }}>
      <MaterialCommunityIcons name="wifi-off" size={18} color={color.onWarningContainer} accessibilityElementsHidden importantForAccessibility="no" />
      <Text style={[type.labelLg, { color: color.onWarningContainer, flex: 1 }]}>
        Çevrimdışısınız. Ödeme ve sipariş işlemleri bağlantı gelince yapılabilir.
      </Text>
    </View>
  );
}

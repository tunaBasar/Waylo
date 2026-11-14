import { Platform } from 'react-native';

/**
 * Backend base URL (API gateway). Override with EXPO_PUBLIC_API_URL.
 * Default: Android emulator reaches the host via 10.0.2.2, everything else uses localhost.
 */
export const API_URL: string =
  process.env.EXPO_PUBLIC_API_URL || (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');

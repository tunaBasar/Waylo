import { Redirect } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';

export default function Index() {
  const { user } = useAuth();
  return <Redirect href={user ? '/(tabs)/hotels' : '/(auth)/login'} />;
}

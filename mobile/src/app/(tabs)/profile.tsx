import { Text } from 'react-native';
import { useAuth } from '@/features/auth/AuthContext';
import { Button, Card, Muted, Screen, Title } from '@/components/ui';

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  return (
    <Screen>
      <Title>Profil</Title>
      {user ? (
        <Card>
          <Text style={{ fontWeight: '700' }}>{`${user.firstName} ${user.lastName}`}</Text>
          <Muted>{user.email}</Muted>
          <Muted>{user.emailVerified ? 'E-posta dogrulandi' : 'E-posta dogrulanmadi'}</Muted>
        </Card>
      ) : null}
      <Button kind="danger" title="Cikis yap" onPress={() => { void logout(); }} />
    </Screen>
  );
}

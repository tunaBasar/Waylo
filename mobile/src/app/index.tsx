import { Redirect } from 'expo-router';

/** Guests browse the catalog tabs, account pages ask for login. */
export default function Index() {
  return <Redirect href="/(tabs)/hotels" />;
}

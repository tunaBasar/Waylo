import { fireEvent, render, screen } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Settings from '../src/app/settings';
import { ThemeProvider } from '../src/theme/ThemeContext';

jest.mock('expo-secure-store', () => ({}));
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  Stack: { Screen: () => null },
}));
jest.mock('../src/features/auth/AuthContext', () => ({
  useAuth: () => ({ clearSession: jest.fn(), logout: jest.fn() }),
}));

describe('Settings appearance', () => {
  it('saves the dark preference and checks the Koyu radio', async () => {
    await render(<ThemeProvider><Settings /></ThemeProvider>);
    await fireEvent.press(screen.getByLabelText('Koyu'));
    expect(AsyncStorage.setItem).toHaveBeenCalledWith('waylo_theme', 'dark');
    expect(screen.getByLabelText('Koyu').props.accessibilityState).toMatchObject({ checked: true });
  });
});

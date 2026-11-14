import { render, screen, waitFor } from '@testing-library/react-native';
import Orders from '../src/app/(tabs)/orders';
import { ordersApi } from '../src/features/orders/api';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({}));

describe('Orders screen', () => {
  afterEach(() => jest.restoreAllMocks());

  it('shows the empty state when the API returns no orders', async () => {
    jest.spyOn(ordersApi, 'list').mockResolvedValue([]);
    await render(<Orders />);
    await waitFor(() => expect(screen.getByText('Henuz siparisiniz yok.')).toBeTruthy());
  });

  it('renders orders from the API, status from backend', async () => {
    jest.spyOn(ordersApi, 'list').mockResolvedValue([
      { id: 'abcdef1234', status: 'CONFIRMED', currency: 'TRY', totalAmount: 100, createdAt: '2026-01-01T00:00:00Z' },
    ]);
    await render(<Orders />);
    await waitFor(() => expect(screen.getByText('Onaylandi')).toBeTruthy());
    expect(screen.getByText('#abcdef12')).toBeTruthy();
  });

  it('shows an error with retry when offline', async () => {
    const { ApiError } = require('../src/api/client');
    jest.spyOn(ordersApi, 'list').mockRejectedValue(new ApiError('x', 0, 'NETWORK'));
    await render(<Orders />);
    await waitFor(() => expect(screen.getByText(/Cevrimdisisiniz/)).toBeTruthy());
    expect(screen.getByLabelText('Tekrar dene')).toBeTruthy();
  });
});

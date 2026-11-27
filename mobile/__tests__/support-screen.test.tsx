import { render, screen, waitFor } from '@testing-library/react-native';
import SupportList from '../src/app/support/index';
import { supportApi } from '../src/features/support/api';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }), Stack: { Screen: () => null } }));
jest.mock('expo-secure-store', () => ({}));

describe('Support list screen', () => {
  afterEach(() => jest.restoreAllMocks());
  const caps = { liveChat: false, channels: [], hours: null, decisionPending: true };

  it('shows the empty state', async () => {
    jest.spyOn(supportApi, 'capabilities').mockResolvedValue(caps);
    jest.spyOn(supportApi, 'myList').mockResolvedValue([]);
    await render(<SupportList />);
    await waitFor(() => expect(screen.getByText(/Henüz destek talebiniz yok/)).toBeTruthy());
    await waitFor(() => expect(screen.getByText(/henüz belirlenmedi/)).toBeTruthy());
  });

  it('renders a case from the API', async () => {
    jest.spyOn(supportApi, 'capabilities').mockResolvedValue(caps);
    jest.spyOn(supportApi, 'myList').mockResolvedValue([
      { id: 'c1', customerId: 'u', orderId: 'o', orderItemId: null, supplierOrganizationId: null, subject: 'Oda sorunu', status: 'ANSWERED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z', messages: null },
    ]);
    await render(<SupportList />);
    await waitFor(() => expect(screen.getByText('Oda sorunu')).toBeTruthy());
    expect(screen.getByText('Yanıtlandı')).toBeTruthy();
  });
});

import { scoreText } from '../src/features/reviews/api';

jest.mock('expo-secure-store', () => ({}));

describe('scoreText', () => {
  it('formats with a Turkish comma', () => {
    expect(scoreText(4.6, 328)).toBe('4,6 / 5 (328 değerlendirme)');
    expect(scoreText(5, 1)).toBe('5,0 / 5 (1 değerlendirme)');
  });
  it('returns null without reviews or average', () => {
    expect(scoreText(4.6, 0)).toBeNull();
    expect(scoreText(null, 3)).toBeNull();
    expect(scoreText(undefined, undefined)).toBeNull();
  });
});

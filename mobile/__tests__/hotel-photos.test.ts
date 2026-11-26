import { hotelCoverUrl, photoAspect, photoUrl } from '@/features/hotels/api';

describe('photoUrl', () => {
  it('builds the media url', () => {
    expect(photoUrl('http://h:8080', 'a', 'b')).toBe('http://h:8080/api/hotels/a/media/b');
  });
  it('encodes ids and trims trailing slashes', () => {
    expect(photoUrl('http://h/', 'a/b?', 'c d#')).toBe('http://h/api/hotels/a%2Fb%3F/media/c%20d%23');
  });
});

describe('photoAspect', () => {
  it('uses width/height', () => expect(photoAspect(200, 100)).toBe(2));
  it('falls back to 4:3', () => {
    expect(photoAspect(0, 100)).toBeCloseTo(4 / 3);
    expect(photoAspect(100, 0)).toBeCloseTo(4 / 3);
  });
});

describe('hotelCoverUrl', () => {
  it('builds the cover url from coverMediaId', () => {
    expect(hotelCoverUrl('http://h:8080', { id: 'a', coverMediaId: 'b' })).toBe('http://h:8080/api/hotels/a/media/b');
  });
  it('is null without a cover', () => {
    expect(hotelCoverUrl('http://h', { id: 'a' })).toBeNull();
    expect(hotelCoverUrl('http://h', { id: 'a', coverMediaId: null })).toBeNull();
  });
});

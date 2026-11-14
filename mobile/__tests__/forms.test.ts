import { validateReview } from '@/features/reviews/api';
import { validateName, validatePasswordChange } from '@/features/profile/api';

test('review validation', () => {
  expect(validateReview(0, '')).not.toBeNull();
  expect(validateReview(6, '')).not.toBeNull();
  expect(validateReview(4.5, '')).not.toBeNull();
  expect(validateReview(5, 'ok')).toBeNull();
  expect(validateReview(3, 'x'.repeat(2001))).not.toBeNull();
});
test('name validation', () => {
  expect(validateName(' ', 'a')).not.toBeNull();
  expect(validateName('a', 'b')).toBeNull();
});
test('password change validation', () => {
  expect(validatePasswordChange('old', 'short1', 'short1')).not.toBeNull();
  expect(validatePasswordChange('oldpassword1', 'newpassword1', 'different12')).not.toBeNull();
  expect(validatePasswordChange('oldpassword1', 'oldpassword1', 'oldpassword1')).not.toBeNull();
  expect(validatePasswordChange('oldpassword1', 'newpassword1', 'newpassword1')).toBeNull();
});

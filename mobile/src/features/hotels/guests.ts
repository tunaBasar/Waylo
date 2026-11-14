export const MAX_ROOMS = 5;
export const MAX_ADULTS_PER_ROOM = 4;
export const MAX_CHILDREN = 6;
export const MAX_CHILD_AGE = 17;

export function clampInt(v: number, min: number, max: number) { return Math.min(max, Math.max(min, Math.trunc(v) || min)); }

/** Resize children ages list, new children default to age 5. */
export function resizeChildren(ages: number[], count: number): number[] {
  const n = clampInt(count, 0, MAX_CHILDREN);
  return n <= ages.length ? ages.slice(0, n) : [...ages, ...Array(n - ages.length).fill(5)];
}

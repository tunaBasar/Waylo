import { useState } from 'react';

export type SingleFlight = <T>(key: string, fn: () => Promise<T>) => Promise<T | undefined>;

/** Framework-free single-flight guard: a key that is already running is skipped (returns undefined). */
export function createSingleFlight(): SingleFlight {
  const running = new Set<string>();
  return async function run<T>(key: string, fn: () => Promise<T>): Promise<T | undefined> {
    if (running.has(key)) return undefined;
    running.add(key);
    try {
      return await fn();
    } finally {
      running.delete(key);
    }
  };
}

/** React hook: state updates are async, so a stable guard object stops a second synchronous click. */
export function useSingleFlight(): SingleFlight {
  return useState(createSingleFlight)[0];
}

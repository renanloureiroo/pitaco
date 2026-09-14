import type { Clock, Instant } from '../../core/clock';

export interface FakeClock extends Clock {
  advance(ms: number): void;
  set(mono: number, wall?: number): void;
}

// Relógio controlado à mão, para a máquina pura. `wall` anda junto com `mono`.
export function createFakeClock(start: Instant = { mono: 1_000, wall: Date.UTC(2026, 8, 12, 13, 0, 0) }): FakeClock {
  let mono = start.mono;
  let wall = start.wall;
  return {
    now: () => ({ mono, wall }),
    advance(ms) {
      mono += ms;
      wall += ms;
    },
    set(nextMono, nextWall) {
      mono = nextMono;
      if (nextWall !== undefined) wall = nextWall;
    },
  };
}

// Relógio que lê o `Date.now` do Jest: acompanha `jest.advanceTimersByTime` com timers falsos.
export const jestClock: Clock = {
  now: () => ({ mono: Date.now(), wall: Date.now() }),
};

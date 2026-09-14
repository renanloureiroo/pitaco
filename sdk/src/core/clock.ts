// Relógio injetável. `mono` é o relógio monotônico (imune a ajuste de hora) usado para medir
// tempos; `wall` é o relógio do dispositivo, só para `occurredAt` e para prazos que precisam
// sobreviver a um reinício do app (fila local).

export interface Instant {
  readonly mono: number;
  readonly wall: number;
}

export interface Clock {
  now(): Instant;
}

interface PerformanceLike {
  now(): number;
}

export function createSystemClock(): Clock {
  let last = Number.NEGATIVE_INFINITY;

  const readMonotonic = (): number => {
    const performance = (globalThis as { performance?: Partial<PerformanceLike> }).performance;
    if (performance && typeof performance.now === 'function') {
      const value = performance.now();
      if (Number.isFinite(value)) return value;
    }
    return Date.now();
  };

  return {
    now() {
      const value = readMonotonic();
      // Nunca volta: um relógio que recua quebraria `elapsedMs` monotônico.
      last = value > last ? value : last;
      return { mono: last, wall: Date.now() };
    },
  };
}

export function toIsoString(wall: number): string {
  const safe = Number.isFinite(wall) ? wall : Date.now();
  try {
    return new Date(safe).toISOString();
  } catch {
    return new Date(Date.now()).toISOString();
  }
}

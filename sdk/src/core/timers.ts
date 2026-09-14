export type TimerHandle = ReturnType<typeof setTimeout>;

export interface Timers {
  set(callback: () => void, delayMs: number): TimerHandle;
  clear(handle: TimerHandle): void;
}

// Resolve `setTimeout` na hora da chamada, e não na importação: quem troca os timers (testes com
// relógio falso) é respeitado.
export const systemTimers: Timers = {
  set: (callback, delayMs) => setTimeout(callback, Math.max(0, delayMs)),
  clear: (handle) => clearTimeout(handle),
};

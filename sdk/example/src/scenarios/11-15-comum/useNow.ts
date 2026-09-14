// Relógio de parede para contadores regressivos. O valor muda só no callback do timer, nunca no
// render; `null` até a primeira leitura.
import { useEffect, useState } from 'react';

export function useNow(intervalMs = 250): number | null {
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    const tick = () => setNow(Date.now());
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, intervalMs);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [intervalMs]);

  return now;
}

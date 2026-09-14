// `usePitaco().diagnostics()` lido em intervalo: fila local, bloqueios, adiamento, pesquisa retida.
// É o mesmo ponto de diagnóstico do painel de depuração, trazido para dentro da tela do cenário.
import { type RuntimeDiagnostics, usePitaco } from '@pitaco/react-native';
import { useEffect, useState } from 'react';

export function usePolledDiagnostics(intervalMs = 500): RuntimeDiagnostics | null {
  const { diagnostics } = usePitaco();
  const [value, setValue] = useState<RuntimeDiagnostics | null>(null);

  useEffect(() => {
    const read = () => setValue(diagnostics());
    const first = setTimeout(read, 0);
    const timer = setInterval(read, intervalMs);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [diagnostics, intervalMs]);

  return value;
}

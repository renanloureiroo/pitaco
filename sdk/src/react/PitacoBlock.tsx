// Uso declarativo do bloqueio: bloqueia enquanto estiver montado, desbloqueia ao desmontar. Pensado
// para telas em que nada pode aparecer, como pagamento — `<PitacoBlock />` na tela inteira, sem
// precisar lembrar de chamar `unblock` na saída (inclusive numa saída por erro, troca de rota
// abrupta etc.: é o cleanup do efeito quem garante).
//
// Dois `<PitacoBlock />` com o mesmo motivo montados ao mesmo tempo não se anulam: `usePitaco()`
// conta referências por motivo (ver `PitacoRuntime.block`/`unblock`), então o bloqueio só some
// quando o último desmonta.

import { useEffect } from 'react';
import { usePitaco } from './usePitaco';

export interface PitacoBlockProps {
  // Motivo do bloqueio, para acumular corretamente com outros bloqueios simultâneos e para
  // aparecer em `placement_blocked`. Sem motivo, usa o motivo padrão do SDK.
  readonly reason?: string;
}

export function PitacoBlock({ reason }: PitacoBlockProps): null {
  const pitaco = usePitaco();

  useEffect(() => {
    pitaco.block(reason);
    return () => pitaco.unblock(reason);
  }, [pitaco, reason]);

  return null;
}

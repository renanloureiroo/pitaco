"use client";

import { useState } from "react";

/**
 * Reage à mudança do estado devolvido por uma Server Action **durante a renderização**, e não
 * em um efeito.
 *
 * Estado derivado de outro estado é ajustado no render — é o padrão que o React recomenda.
 * Fazer isso em `useEffect` custa uma renderização extra com o valor errado no meio, que o
 * usuário chega a ver piscar. Use apenas para ajustar estado **do próprio componente**:
 * atualizar o estado de um componente pai durante o render é proibido, e aí o efeito continua
 * sendo o caminho.
 */
export function useFormStateChange<TState>(
  state: TState,
  onChange: (state: TState) => void,
): void {
  const [seen, setSeen] = useState(state);

  if (!Object.is(seen, state)) {
    setSeen(state);
    onChange(state);
  }
}

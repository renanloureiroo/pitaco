// Movimento do foco de acessibilidade para a pergunta atual, extraído de
// `<PitacoSurveyContent />` como uma função pura e injetável: `findNodeHandle`/
// `AccessibilityInfo.setAccessibilityFocus` são nós nativos de verdade, que o renderizador de
// teste do React Native não tem (`findNodeHandle` sempre devolve `null` nele) — separar a
// decisão ("há um handle? então mande focar, e nunca deixe uma falha da API chegar à pesquisa")
// do jeito de obter o handle e de chamar a API é o que permite testar a decisão sozinha.
export function moveAccessibilityFocus(handle: number | null, setFocus: (handle: number) => void): void {
  if (handle === null) return;
  try {
    setFocus(handle);
  } catch {
    // Foco de acessibilidade nunca derruba a pesquisa.
  }
}

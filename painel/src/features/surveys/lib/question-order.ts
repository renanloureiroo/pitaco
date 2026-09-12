/**
 * Ordem resultante de soltar uma pergunta sobre outra.
 *
 * Devolve `undefined` quando não há o que enviar: soltar fora da lista, sobre si mesma, ou com
 * identificadores que a tela não conhece. É a função que o arrastar chama ao soltar — pura, para
 * ser testável sem simular ponteiro.
 */
export function draggedOrder(
  questionIds: string[],
  activeId: string,
  overId: string | undefined,
): string[] | undefined {
  if (overId === undefined || activeId === overId) {
    return undefined;
  }

  const from = questionIds.indexOf(activeId);
  const to = questionIds.indexOf(overId);

  if (from === -1 || to === -1) {
    return undefined;
  }

  const reordered = [...questionIds];
  const [moved] = reordered.splice(from, 1);
  reordered.splice(to, 0, moved);
  return reordered;
}

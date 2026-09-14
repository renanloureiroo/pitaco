// Registro dos quinze cenários do prompt (fase 5). Rotas fixas, em `app/(tabs)/cenarios/<id>.tsx`:
// a 5c e a 5d implementam o conteúdo de cada arquivo, sem renomear. Rotas extras de um cenário
// (a pesquisa como tela, no cenário 3) moram na mesma pasta com o mesmo prefixo: `03-tela-pesquisa.tsx`.
//
// testIDs estáveis para o Maestro:
// - item da lista na tela inicial: `cenario-<id>` (por exemplo `cenario-01-sheet`);
// - raiz da tela do cenário: `tela-<id>` (por exemplo `tela-01-sheet`).
export type ScenarioId =
  | '01-sheet'
  | '02-gorhom'
  | '03-tela'
  | '04-comparar'
  | '05-tema'
  | '06-textos'
  | '07-renderizador'
  | '08-slots'
  | '09-headless'
  | '10-modal-inline'
  | '11-bloqueio'
  | '12-adiamento'
  | '13-sem-rede'
  | '14-falhas'
  | '15-proxy';

export interface ScenarioMeta {
  readonly id: ScenarioId;
  readonly title: string;
  readonly description: string;
}

export const SCENARIOS: readonly ScenarioMeta[] = [
  { id: '01-sheet', title: '1. Sheet do SDK', description: 'Provider e um botão que chama track(), com o bottom sheet próprio do SDK.' },
  { id: '02-gorhom', title: '2. Com gorhom', description: 'A pesquisa dentro de um BottomSheetModal do @gorhom/bottom-sheet.' },
  { id: '03-tela', title: '3. Como tela', description: 'A pesquisa como uma rota do Expo Router, empilhada na navegação.' },
  { id: '04-comparar', title: '4. Comparar apresentações', description: 'A mesma pesquisa do seed nas formas Sheet, Gorhom e Tela.' },
  { id: '05-tema', title: '5. Tema', description: 'Claro, escuro e um tema com valores inválidos que degrada para o padrão.' },
  { id: '06-textos', title: '6. Textos', description: 'Rótulos substituídos em outro idioma.' },
  { id: '07-renderizador', title: '7. Renderizador substituído', description: 'Um NPS desenhado pelo app e um renderizador que lança erro de propósito.' },
  { id: '08-slots', title: '8. Slots', description: 'Cabeçalho e rodapé próprios.' },
  { id: '09-headless', title: '9. Headless', description: 'UI inteiramente própria sobre usePitacoSurvey().' },
  { id: '10-modal-inline', title: '10. Modal e inline', description: 'O modal em tela cheia do SDK, e PitacoSurveyContent no meio de uma tela com rolagem.' },
  { id: '11-bloqueio', title: '11. Bloqueio', description: 'Uma tela de pagamento com <PitacoBlock />: o evento dispara e nada aparece.' },
  { id: '12-adiamento', title: '12. Adiamento', description: 'Pesquisa adiada numa tela e liberada em outra, e um adiamento que estoura o prazo.' },
  { id: '13-sem-rede', title: '13. Sem rede', description: 'Responder em modo avião, fechar o app, reabrir com rede e ver a resposta chegar uma vez.' },
  { id: '14-falhas', title: '14. Falhas', description: 'Chave inválida, endereço inalcançável e API lenta, com o app seguindo navegável.' },
  { id: '15-proxy', title: '15. Proxy', description: 'O mesmo fluxo pela topologia com gateway (perfil "Proxy").' },
];

export const scenarioHref = (id: ScenarioId) => `/cenarios/${id}` as const;

export function scenarioById(id: ScenarioId): ScenarioMeta {
  const meta = SCENARIOS.find((scenario) => scenario.id === id);
  if (meta === undefined) throw new Error(`Cenário desconhecido: ${id}`);
  return meta;
}

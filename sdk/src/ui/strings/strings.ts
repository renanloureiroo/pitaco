// Todos os rótulos de interface da UI padrão, em pt-BR por padrão. Substituíveis por um objeto
// `strings` parcial no Provider (ou local em `<PitacoSurveyContent />`); quem não vier no
// substituto usa o padrão daqui.

export interface ProgressParams {
  readonly position: number;
  readonly total: number;
}

export interface QuestionA11yLabelParams {
  readonly position: number;
  readonly total: number;
  readonly statement: string;
  readonly required: boolean;
}

export interface PitacoStrings {
  readonly next: string;
  readonly back: string;
  readonly submit: string;
  readonly close: string;
  readonly closeA11yLabel: string;
  readonly requiredBadge: string;
  readonly optionalBadge: string;
  // Interpolação simples e tipada: uma função de parâmetros nomeados para a string final.
  readonly progress: (params: ProgressParams) => string;
  readonly questionA11yLabel: (params: QuestionA11yLabelParams) => string;
  readonly thankYouTitle: string;
  readonly thankYouBody: string;
  readonly freeTextNoticeDefault: string;
  readonly freeTextPlaceholder: string;
  readonly validationRequired: string;
  readonly npsMinLabelDefault: string;
  readonly npsMaxLabelDefault: string;
  readonly multipleChoiceHint: string;
  readonly loadingLabel: string;
}

export type PartialPitacoStrings = Partial<PitacoStrings>;

export const DEFAULT_STRINGS: PitacoStrings = {
  next: 'Próxima',
  back: 'Voltar',
  submit: 'Enviar',
  close: 'Fechar',
  closeA11yLabel: 'Fechar pesquisa',
  requiredBadge: 'obrigatória',
  optionalBadge: 'opcional',
  progress: ({ position, total }) => `Pergunta ${position} de ${total}`,
  questionA11yLabel: ({ position, total, statement, required }) =>
    `Pergunta ${position} de ${total}${required ? ', obrigatória' : ''}: ${statement}`,
  thankYouTitle: 'Obrigado!',
  thankYouBody: 'Sua resposta foi enviada.',
  freeTextNoticeDefault: 'Evite escrever dados pessoais nesta resposta.',
  freeTextPlaceholder: 'Digite sua resposta',
  validationRequired: 'Essa pergunta é obrigatória.',
  npsMinLabelDefault: 'Nada provável',
  npsMaxLabelDefault: 'Muito provável',
  multipleChoiceHint: 'Selecione uma ou mais opções',
  loadingLabel: 'Carregando…',
};

// Merge raso: cada rótulo substituído entra por inteiro; o que faltar usa o padrão. Não há
// validação de tipo aqui além do `Partial` do TypeScript, porque strings livres não têm "valor
// inválido" como cor ou número — qualquer texto (ou função) fornecido é aceito como está.
export function mergeStrings(override: PartialPitacoStrings | undefined): PitacoStrings {
  if (override === undefined || override === null) return DEFAULT_STRINGS;
  return { ...DEFAULT_STRINGS, ...override };
}

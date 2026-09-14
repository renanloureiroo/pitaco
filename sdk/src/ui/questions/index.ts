// Registro padrão dos seis renderizadores. A fase 3b substitui cada arquivo por um definitivo,
// mantendo o nome do arquivo, o nome do componente exportado e a assinatura
// `(props: QuestionRendererProps) => ReactNode` — este índice não deveria precisar mudar.

import type { PitacoRendererMap } from '../types';
import { FreeTextQuestion } from './FreeTextQuestion';
import { MultipleChoiceQuestion } from './MultipleChoiceQuestion';
import { NpsQuestion } from './NpsQuestion';
import { RatingQuestion } from './RatingQuestion';
import { ScaleQuestion } from './ScaleQuestion';
import { SingleChoiceQuestion } from './SingleChoiceQuestion';

export const DEFAULT_RENDERERS: PitacoRendererMap = {
  singleChoice: SingleChoiceQuestion,
  multipleChoice: MultipleChoiceQuestion,
  rating: RatingQuestion,
  scale: ScaleQuestion,
  nps: NpsQuestion,
  freeText: FreeTextQuestion,
};

export { FreeTextQuestion, MultipleChoiceQuestion, NpsQuestion, RatingQuestion, ScaleQuestion, SingleChoiceQuestion };

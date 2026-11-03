// Registro padrão dos seis renderizadores. Cada arquivo exporta um componente com a assinatura
// `(props: QuestionRendererProps) => ReactNode`; trocar a implementação não muda este índice.

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

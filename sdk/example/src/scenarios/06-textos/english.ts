// Todos os rótulos da UI padrão em inglês. Tipado como `PitacoStrings` (não parcial) para o
// TypeScript acusar se o SDK ganhar um rótulo novo e ele faltar aqui.
import type { PitacoStrings } from '@pitaco/react-native';
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export const ENGLISH_STRINGS: PitacoStrings = {
  next: 'Next',
  back: 'Back',
  submit: 'Submit',
  close: 'Close',
  closeA11yLabel: 'Close survey',
  requiredBadge: 'required',
  optionalBadge: 'optional',
  progress: ({ position, total }) => `Question ${position} of ${total}`,
  questionA11yLabel: ({ position, total, statement, required }) =>
    `Question ${position} of ${total}${required ? ', required' : ''}: ${statement}`,
  thankYouTitle: 'Thank you!',
  thankYouBody: 'Your answer has been sent.',
  freeTextNoticeDefault: 'Please avoid writing personal data in this answer.',
  freeTextPlaceholder: 'Type your answer',
  validationRequired: 'This question is required.',
  npsMinLabelDefault: 'Not at all likely',
  npsMaxLabelDefault: 'Extremely likely',
  multipleChoiceHint: 'Select one or more options',
  loadingLabel: 'Loading…',
};

export const ENGLISH_CONFIG: ScenarioProviderConfig = { strings: ENGLISH_STRINGS };

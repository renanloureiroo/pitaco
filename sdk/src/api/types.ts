// Tipos das rotas `/collect`, derivados do OpenAPI gerado (`npm run gen:api`). O resto do SDK
// importa daqui, nunca de `./openapi` direto, para a troca de nome de DTO no backend mexer num
// arquivo só.
import type { AnswerValueWire } from '../core/survey/answers';
import type { components } from './openapi';

type Schemas = components['schemas'];

export type EligibilityRequest = Schemas['EligibilityRequestDTO'];
export type EligibilityResponse = Schemas['EligibilityResponseDTO'];
export type DeliverableSurvey = Schemas['DeliverableSurvey'];
export type DeliverableQuestion = Schemas['DeliverableQuestion'];
export type RespondentPayload = Schemas['RespondentDTO'];
export type OpenDisplayRequest = Schemas['OpenDisplayRequestDTO'];
export type SuppressionRequest = Schemas['SuppressionRequestDTO'];
export type SdkErrorReportRequest = Schemas['SdkErrorReportRequestDTO'];
export type InteractionEventsReceipt = Schemas['InteractionEventsReceiptDTO'];
export type ApiProblem = Schemas['ApiError'];
export type ContractInteractionEventType = Schemas['InteractionEventType'];

// O gerador lê o `value` de `AnswerDTO` como `null & (...)`, que o TypeScript reduz a `never`
// (o OpenAPI declara o campo com `type: null` ao lado do `oneOf`). A forma real está no contrato:
// ausente em SKIPPED e NOT_APPLICABLE, texto, inteiro ou lista de textos em ANSWERED.
export type SubmissionAnswer = Omit<Schemas['AnswerDTO'], 'value'> & {
  value?: AnswerValueWire | null;
};

export type SubmissionRequest = Omit<Schemas['SubmissionRequestDTO'], 'answers'> & {
  answers: SubmissionAnswer[];
};

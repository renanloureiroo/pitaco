// O envio de respostas e desfecho (`POST /collect/displays/{displayId}/submission`) derivado do
// estado final da máquina.
//
// Concluída: toda pergunta renderizável entra, como ANSWERED, SKIPPED ou NOT_APPLICABLE.
// Dispensada: entra o que a pessoa alcançou (respostas dadas, perguntas vistas em branco como
// SKIPPED e as puladas pela condição), e o que ficou adiante não é enviado. A resposta a uma
// pergunta que depois deixou de se aplicar (a pessoa voltou e trocou a origem) vai como
// NOT_APPLICABLE, sem valor. Perguntas de tipo desconhecido nunca entram.

import type { SubmissionAnswer, SubmissionRequest } from '../../api/types';
import { toWireValue } from '../survey/answers';
import type { MachineState } from './machine';
import { resolveStatuses } from './path';

export function buildSubmission(state: MachineState): SubmissionRequest | null {
  if (state.status !== 'completed' && state.status !== 'dismissed') return null;
  const completed = state.status === 'completed';
  const questions = state.survey.questions;
  const statuses = resolveStatuses(questions, state.answers);
  const answers: SubmissionAnswer[] = [];

  questions.forEach((question, index) => {
    const status = statuses[index];
    const reached = completed || (state.visits[question.key] ?? 0) > 0;
    const value = state.answers[question.key];

    if (status === 'ANSWERED' && value !== undefined) {
      answers.push({ questionKey: question.key, status: 'ANSWERED', value: toWireValue(value) });
    } else if (status === 'NOT_APPLICABLE') {
      if (reached || state.passedOver[question.key] === true) {
        answers.push({ questionKey: question.key, status: 'NOT_APPLICABLE' });
      }
    } else if (reached) {
      answers.push({ questionKey: question.key, status: 'SKIPPED' });
    }
  });

  return { outcome: completed ? 'COMPLETED' : 'DISMISSED', answers };
}

import { Badge } from "@/components/ui/badge";
import { orNotConfigured } from "@/shared/lib";

import { getTransitions } from "../../api/lifecycle";
import { getSurvey } from "../../api/surveys";
import { SURVEY_STATE_LABELS, surveyStateVariant } from "../../lib/survey-labels";
import { TransitionActions } from "../lifecycle/transition-actions";
import { TransitionsHistory } from "../lifecycle/transitions-history";
import { DiscardSurveyButton } from "./discard-survey-button";
import { RenameSurvey } from "./rename-survey";

/**
 * Cabeçalho da pesquisa: nome, estado, transições permitidas e histórico.
 *
 * É um Server Component assíncrono renderizado sob `<Suspense>` no layout, para que a leitura
 * do cabeçalho não bloqueie a navegação entre montagem, disparo, publicação e versões (R6).
 */
export async function SurveyHeader({
  applicationId,
  surveyId,
}: {
  applicationId: string;
  surveyId: string;
}) {
  const [result, transitionsResult] = await Promise.all([
    getSurvey(applicationId, surveyId),
    getTransitions(applicationId, surveyId),
  ]);

  if (!result.ok) {
    return null;
  }

  const survey = result.data;
  /** As ações oferecidas vêm **apenas** desta leitura; nada é derivado do estado (FR-035). */
  const transitions = transitionsResult.ok ? transitionsResult.data : [];
  /** Descartar só é oferecido enquanto a pesquisa nunca foi publicada (FR-020). */
  const neverPublished = survey.publishedVersionNumber === undefined;
  /** Encerrada é somente leitura: nem renomear (FR-037). */
  const ended = survey.state === "ended";

  return (
    <header data-testid="survey-header" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-2">
          <h1 className="font-heading text-2xl font-semibold tracking-tight">{survey.name}</h1>
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <Badge data-testid="survey-state" variant={surveyStateVariant(survey.state)}>
              {SURVEY_STATE_LABELS[survey.state]}
            </Badge>
            <span>versão publicada: {orNotConfigured(survey.publishedVersionNumber)}</span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {ended ? null : (
            <RenameSurvey applicationId={applicationId} surveyId={surveyId} name={survey.name} />
          )}
          {neverPublished ? (
            <DiscardSurveyButton applicationId={applicationId} surveyId={surveyId} />
          ) : null}
          <TransitionActions
            applicationId={applicationId}
            surveyId={surveyId}
            transitions={transitions}
            currentState={survey.state}
          />
        </div>
      </div>

      <TransitionsHistory transitions={transitions} currentState={survey.state} />
    </header>
  );
}

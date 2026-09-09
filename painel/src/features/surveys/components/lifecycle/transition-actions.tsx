"use client";

import { CircleStopIcon, PauseIcon, PlayIcon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { endSurveyAction, pauseSurveyAction, resumeSurveyAction } from "../../actions";
import type { SurveyState } from "../../schemas/survey";
import { allowedManualReasons, type SurveyStateTransition } from "../../schemas/transition";

/**
 * Renderiza **apenas** o que `getTransitions` autoriza (FR-035): nada aqui é derivado do
 * estado da pesquisa. Lista de transições vazia ⇒ nenhuma ação oferecida, que é exatamente o
 * caso de uma pesquisa encerrada.
 *
 * Encerrar é irreversível, e por isso passa por confirmação explícita.
 */
export function TransitionActions({
  applicationId,
  surveyId,
  transitions,
  currentState,
}: {
  applicationId: string;
  surveyId: string;
  transitions: SurveyStateTransition[];
  currentState: SurveyState;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  const allowed = allowedManualReasons(transitions, currentState);

  if (allowed.length === 0) {
    return null;
  }

  function run(action: () => Promise<{ status: string; message?: string }>) {
    startTransition(async () => {
      const state = await action();
      setError(state.status === "error" ? state.message : undefined);
    });
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {allowed.includes("manual_pause") ? (
          <Button
            variant="outline"
            size="sm"
            data-testid="pause-survey-button"
            disabled={pending}
            onClick={() => run(() => pauseSurveyAction(applicationId, surveyId))}
          >
            <PauseIcon aria-hidden />
            Pausar
          </Button>
        ) : null}

        {allowed.includes("manual_resume") ? (
          <Button
            variant="outline"
            size="sm"
            data-testid="resume-survey-button"
            disabled={pending}
            onClick={() => run(() => resumeSurveyAction(applicationId, surveyId))}
          >
            <PlayIcon aria-hidden />
            Retomar
          </Button>
        ) : null}

        {allowed.includes("manual_end") ? (
          <ConfirmDialog
            trigger={
              <Button variant="destructive" size="sm" data-testid="end-survey-button">
                <CircleStopIcon aria-hidden />
                Encerrar
              </Button>
            }
            title="Encerrar esta pesquisa?"
            description="Encerrar é irreversível: a pesquisa sai do ar e não pode ser retomada nem editada."
            confirmLabel="Encerrar definitivamente"
            pending={pending}
            onConfirm={() => run(() => endSurveyAction(applicationId, surveyId))}
          />
        ) : null}
      </div>

      <FormError message={error} />
    </div>
  );
}

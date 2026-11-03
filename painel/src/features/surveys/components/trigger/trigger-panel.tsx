import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NOT_CONFIGURED, formatDateTime, formatSamplingRate } from "@/shared/lib";

import type { Trigger } from "../../schemas/trigger";
import { RulesList, type ObservedAttributeSuggestion } from "./rules-list";
import { TriggerForm } from "./trigger-form";

/**
 * Disparo e regras são exibidos **em conjunto** (FR-028): quem opera precisa ver, de uma vez,
 * quando a pesquisa aparece e para quem. Sem disparo, o estado é explícito — "não configurado",
 * com a ação de definir logo abaixo.
 */
export function TriggerPanel({
  applicationId,
  surveyId,
  trigger,
  observedEvents = [],
  observedAttributes = [],
  readOnly = false,
}: {
  applicationId: string;
  surveyId: string;
  trigger?: Trigger;
  /** Eventos já vistos na aplicação, oferecidos como sugestão no formulário. */
  observedEvents?: string[];
  /** Atributos já vistos na aplicação, com seus valores, oferecidos ao montar regras. */
  observedAttributes?: ObservedAttributeSuggestion[];
  readOnly?: boolean;
}) {
  return (
    <div data-testid="trigger-panel" className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Disparo</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          {trigger === undefined ? (
            <p className="text-sm text-muted-foreground">
              Disparo <strong>{NOT_CONFIGURED}</strong>. Sem ele, a pesquisa não aparece para
              ninguém.
            </p>
          ) : (
            <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Evento</dt>
                <dd data-testid="trigger-event" className="font-mono text-sm">
                  {trigger.eventName}
                </dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Início</dt>
                <dd className="text-sm">{formatDateTime(trigger.windowStart)}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Fim</dt>
                <dd className="text-sm">
                  {trigger.windowEnd === undefined
                    ? "janela aberta"
                    : formatDateTime(trigger.windowEnd)}
                </dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                  Amostragem
                </dt>
                <dd className="text-sm">{formatSamplingRate(trigger.samplingRate)}</dd>
              </div>
            </dl>
          )}

          <RulesList
            applicationId={applicationId}
            surveyId={surveyId}
            rules={trigger?.rules ?? []}
            observedAttributes={observedAttributes}
            readOnly={readOnly}
          />
        </CardContent>
      </Card>

      {readOnly ? null : (
        <Card>
          <CardHeader>
            <CardTitle>
              {trigger === undefined ? "Definir disparo" : "Redefinir disparo"}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <TriggerForm
              applicationId={applicationId}
              surveyId={surveyId}
              trigger={trigger}
              observedEvents={observedEvents}
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}

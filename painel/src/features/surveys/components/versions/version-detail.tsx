import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NOT_CONFIGURED, formatDateTime, formatSamplingRate } from "@/shared/lib";

import { CHANGE_KIND_LABELS } from "../../schemas/publication";
import type { SurveyVersionDetail } from "../../schemas/version";
import { QuestionsList } from "../questions/questions-list";

/** Conteúdo congelado: somente leitura, sem nenhuma ação de edição. */
export function VersionDetail({ version }: { version: SurveyVersionDetail }) {
  return (
    <div data-testid="version-detail" className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-3">
            Versão {version.number}
            <Badge variant={version.status === "published" ? "default" : "secondary"}>
              {version.status === "published" ? "Publicada" : "Rascunho"}
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                Publicada em
              </dt>
              <dd className="text-sm">
                {version.publishedAt === undefined
                  ? NOT_CONFIGURED
                  : formatDateTime(version.publishedAt)}
              </dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">Mudança</dt>
              <dd className="text-sm">
                {version.changeKind === undefined
                  ? NOT_CONFIGURED
                  : CHANGE_KIND_LABELS[version.changeKind]}
              </dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">Resumo</dt>
              <dd className="text-sm">{version.changeSummary ?? NOT_CONFIGURED}</dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">Grupo</dt>
              <dd className="text-sm">{version.comparabilityGroup}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Disparo congelado</CardTitle>
        </CardHeader>
        <CardContent>
          {version.trigger === undefined ? (
            <p className="text-sm text-muted-foreground">Disparo {NOT_CONFIGURED}.</p>
          ) : (
            <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Evento</dt>
                <dd className="font-mono text-sm">{version.trigger.eventName}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Início</dt>
                <dd className="text-sm">{formatDateTime(version.trigger.windowStart)}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">Fim</dt>
                <dd className="text-sm">
                  {version.trigger.windowEnd === undefined
                    ? "janela aberta"
                    : formatDateTime(version.trigger.windowEnd)}
                </dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                  Amostragem
                </dt>
                <dd className="text-sm">{formatSamplingRate(version.trigger.samplingRate)}</dd>
              </div>
            </dl>
          )}
        </CardContent>
      </Card>

      <section data-testid="version-questions" className="flex flex-col gap-3">
        <h2 className="font-heading text-lg font-medium">Perguntas congeladas</h2>
        {version.questions.length === 0 ? (
          <p className="text-sm text-muted-foreground">Esta versão não tem perguntas.</p>
        ) : (
          <QuestionsList questions={version.questions} />
        )}
      </section>
    </div>
  );
}

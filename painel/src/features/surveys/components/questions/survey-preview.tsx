"use client";

import dynamic from "next/dynamic";
import { useState } from "react";
import type { InteractionEvent } from "@pitaco/react-native";
import type { PitacoPreviewProps } from "@pitaco/react-native/preview";

import { useTheme } from "next-themes";

import type { Question } from "../../schemas/question";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

// SSR false é necessário pois usa react-native-web
const PitacoPreview = dynamic<PitacoPreviewProps>(
  () => import("@pitaco/react-native/preview").then((mod) => mod.PitacoPreview),
  { ssr: false }
);

function mapToDeliverable(q: Question) {
  return {
    key: q.key,
    position: q.position,
    statement: q.statement,
    type: q.type.toUpperCase(),
    required: q.required,
    options: q.options,
    range: q.range,
    condition: q.condition,
  };
}

/** Resumo de uma linha do `data` do evento: `position=1 · visit=1 · from=start`. */
function summarize(data: Record<string, unknown>): string {
  return Object.entries(data)
    .map(([key, value]) => `${key}=${typeof value === "object" ? JSON.stringify(value) : String(value)}`)
    .join(" · ");
}

export function SurveyPreview({ questions }: { questions: Question[] }) {
  const [events, setEvents] = useState<InteractionEvent[]>([]);
  const { resolvedTheme } = useTheme();

  const deliverableQuestions = questions.map(mapToDeliverable);

  const schema = {
    surveyId: "draft",
    versionId: "draft",
    versionNumber: 1,
    questions: deliverableQuestions,
    freeTextNotice: {
      message: "Respostas de texto livre não devem conter dados pessoais."
    }
  };

  return (
    <div className="flex flex-col gap-4" data-testid="survey-preview-section">
      <Card>
        <CardHeader>
          <CardTitle>Pré-visualização</CardTitle>
          <CardDescription>
            O renderizador real do SDK, com o tema padrão. O app pode trocar cores e textos.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="relative h-[460px] overflow-hidden rounded-lg border border-border bg-background">
            <PitacoPreview
              schema={schema}
              theme={{ colorScheme: resolvedTheme === "dark" ? "dark" : "light" }}
              onEvent={(e: InteractionEvent) => setEvents((prev) => [...prev, e])}
              resetKey={JSON.stringify(deliverableQuestions)}
            />
          </div>
        </CardContent>
      </Card>
      
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <CardTitle>Eventos emitidos</CardTitle>
            <CardDescription>O que o SDK enviaria ao backend enquanto você interage acima.</CardDescription>
          </div>
          {events.length > 0 ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => setEvents([])}>
              Limpar
            </Button>
          ) : null}
        </CardHeader>
        <CardContent>
          <div data-testid="survey-preview-events">
            {events.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhum evento emitido ainda.</p>
            ) : (
              <ol className="flex max-h-72 flex-col gap-1 overflow-y-auto text-xs">
                {[...events].reverse().map((event) => (
                  <li key={`${event.displayId}-${event.seq}`} className="rounded-md bg-surface-raised">
                    <details>
                      <summary className="flex cursor-pointer items-center gap-2 px-2.5 py-1.5">
                        <span className="w-6 shrink-0 text-right text-ink-muted tabular-nums">{event.seq}</span>
                        <span className="font-mono font-medium text-foreground">{event.type}</span>
                        <span className="min-w-0 truncate font-mono text-ink-muted">
                          {summarize(event.data as Record<string, unknown>)}
                        </span>
                      </summary>
                      <pre className="overflow-x-auto border-t border-border px-2.5 py-2 font-mono text-[11px] text-ink-secondary">
                        {JSON.stringify(event, null, 2)}
                      </pre>
                    </details>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

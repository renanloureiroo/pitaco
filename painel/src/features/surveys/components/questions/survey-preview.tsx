"use client";

import dynamic from "next/dynamic";
import { useState } from "react";
import type { InteractionEvent } from "@pitaco/react-native";

import type { Question } from "../../schemas/question";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

// SSR false é necessário pois usa react-native-web
const PitacoPreview = dynamic(
  () => import("@pitaco/react-native").then((mod) => mod.PitacoPreview),
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

export function SurveyPreview({ questions }: { questions: Question[] }) {
  const [events, setEvents] = useState<InteractionEvent[]>([]);

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
        </CardHeader>
        <CardContent>
          <div className="border rounded-md overflow-hidden bg-background h-[500px] relative">
            <PitacoPreview
              schema={schema}
              onEvent={(e) => setEvents((prev) => [...prev, e])}
              resetKey={JSON.stringify(deliverableQuestions)}
            />
          </div>
        </CardContent>
      </Card>
      
      <Card>
        <CardHeader>
          <CardTitle>Eventos Emitidos</CardTitle>
        </CardHeader>
        <CardContent>
          <div data-testid="survey-preview-events">
            {events.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhum evento emitido ainda.</p>
            ) : (
              <ul className="text-xs font-mono space-y-2 max-h-64 overflow-y-auto">
                {events.map((e, i) => (
                  <li key={i} className="bg-muted p-2 rounded-md overflow-auto">
                    {JSON.stringify(e, null, 2)}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

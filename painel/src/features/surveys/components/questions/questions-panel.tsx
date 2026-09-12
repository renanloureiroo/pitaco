"use client";

import { PencilIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/shared/components";

import type { Question } from "../../schemas/question";
import { MoveQuestionButtons } from "./move-question-buttons";
import { QuestionForm } from "./question-form";
import { QuestionsList } from "./questions-list";
import { RemoveQuestionButton } from "./remove-question-button";
import { SortableQuestionsList } from "./sortable-questions-list";

/**
 * Montagem das perguntas.
 *
 * `"use client"` porque "qual pergunta estou editando" e "o formulário de adicionar está
 * aberto" são estado de tela. A leitura continua sendo do Server Component que passa
 * `questions` — nada aqui fala com a API diretamente.
 */
export function QuestionsPanel({
  applicationId,
  surveyId,
  questions,
  readOnly = false,
}: {
  applicationId: string;
  surveyId: string;
  questions: Question[];
  readOnly?: boolean;
}) {
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<string>();

  const ordered = [...questions].sort((a, b) => a.position - b.position);
  const questionIds = ordered.map((question) => question.id);
  const editing = ordered.find((question) => question.id === editingId);

  if (ordered.length === 0 && !adding) {
    return (
      <EmptyState
        title="Nenhuma pergunta ainda"
        description={
          readOnly
            ? "Esta versão foi congelada sem perguntas."
            : "Adicione a primeira pergunta para começar a montar a pesquisa."
        }
        action={
          readOnly ? undefined : (
            <Button data-testid="add-question-button" onClick={() => setAdding(true)}>
              <PlusIcon aria-hidden />
              Adicionar pergunta
            </Button>
          )
        }
      />
    );
  }

  const actionsFor = (question: Question) => (
    <>
      <MoveQuestionButtons
        applicationId={applicationId}
        surveyId={surveyId}
        questionIds={questionIds}
        questionId={question.id}
      />
      <Button
        variant="ghost"
        size="icon"
        data-testid="edit-question-button"
        aria-label={`Editar pergunta "${question.statement}"`}
        onClick={() => {
          setAdding(false);
          setEditingId(question.id);
        }}
      >
        <PencilIcon aria-hidden />
      </Button>
      <RemoveQuestionButton
        applicationId={applicationId}
        surveyId={surveyId}
        questionId={question.id}
        statement={question.statement}
      />
    </>
  );

  return (
    <div className="flex flex-col gap-4">
      {readOnly ? (
        <QuestionsList questions={ordered} />
      ) : (
        <SortableQuestionsList
          applicationId={applicationId}
          surveyId={surveyId}
          questions={ordered}
          actionsFor={actionsFor}
        />
      )}

      {readOnly ? null : editing !== undefined ? (
        <Card>
          <CardContent>
            <QuestionForm
              key={editing.id}
              applicationId={applicationId}
              surveyId={surveyId}
              questions={ordered}
              question={editing}
              onFinished={() => setEditingId(undefined)}
            />
          </CardContent>
        </Card>
      ) : adding ? (
        <Card>
          <CardContent>
            <QuestionForm
              applicationId={applicationId}
              surveyId={surveyId}
              questions={ordered}
              onFinished={() => setAdding(false)}
            />
          </CardContent>
        </Card>
      ) : (
        <div>
          <Button data-testid="add-question-button" onClick={() => setAdding(true)}>
            <PlusIcon aria-hidden />
            Adicionar pergunta
          </Button>
        </div>
      )}
    </div>
  );
}

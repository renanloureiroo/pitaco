"use client";

import { EyeIcon, PencilIcon, PlusIcon } from "lucide-react";
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
import { SurveyPreview } from "./survey-preview";

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
  const [liveDraft, setLiveDraft] = useState<Question>();
  const [previewOnlyId, setPreviewOnlyId] = useState<string>();

  const ordered = [...questions].sort((a, b) => a.position - b.position);
  const questionIds = ordered.map((question) => question.id);
  const editing = ordered.find((question) => question.id === editingId);

  // Compute preview questions applying the live draft
  const previewQuestions = liveDraft
    ? [liveDraft]
    : previewOnlyId
      ? ordered.filter((q) => q.id === previewOnlyId)
      : ordered;

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
              <PlusIcon aria-hidden className="mr-2 h-4 w-4" />
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
        size="sm"
        aria-label={`Visualizar pergunta "${question.statement}"`}
        onClick={() => setPreviewOnlyId(previewOnlyId === question.id ? undefined : question.id)}
        className={previewOnlyId === question.id ? "bg-accent" : ""}
      >
        <EyeIcon aria-hidden className="mr-2 h-4 w-4" />
        Visualizar
      </Button>
      <Button
        variant="ghost"
        size="sm"
        data-testid="edit-question-button"
        aria-label={`Editar pergunta "${question.statement}"`}
        onClick={() => {
          setAdding(false);
          setEditingId(question.id);
          setPreviewOnlyId(undefined);
        }}
      >
        <PencilIcon aria-hidden className="mr-2 h-4 w-4" />
        Editar
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
    <div className="grid lg:grid-cols-2 gap-8 items-start">
      <div className="flex flex-col gap-4">
        {readOnly ? (
          <QuestionsList questions={ordered} />
        ) : editing !== undefined ? (
          <Card>
            <CardContent className="pt-6">
              <QuestionForm
                key={editing.id}
                applicationId={applicationId}
                surveyId={surveyId}
                questions={ordered}
                question={editing}
                onFinished={() => {
                  setEditingId(undefined);
                  setLiveDraft(undefined);
                }}
                onLiveUpdate={setLiveDraft}
              />
            </CardContent>
          </Card>
        ) : adding ? (
          <Card>
            <CardContent className="pt-6">
              <QuestionForm
                applicationId={applicationId}
                surveyId={surveyId}
                questions={ordered}
                onFinished={() => {
                  setAdding(false);
                  setLiveDraft(undefined);
                }}
                onLiveUpdate={setLiveDraft}
              />
            </CardContent>
          </Card>
        ) : (
          <>
            <SortableQuestionsList
              applicationId={applicationId}
              surveyId={surveyId}
              questions={ordered}
              actionsFor={actionsFor}
            />
            <div>
              <Button data-testid="add-question-button" onClick={() => setAdding(true)}>
                <PlusIcon aria-hidden className="mr-2 h-4 w-4" />
                Adicionar pergunta
              </Button>
              {previewOnlyId !== undefined && (
                <Button variant="ghost" className="ml-2" onClick={() => setPreviewOnlyId(undefined)}>
                  Ver todas no preview
                </Button>
              )}
            </div>
          </>
        )}
      </div>

      <div className="sticky top-4">
        <SurveyPreview questions={previewQuestions} />
      </div>
    </div>
  );
}

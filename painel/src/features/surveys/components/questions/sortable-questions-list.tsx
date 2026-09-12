"use client";

import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon } from "lucide-react";
import { useState, useTransition, type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { FormError } from "@/shared/components";
import { cn } from "@/lib/utils";

import { reorderQuestionsAction } from "../../actions";
import { describeCondition } from "../../lib/condition";
import { draggedOrder } from "../../lib/question-order";
import type { Question } from "../../schemas/question";
import { QuestionCard } from "./question-card";

/** O que acontece ao soltar, separado do componente para ser testável sem simular ponteiro. */
export function reorderOnDrop({
  applicationId,
  surveyId,
  ids,
  event,
  showPending,
  showError,
  run,
}: {
  applicationId: string;
  surveyId: string;
  ids: string[];
  event: Pick<DragEndEvent, "active" | "over">;
  showPending: (ids: string[] | undefined) => void;
  showError: (message: string | undefined) => void;
  run: (work: () => Promise<void>) => void;
}): void {
  const { active, over } = event;
  const reordered = draggedOrder(ids, String(active.id), over === null ? undefined : String(over.id));
  if (reordered === undefined) {
    return;
  }

  showError(undefined);
  showPending(reordered);
  run(async () => {
    const state = await reorderQuestionsAction(applicationId, surveyId, reordered);
    if (state.status === "error") {
      showError(state.message);
    }
    showPending(undefined);
  });
}

/**
 * Lista de perguntas reordenável por arrastar, com a alça acessível por teclado (espaço para
 * pegar, setas para mover, espaço para soltar). Soltar envia a **permutação completa** pela
 * mesma ação que os botões de mover usam; até a resposta chegar, a tela mostra a ordem
 * pretendida, e volta à do servidor se ele recusar.
 */
export function SortableQuestionsList({
  applicationId,
  surveyId,
  questions,
  actionsFor,
}: {
  applicationId: string;
  surveyId: string;
  questions: Question[];
  actionsFor?: (question: Question) => ReactNode;
}) {
  const ordered = [...questions].sort((a, b) => a.position - b.position);
  const serverIds = ordered.map((question) => question.id);

  const [pendingIds, setPendingIds] = useState<string[]>();
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  const ids = pendingIds ?? serverIds;
  const byId = new Map(ordered.map((question) => [question.id, question]));

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  function statementOf(id: string | number | undefined): string {
    return id === undefined ? "" : (byId.get(String(id))?.statement ?? "");
  }

  function positionOf(id: string | number | undefined): number {
    return id === undefined ? 0 : ids.indexOf(String(id)) + 1;
  }

  const announcements: Announcements = {
    onDragStart: ({ active }) =>
      `Pegou "${statementOf(active.id)}", na posição ${positionOf(active.id)} de ${ids.length}.`,
    onDragOver: ({ active, over }) =>
      over === null
        ? `"${statementOf(active.id)}" está fora da lista.`
        : `"${statementOf(active.id)}" foi movida para a posição ${positionOf(over.id)} de ${ids.length}.`,
    onDragEnd: ({ active, over }) =>
      over === null
        ? `"${statementOf(active.id)}" voltou à posição ${positionOf(active.id)}.`
        : `"${statementOf(active.id)}" solta na posição ${positionOf(over.id)} de ${ids.length}.`,
    onDragCancel: ({ active }) =>
      `Arrastar cancelado. "${statementOf(active.id)}" voltou à posição ${positionOf(active.id)}.`,
  };

  function handleDragEnd(event: DragEndEvent) {
    reorderOnDrop({
      applicationId,
      surveyId,
      ids,
      event,
      showPending: setPendingIds,
      showError: setError,
      run: startTransition,
    });
  }

  return (
    <DndContext
      // Estável entre servidor e cliente: sem ele, o dnd-kit gera o id do `aria-describedby` com
      // um contador que diverge na hidratação.
      id={`questions-${surveyId}`}
      sensors={sensors}
      collisionDetection={closestCenter}
      accessibility={{
        announcements,
        screenReaderInstructions: {
          draggable:
            "Para reordenar, pressione espaço na alça, use as setas para mover e espaço de novo " +
            "para soltar. Escape cancela.",
        },
      }}
      onDragEnd={handleDragEnd}
    >
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        <ol data-testid="questions-list" className="flex flex-col gap-3">
          {ids.map((id, index) => {
            const question = byId.get(id);
            return question === undefined ? null : (
              <SortableQuestionItem
                key={id}
                question={question}
                index={index}
                disabled={pending}
                actions={actionsFor?.(question)}
                conditionSummary={
                  question.condition === undefined
                    ? undefined
                    : describeCondition(question.condition, ordered)
                }
              />
            );
          })}
        </ol>
      </SortableContext>
      <FormError message={error} />
    </DndContext>
  );
}

function SortableQuestionItem({
  question,
  index,
  disabled,
  actions,
  conditionSummary,
}: {
  question: Question;
  index: number;
  disabled: boolean;
  actions?: ReactNode;
  conditionSummary?: string;
}) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: question.id, disabled });

  return (
    <li
      ref={setNodeRef}
      data-testid="question-item"
      data-question-key={question.key}
      className={cn(isDragging && "z-10 opacity-80")}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      <QuestionCard
        question={question}
        index={index}
        handle={
          <Button
            ref={setActivatorNodeRef}
            type="button"
            variant="ghost"
            size="icon"
            data-testid="drag-question-handle"
            aria-label={`Arrastar pergunta "${question.statement}"`}
            className="cursor-grab touch-none active:cursor-grabbing"
            disabled={disabled}
            {...attributes}
            {...listeners}
          >
            <GripVerticalIcon aria-hidden />
          </Button>
        }
        actions={actions}
        conditionSummary={conditionSummary}
      />
    </li>
  );
}

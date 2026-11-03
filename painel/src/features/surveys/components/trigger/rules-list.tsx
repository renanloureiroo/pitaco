"use client";

import { Trash2Icon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { removeSegmentationRuleAction } from "../../actions";
import { RULE_OPERATION_LABELS } from "../../lib/survey-labels";
import { requiresValue, type SegmentationRule } from "../../schemas/trigger";
import { RuleForm, type ObservedAttributeSuggestion } from "./rule-form";

export type { ObservedAttributeSuggestion };

function RemoveRuleButton({
  applicationId,
  surveyId,
  rule,
  description,
}: {
  applicationId: string;
  surveyId: string;
  rule: SegmentationRule;
  description: string;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  return (
    <>
      <ConfirmDialog
        trigger={
          <Button
            variant="ghost"
            size="icon"
            data-testid="remove-rule-button"
            aria-label={`Remover regra ${description}`}
          >
            <Trash2Icon aria-hidden />
          </Button>
        }
        title="Remover esta regra?"
        description={`A regra "${description}" deixa de restringir quem recebe a pesquisa.`}
        confirmLabel="Remover"
        pending={pending}
        onConfirm={() =>
          startTransition(async () => {
            const state = await removeSegmentationRuleAction(applicationId, surveyId, rule.id);
            if (state.status === "error") {
              setError(state.message);
            }
          })
        }
      />
      <FormError message={error} />
    </>
  );
}

export function describeRule(rule: SegmentationRule): string {
  const operation = RULE_OPERATION_LABELS[rule.operation];
  return requiresValue(rule.operation)
    ? `${rule.attribute} ${operation} "${rule.value ?? ""}"`
    : `${rule.attribute} ${operation}`;
}

export function RulesList({
  applicationId,
  surveyId,
  rules,
  observedAttributes = [],
  readOnly = false,
}: {
  applicationId: string;
  surveyId: string;
  rules: SegmentationRule[];
  observedAttributes?: ObservedAttributeSuggestion[];
  readOnly?: boolean;
}) {
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-heading text-sm font-medium">Regras de segmentação</h3>

      {rules.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Sem regras: a pesquisa alcança todo mundo que dispara o evento.
        </p>
      ) : (
        <ul data-testid="rules-list" className="flex flex-col gap-2">
          {rules.map((rule) => (
            <li
              key={rule.id}
              data-testid="rule-item"
              className="flex items-center justify-between gap-4 rounded-md border px-3 py-2 text-sm"
            >
              <span>{describeRule(rule)}</span>
              {readOnly ? null : (
                <RemoveRuleButton
                  applicationId={applicationId}
                  surveyId={surveyId}
                  rule={rule}
                  description={describeRule(rule)}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      {/* O formulário fica sempre à mão: adicionar regra é a operação corrente desta tela. */}
      {readOnly ? null : (
        <RuleForm
          applicationId={applicationId}
          surveyId={surveyId}
          observedAttributes={observedAttributes}
        />
      )}
    </div>
  );
}

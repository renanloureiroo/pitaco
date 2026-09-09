"use client";

import { PencilIcon } from "lucide-react";
import { useActionState, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { useFormStateChange } from "@/shared/hooks";
import { idleFormState, type FormState } from "@/shared/lib";

import { renameSurveyAction } from "../../actions";

export function RenameSurvey({
  applicationId,
  surveyId,
  name,
}: {
  applicationId: string;
  surveyId: string;
  name: string;
}) {
  const [open, setOpen] = useState(false);
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    renameSurveyAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  // Só fecha depois que o backend aceitou: uma recusa precisa continuar visível.
  useFormStateChange(state, (next) => {
    if (next.status === "success") {
      setOpen(false);
    }
  });

  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm" data-testid="rename-survey-button">
          <PencilIcon aria-hidden />
          Renomear
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form action={formAction} data-testid="rename-survey-form" className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>Renomear pesquisa</DialogTitle>
            <DialogDescription>
              O nome é interno à operação e não aparece para quem responde.
            </DialogDescription>
          </DialogHeader>

          {state.status === "error" ? <FormError message={state.message} /> : null}

          <Field>
            <FieldLabel htmlFor="rename-survey-name">Nome</FieldLabel>
            <Input
              id="rename-survey-name"
              name="name"
              required
              maxLength={160}
              defaultValue={state.status === "error" ? (state.values.name ?? name) : name}
              aria-invalid={errors.name !== undefined}
            />
            <FieldMessage name="name" errors={errors} />
          </Field>

          <DialogFooter>
            <SubmitButton pending={pending} testId="rename-survey-submit">
              Salvar
            </SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

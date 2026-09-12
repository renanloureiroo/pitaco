"use client";

import { CopyIcon } from "lucide-react";
import { useActionState, useEffect, useState } from "react";

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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { duplicateSurveyAction } from "../../actions";

export type DuplicationTarget = { id: string; name: string };

/**
 * Duplicar: escolher onde a cópia nasce e, se quiser, com que nome. O caso comum é rodar em
 * outro app a pesquisa que rodou neste — por isso a aplicação de destino vem primeiro. A cópia
 * não leva respostas nem histórico, e a tela diz isso antes de confirmar.
 */
export function DuplicateSurveyDialog({
  applicationId,
  surveyId,
  surveyName,
  applications,
}: {
  applicationId: string;
  surveyId: string;
  surveyName: string;
  applications: DuplicationTarget[];
}) {
  const [open, setOpen] = useState(false);
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    duplicateSurveyAction.bind(null, applicationId, surveyId),
    idleFormState,
  );
  const [target, setTarget] = useState(applicationId);

  // O reset do formulário depois de uma recusa devolve o Select ao valor inicial; o destino
  // escolhido volta de `values`, que traz o que foi enviado.
  useEffect(() => {
    if (state.status === "error" && state.values.targetApplicationId !== undefined) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- ver comentário acima
      setTarget(state.values.targetApplicationId);
    }
  }, [state]);

  const errors = state.status === "error" ? state.fieldErrors : {};
  const values = state.status === "error" ? state.values : {};
  const targets = applications.some((application) => application.id === applicationId)
    ? applications
    : [{ id: applicationId, name: "Esta aplicação" }, ...applications];

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" data-testid="duplicate-survey-button">
          <CopyIcon aria-hidden />
          Duplicar
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form action={formAction} data-testid="duplicate-survey-form" className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>Duplicar pesquisa</DialogTitle>
            <DialogDescription>
              A cópia nasce em rascunho, com as perguntas, o disparo, a segmentação e a exposição
              desta pesquisa. Respostas e versões anteriores ficam só com a original.
            </DialogDescription>
          </DialogHeader>

          {state.status === "error" ? <FormError message={state.message} /> : null}

          <Field>
            <FieldLabel htmlFor="duplicate-target">Aplicação de destino</FieldLabel>
            <input type="hidden" name="targetApplicationId" value={target} />
            <Select value={target} onValueChange={setTarget}>
              <SelectTrigger id="duplicate-target" data-testid="duplicate-target-select">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {targets.map((application) => (
                  <SelectItem key={application.id} value={application.id}>
                    {application.id === applicationId
                      ? `${application.name} (esta)`
                      : application.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldMessage name="targetApplicationId" errors={errors} />
          </Field>

          <Field>
            <FieldLabel htmlFor="duplicate-name">Nome da cópia</FieldLabel>
            <Input
              id="duplicate-name"
              name="name"
              maxLength={120}
              placeholder={`Cópia de ${surveyName}`}
              defaultValue={values.name ?? ""}
              aria-invalid={errors.name !== undefined}
            />
            <FieldDescription>Em branco, vira “Cópia de” seguido do nome atual.</FieldDescription>
            <FieldMessage name="name" errors={errors} />
          </Field>

          <DialogFooter>
            <SubmitButton pending={pending} testId="duplicate-survey-submit">
              Duplicar
            </SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

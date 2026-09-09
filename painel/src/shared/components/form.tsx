import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { FieldError as FieldErrorPrimitive } from "@/components/ui/field";
import { cn } from "@/lib/utils";
import type { FieldErrors } from "@/shared/lib";

/**
 * Peças de formulário repetidas em todas as features.
 *
 * O painel não usa `react-hook-form` nem o `Form` do shadcn, que é adaptador dele (R4): o
 * formulário é `<form action={formAction}>` com `useActionState`, e a verdade da validação é o
 * backend. O que se repete — e por isso nasce compartilhado — é como a recusa é exibida e como
 * o envio duplicado é bloqueado.
 */

/** Mensagem por campo, com o testid estável de que o E2E depende. */
export function FieldMessage({
  name,
  errors,
}: {
  name: string;
  errors: FieldErrors;
}) {
  const message = errors[name];

  if (message === undefined) {
    return null;
  }

  return (
    <FieldErrorPrimitive data-testid={`field-error-${name}`} role="alert">
      {message}
    </FieldErrorPrimitive>
  );
}

/** Recusa que não pertence a nenhum campo — conflito, indisponibilidade, regra do backend. */
export function FormError({ message }: { message?: string }) {
  if (message === undefined || message === "") {
    return null;
  }

  return (
    <p
      data-testid="form-error"
      role="alert"
      className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
    >
      {message}
    </p>
  );
}

/**
 * Envio bloqueado enquanto a action está pendente (FR-006): dois cliques não criam dois
 * registros.
 */
export function SubmitButton({
  pending,
  disabled = false,
  children,
  className,
  testId = "submit-button",
  ...rest
}: {
  pending: boolean;
  /** Bloqueio por regra de tela — desabilita sem sinalizar trabalho em curso. */
  disabled?: boolean;
  children: ReactNode;
  className?: string;
  testId?: string;
} & Omit<React.ComponentProps<typeof Button>, "children" | "className" | "disabled" | "type">) {
  return (
    <Button
      type="submit"
      disabled={pending || disabled}
      data-testid={testId}
      className={cn(className)}
      {...rest}
    >
      {pending ? <Spinner aria-hidden /> : null}
      {children}
    </Button>
  );
}

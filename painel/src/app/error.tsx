"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function AppError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 items-center px-6 py-16">
      <ErrorState
        description="Algo falhou ao montar esta tela. Tentar de novo costuma resolver quando a causa é a API estar fora do ar."
        retry={retry}
      />
    </main>
  );
}

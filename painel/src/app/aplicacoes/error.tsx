"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function ApplicationsError({
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
    <main className="mx-auto flex w-full max-w-6xl flex-1 items-center px-6 py-10">
      <ErrorState
        title="Não foi possível listar as aplicações"
        description="A API do Pitaco não respondeu como esperado."
        retry={retry}
      />
    </main>
  );
}

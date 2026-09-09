"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function ApplicationError({
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
    <div className="py-10">
      <ErrorState
        title="Não foi possível carregar esta aplicação"
        description="A API do Pitaco não respondeu como esperado."
        retry={retry}
      />
    </div>
  );
}

"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function RespondentsError({
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
        title="Não foi possível carregar os respondentes"
        description="A API do Pitaco não respondeu como esperado."
        retry={retry}
      />
    </div>
  );
}

"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function SurveyError({
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
        title="Não foi possível carregar esta pesquisa"
        description="A API do Pitaco não respondeu como esperado."
        retry={retry}
      />
    </div>
  );
}

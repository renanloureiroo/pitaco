"use client";

import { useEffect } from "react";

import { ErrorState } from "@/shared/components";

export default function DisplayError({
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
        title="Não foi possível carregar esta exibição"
        description="A API do Pitaco não respondeu como esperado. A nova tentativa recarrega só esta tela."
        retry={retry}
      />
    </div>
  );
}

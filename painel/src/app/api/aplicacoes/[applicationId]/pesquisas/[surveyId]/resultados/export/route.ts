import type { NextRequest } from "next/server";

import { backendExportUrl } from "@/features/results";

/**
 * Repasse em fluxo do CSV do backend. Existe porque nada no navegador fala com a API (R5):
 * o corpo é encaminhado como chega, sem ser carregado na memória do painel, e os cabeçalhos
 * de tipo e de nome de arquivo são preservados. Recusa do backend volta como veio.
 */
export async function GET(
  request: NextRequest,
  { params }: RouteContext<"/api/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/export">,
) {
  const { applicationId, surveyId } = await params;
  const query: Record<string, string> = {};
  request.nextUrl.searchParams.forEach((value, key) => {
    query[key] = value;
  });

  let upstream: Response;
  try {
    upstream = await fetch(backendExportUrl(applicationId, surveyId, query), {
      headers: { Accept: "text/csv, application/problem+json" },
      cache: "no-store",
    });
  } catch {
    return new Response("Não foi possível falar com a API do Pitaco.", {
      status: 502,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }

  const headers = new Headers();
  for (const name of ["content-type", "content-disposition"]) {
    const value = upstream.headers.get(name);
    if (value !== null) {
      headers.set(name, value);
    }
  }

  return new Response(upstream.body, { status: upstream.status, headers });
}

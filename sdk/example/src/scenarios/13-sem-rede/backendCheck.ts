// Conferência do cenário 13 no backend local, pela API administrativa (não é rota do SDK, e só existe
// porque o exemplo roda contra um backend de desenvolvimento sem autenticação administrativa). Lê a
// exportação dos resultados da pesquisa do seed e conta as linhas da exibição: uma linha por
// exibição, com o desfecho (`COMPLETED`/`DISMISSED`) quando a resposta chegou, `IN_PROGRESS`
// quando só a abertura chegou.
//
// Mesma conferência pelo terminal (dentro de `sdk/example/`, com o `applicationId` de
// `scripts/.seed-state.json`):
//   curl -s http://localhost:8080/api/applications/<applicationId>/surveys/<surveyId>/results/export | grep -c <displayId>
import { profileById } from '../../pitaco/config';

// O slug que `scripts/seed.ts` dá à aplicação de exemplo.
const SEED_APPLICATION_SLUG = 'pitaco-example-app';
const TIMEOUT_MS = 5000;

export interface BackendCheckResult {
  readonly rows: number;
  readonly outcome: string | null;
}

async function get(url: string): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) throw new Error(`GET ${url} → ${response.status}`);
    return response;
  } finally {
    clearTimeout(timer);
  }
}

export async function checkBackend(displayId: string, surveyId: string): Promise<BackendCheckResult> {
  // O perfil direto aponta para `/api` do backend, que também serve a API administrativa.
  const base = profileById('direto').baseUrl.replace(/\/+$/, '');
  if (base === '') throw new Error('EXPO_PUBLIC_PITACO_BASE_URL vazio: rode npm run seed.');

  const applications = (await (await get(`${base}/applications`)).json()) as { items?: { id?: string; slug?: string }[] };
  const application = applications.items?.find((item) => item.slug === SEED_APPLICATION_SLUG);
  if (application?.id === undefined) throw new Error(`aplicação "${SEED_APPLICATION_SLUG}" não encontrada: rode npm run seed.`);

  const csv = await (await get(`${base}/applications/${application.id}/surveys/${surveyId}/results/export`)).text();
  const rows = csv.split(/\r?\n/).filter((line) => line.startsWith(`${displayId},`));
  return { rows: rows.length, outcome: rows[0]?.split(',')[3] ?? null };
}

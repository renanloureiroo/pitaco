/// <reference types="node" />
// Semeia o backend local para o exemplo: cria (ou reaproveita) a aplicação, emite (ou reaproveita)
// a chave, cria (ou reaproveita) a pesquisa com os seis tipos de pergunta, uma condição e o aviso
// de texto livre, define o disparo, publica, e escreve `.env` + `src/generated/seed-survey.json`.
//
// Roda direto com `node scripts/seed.ts` (Node 25 remove os tipos sozinho, sem dependência nova).
// Idempotente: rodar de novo localiza aplicação/pesquisa por nome e não duplica nada; a chave só é
// reemitida se a rastreada em `.seed-state.json` não existir mais ou não estiver no `.env`.
//
// Flags:
//   --base-url <url>     Endereço administrativo do backend (padrão http://localhost:8080/api)
//   --target ios-sim|android-emu|device   Para quem o app vai rodar (padrão ios-sim)
//   --lan-ip <ip>         Obrigatório com --target device: IP do backend na rede local
//   --proxy-port <porta>  Porta do proxy local (padrão 8787)

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const EXAMPLE_ROOT = join(HERE, '..');
const ENV_PATH = join(EXAMPLE_ROOT, '.env');
const STATE_PATH = join(HERE, '.seed-state.json');
const SEED_SURVEY_PATH = join(EXAMPLE_ROOT, 'src', 'generated', 'seed-survey.json');

const APPLICATION_NAME = 'Pitaco Example App';
const SURVEY_NAME = 'Pesquisa de exemplo';
const API_KEY_LABEL = 'sdk-example';
const TRIGGER_EVENT = 'pitaco.example.trigger';

interface Args {
  readonly baseUrl: string;
  readonly target: 'ios-sim' | 'android-emu' | 'device';
  readonly lanIp: string | null;
  readonly proxyPort: number;
}

function parseArgs(argv: readonly string[]): Args {
  let baseUrl = 'http://localhost:8080/api';
  let target: Args['target'] = 'ios-sim';
  let lanIp: string | null = null;
  let proxyPort = 8787;
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = (): string => {
      const value = argv[(i += 1)];
      if (value === undefined) throw new Error(`${arg} exige um valor`);
      return value;
    };
    if (arg === '--base-url') baseUrl = next();
    else if (arg === '--target') {
      const value = next();
      if (value !== 'ios-sim' && value !== 'android-emu' && value !== 'device') {
        throw new Error(`--target inválido: ${value} (use ios-sim, android-emu ou device)`);
      }
      target = value;
    } else if (arg === '--lan-ip') lanIp = next();
    else if (arg === '--proxy-port') proxyPort = Number(next());
    else throw new Error(`Argumento desconhecido: ${arg}`);
  }
  if (target === 'device' && !lanIp) {
    throw new Error('--target device exige --lan-ip <ip>');
  }
  return { baseUrl, target, lanIp, proxyPort };
}

function targetHost(args: Args): string {
  if (args.target === 'ios-sim') return 'localhost';
  if (args.target === 'android-emu') return '10.0.2.2';
  return args.lanIp as string;
}

// --- Cliente HTTP mínimo contra a API administrativa ---------------------------------------

class ApiError extends Error {
  readonly method: string;
  readonly path: string;
  readonly status: number;
  readonly body: unknown;

  constructor(method: string, path: string, status: number, body: unknown) {
    super(`${method} ${path} -> ${status}: ${JSON.stringify(body)}`);
    this.method = method;
    this.path = path;
    this.status = status;
    this.body = body;
  }
}

function makeClient(baseUrl: string) {
  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const text = await response.text();
    const parsed = text === '' ? null : JSON.parse(text);
    if (!response.ok) throw new ApiError(method, path, response.status, parsed);
    return parsed as T;
  }
  return {
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
    put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
    patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body ?? {}),
    del: <T>(path: string) => request<T>('DELETE', path),
  };
}

type Client = ReturnType<typeof makeClient>;

// --- Formas mínimas das respostas administrativas usadas aqui -------------------------------

interface PageResponse<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly totalPages: number;
}
interface ApplicationSummary {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly status: string;
}
interface SurveySummary {
  readonly id: string;
  readonly name: string;
  readonly state: string;
  readonly draftVersionNumber: number | null;
  readonly publishedVersionNumber: number | null;
}
interface ApiKeySummary {
  readonly id: string;
  readonly label: string;
  readonly status: 'active' | 'revoked';
  readonly prefix: string;
}
interface IssuedApiKey extends ApiKeySummary {
  readonly secret: string;
}
interface SurveyDetail extends SurveySummary {
  readonly content: {
    readonly source: 'draft' | 'published';
    readonly questions: readonly { readonly key: string; readonly type: string }[];
    readonly trigger?: { readonly eventName: string } | null;
  } | null;
}

async function findAllPages<T>(client: Client, path: string): Promise<T[]> {
  const items: T[] = [];
  let page = 0;
  for (;;) {
    const sep = path.includes('?') ? '&' : '?';
    const response = await client.get<PageResponse<T>>(`${path}${sep}page=${page}&size=100`);
    items.push(...response.items);
    page += 1;
    if (page >= response.totalPages) break;
  }
  return items;
}

// --- Passos do seed ---------------------------------------------------------------------------

async function ensureApplication(client: Client): Promise<ApplicationSummary> {
  const targetSlug = 'pitaco-example-app';
  const applications = await findAllPages<ApplicationSummary>(client, '/applications');
  const existing = applications.find((app) => app.slug === targetSlug);
  if (existing) {
    // Garante o descanso desligado mesmo que alguém o tenha ligado pelo painel.
    await client.patch(`/applications/${existing.id}`, { quietPeriodDays: null });
    console.log(`Aplicação reaproveitada: "${existing.name}" (${existing.id}), sem descanso.`);
    return existing;
  }
  const created = await client.post<{ id: string; slug: string }>('/applications', {
    name: APPLICATION_NAME,
    // Sem quietPeriodDays/retentionDays: sem descanso e sem descarte por prazo — o servidor não
    // impõe nada além do sorteio "já respondida" por respondente (limpo pelo painel de depuração
    // do exemplo, que zera a identidade local).
  });
  console.log(`Aplicação criada: "${APPLICATION_NAME}" (${created.id})`);
  return { id: created.id, slug: created.slug, name: APPLICATION_NAME, status: 'active' };
}

interface SeedState {
  applicationId?: string;
  apiKeyId?: string;
  apiKeyPrefix?: string;
  surveyId?: string;
}

function readState(): SeedState {
  if (!existsSync(STATE_PATH)) return {};
  try {
    return JSON.parse(readFileSync(STATE_PATH, 'utf8')) as SeedState;
  } catch {
    return {};
  }
}

function writeState(state: SeedState): void {
  writeFileSync(STATE_PATH, `${JSON.stringify(state, null, 2)}\n`);
}

async function ensureApiKey(
  client: Client,
  applicationId: string,
  state: SeedState,
  envHasKey: boolean,
): Promise<{ secret: string | null; reused: boolean }> {
  const activeKeys = await findAllPages<ApiKeySummary>(client, `/applications/${applicationId}/api-keys?status=active`);
  const labeled = activeKeys.filter((key) => key.label === API_KEY_LABEL);

  const trackedKey = labeled.find((key) => key.id === state.apiKeyId);
  if (trackedKey !== undefined && envHasKey) {
    console.log(`Chave de API reaproveitada (${trackedKey.prefix}), já presente no .env.`);
    return { secret: null, reused: true };
  }

  // Ou nunca emitimos, ou a rastreada sumiu/foi revogada, ou o .env está sem ela (não temos como
  // recuperar o segredo de uma chave já emitida) — qualquer chave ativa com o mesmo rótulo que
  // sobrar por aí é revogada antes de emitir a nova, para não acumular chave órfã.
  for (const key of labeled) {
    await client.del(`/applications/${applicationId}/api-keys/${key.id}`);
    console.log(`Chave de API anterior revogada (${key.prefix}).`);
  }
  const issued = await client.post<IssuedApiKey>(`/applications/${applicationId}/api-keys`, { label: API_KEY_LABEL });
  console.log(`Chave de API emitida (${issued.prefix}).`);
  return { secret: issued.secret, reused: false };
}

async function ensureSurvey(client: Client, applicationId: string): Promise<SurveyDetail> {
  const surveys = await findAllPages<SurveySummary>(client, `/applications/${applicationId}/surveys`);
  let survey = surveys.find((item) => item.name === SURVEY_NAME) ?? null;

  if (survey === null) {
    const created = await client.post<SurveySummary>(`/applications/${applicationId}/surveys`, {
      name: SURVEY_NAME,
    });
    console.log(`Pesquisa criada em rascunho: "${SURVEY_NAME}" (${created.id})`);
    survey = created;
  } else {
    console.log(`Pesquisa reaproveitada: "${survey.name}" (${survey.id})`);
  }

  let detail = await client.get<SurveyDetail>(`/applications/${applicationId}/surveys/${survey.id}`);
  const isFreshDraft = detail.content === null || (detail.content.source === 'draft' && detail.content.questions.length === 0);

  if (isFreshDraft) {
    await seedQuestionsTriggerAndPublish(client, applicationId, survey.id);
    detail = await client.get<SurveyDetail>(`/applications/${applicationId}/surveys/${survey.id}`);
  } else {
    console.log('Pesquisa já tinha conteúdo — pergunta, condição, disparo e publicação não repetidos.');
    // Idempotente e seguro repetir mesmo com conteúdo pronto: não abre versão nova.
    await client.patch(`/applications/${applicationId}/surveys/${survey.id}`, {
      ignoresQuietPeriod: true,
      responseQuota: null,
    });
  }
  return detail;
}

async function seedQuestionsTriggerAndPublish(client: Client, applicationId: string, surveyId: string): Promise<void> {
  const base = `/applications/${applicationId}/surveys/${surveyId}`;

  const nps = await client.post<{ key: string }>(`${base}/questions`, {
    statement: 'De 0 a 10, o quanto você recomendaria este app a um amigo ou colega?',
    type: 'nps',
    required: true,
    range: { min: 0, max: 10, minLabel: 'Nada provável', maxLabel: 'Extremamente provável' },
  });

  await client.post(`${base}/questions`, {
    statement: 'O quanto você está satisfeito com o app no geral?',
    type: 'scale',
    required: false,
    range: { min: 1, max: 5, minLabel: 'Nada satisfeito', maxLabel: 'Muito satisfeito' },
  });

  await client.post(`${base}/questions`, {
    statement: 'Como você avalia o suporte ao cliente?',
    type: 'rating',
    required: true,
    range: { min: 1, max: 5, minLabel: null, maxLabel: null },
  });

  // A condição: só aparece para quem deu nota 0-6 na NPS (detrator) — a pergunta que a briefing
  // pede que só apareça conforme a resposta de outra.
  await client.post(`${base}/questions`, {
    statement: 'O que mais pesou na sua nota?',
    type: 'single_choice',
    required: false,
    options: [
      { label: 'Preço', value: 'preco' },
      { label: 'Atendimento', value: 'atendimento' },
      { label: 'Estabilidade', value: 'estabilidade' },
    ],
    condition: { sourceKey: nps.key, operator: 'between', min: 0, max: 6 },
  });

  await client.post(`${base}/questions`, {
    statement: 'Quais recursos você mais usa?',
    type: 'multiple_choice',
    required: false,
    options: [
      { label: 'Busca', value: 'busca' },
      { label: 'Notificações', value: 'notificacoes' },
      { label: 'Relatórios', value: 'relatorios' },
    ],
  });

  await client.post(`${base}/questions`, {
    statement: 'Tem algum comentário ou sugestão para nós?',
    type: 'free_text',
    required: false,
  });

  // Aviso de texto livre (ligado por padrão, mas explícito aqui) e sem descanso desta pesquisa
  // em particular — o máximo de reexibição que o servidor permite configurar.
  await client.patch(base, { freeTextNoticeEnabled: true, ignoresQuietPeriod: true });

  const windowStart = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().replace(/\.\d+Z$/, 'Z');
  await client.put(`${base}/trigger`, {
    eventName: TRIGGER_EVENT,
    windowStart,
    // Sem windowEnd: janela indeterminada. samplingRate 1.0: amostragem de 100%.
    samplingRate: 1.0,
  });

  const impediments = await client.get<{ impediments: readonly unknown[] }>(`${base}/publication-impediments`);
  if (impediments.impediments.length > 0) {
    throw new Error(`Pesquisa com impedimentos de publicação: ${JSON.stringify(impediments.impediments)}`);
  }
  await client.post(`${base}/publication`, {});
  console.log('Pesquisa publicada (versão 1): seis tipos de pergunta, uma condição, disparo e amostragem de 100%.');
}

async function fetchDeliverableSchema(
  baseUrl: string,
  apiKeySecret: string,
  triggerEvent: string,
): Promise<{ surveyId: string; schema: unknown }> {
  // Um deviceId novo a cada seed evita que o sorteio "já respondida" (que o servidor sempre
  // aplica, mesmo com amostragem de 100% e sem descanso) esconda a pesquisa nesta consulta.
  const deviceId = crypto.randomUUID();
  const response = await fetch(`${baseUrl}/collect/eligibility`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Pitaco-Key': apiKeySecret,
      'X-Pitaco-Sdk-Version': '1.0.0',
    },
    body: JSON.stringify({ event: triggerEvent, respondent: { deviceId } }),
  });
  if (!response.ok) {
    throw new Error(`Elegibilidade falhou ao gerar o schema do exemplo: ${response.status}`);
  }
  const body = (await response.json()) as { survey: { surveyId: string } | null };
  if (body.survey === null) {
    throw new Error(
      'Elegibilidade devolveu "survey: null" logo após publicar — confira sampling/janela/descanso da pesquisa semeada.',
    );
  }
  return { surveyId: body.survey.surveyId, schema: body.survey };
}

function upsertEnvFile(path: string, updates: Readonly<Record<string, string>>): void {
  const lines: string[] = existsSync(path) ? readFileSync(path, 'utf8').split('\n') : [];
  const seen = new Set<string>();
  const result = lines.map((line) => {
    const key = /^([A-Za-z_][A-Za-z0-9_]*)=/.exec(line)?.[1];
    if (key !== undefined && Object.hasOwn(updates, key)) {
      seen.add(key);
      return `${key}=${updates[key]}`;
    }
    return line;
  });
  const missing = Object.entries(updates).filter(([key]) => !seen.has(key));
  if (missing.length > 0) {
    if (result.length > 0 && (result.at(-1) ?? '').trim() !== '') result.push('');
    for (const [key, value] of missing) result.push(`${key}=${value}`);
  }
  const content = result.join('\n').replace(/\n{3,}/g, '\n\n');
  writeFileSync(path, content.endsWith('\n') ? content : `${content}\n`);
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const client = makeClient(args.baseUrl);
  const state = readState();

  console.log(`Semeando o backend em ${args.baseUrl} (alvo: ${args.target}).`);

  const application = await ensureApplication(client);

  const envBefore = existsSync(ENV_PATH) ? readFileSync(ENV_PATH, 'utf8') : '';
  const envHasKey = /^EXPO_PUBLIC_PITACO_API_KEY=.+$/m.test(envBefore);
  const { secret, reused } = await ensureApiKey(client, application.id, state, envHasKey);

  const surveyDetail = await ensureSurvey(client, application.id);
  const triggerEvent = surveyDetail.content?.trigger?.eventName ?? TRIGGER_EVENT;

  // Precisamos do segredo para consultar a elegibilidade e gerar o schema do preview. Se a chave
  // foi reaproveitada (segredo não devolvido de novo por natureza), lemos o valor que já está no
  // `.env` — é exatamente o mesmo segredo que a chave ativa rastreada representa.
  let apiKeySecret = secret;
  if (apiKeySecret === null) {
    const match = /^EXPO_PUBLIC_PITACO_API_KEY=(.+)$/m.exec(envBefore);
    apiKeySecret = match?.[1] ?? null;
  }
  if (apiKeySecret === null) {
    throw new Error('Sem segredo de API disponível (nem emitido agora, nem no .env) — apague .seed-state.json e rode de novo.');
  }

  const { surveyId, schema } = await fetchDeliverableSchema(args.baseUrl, apiKeySecret, triggerEvent);

  const host = targetHost(args);
  const directBaseUrl = `http://${host}:8080/api`;
  const proxyBaseUrl = `http://${host}:${args.proxyPort}/pitaco`;

  const envUpdates: Record<string, string> = {
    EXPO_PUBLIC_PITACO_BASE_URL: directBaseUrl,
    EXPO_PUBLIC_PITACO_PROXY_BASE_URL: proxyBaseUrl,
  };
  if (!reused || secret !== null) {
    envUpdates.EXPO_PUBLIC_PITACO_API_KEY = apiKeySecret;
  }
  upsertEnvFile(ENV_PATH, envUpdates);

  mkdirSync(dirname(SEED_SURVEY_PATH), { recursive: true });
  writeFileSync(SEED_SURVEY_PATH, `${JSON.stringify({ triggerEvent, surveyId, schema }, null, 2)}\n`);

  let apiKeyId = state.apiKeyId;
  let apiKeyPrefix = state.apiKeyPrefix;
  if (secret !== null) {
    // Recupera o id da chave recém-emitida (a última ativa com o rótulo) para rastrear na
    // próxima execução.
    const activeKeys = await findAllPages<ApiKeySummary>(client, `/applications/${application.id}/api-keys?status=active`);
    const issuedKey = activeKeys.find((key) => key.label === API_KEY_LABEL);
    apiKeyId = issuedKey?.id;
    apiKeyPrefix = issuedKey?.prefix;
  }
  writeState({ applicationId: application.id, apiKeyId, apiKeyPrefix, surveyId });

  console.log('');
  console.log('Resumo:');
  console.log(`  Aplicação: ${application.name} (${application.id})`);
  console.log(`  Pesquisa: ${SURVEY_NAME} (${surveyId})`);
  console.log(`  Disparo: ${triggerEvent}`);
  console.log(`  .env atualizado em ${ENV_PATH}`);
  console.log(`  Schema do preview em ${SEED_SURVEY_PATH}`);
  console.log('');
  console.log('Reexibição: amostragem de 100%, sem descanso, sem cota de respostas, janela aberta.');
  console.log('O que o servidor ainda impõe (não configurável pela API administrativa):');
  console.log('  - pesquisa respondida ou dispensada não volta para o mesmo respondente;');
  console.log('  - depois de 3 abandonos (exibição sem desfecho por 30 min) ela também para;');
  console.log('  - limite de 120 requisições/min por origem e 1200/min por chave.');
  console.log(
    'Para ver a pesquisa de novo no mesmo aparelho, use "limpar storage e identidade" no painel de ' +
      'depuração do exemplo (troca o deviceId).',
  );
}

main().catch((error: unknown) => {
  console.error('Falha ao semear o backend:');
  console.error(error);
  process.exitCode = 1;
});

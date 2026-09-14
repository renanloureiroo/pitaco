/// <reference types="node" />
// Prova ponta a ponta sem app: faz, pelo caminho direto e pelo proxy local, o ciclo que o SDK faz —
// elegibilidade com o disparo do seed, abertura da exibição, lote de eventos e resposta — e confere
// os status do contrato. Cada caminho usa um `deviceId` novo e descartável, para não gastar a
// elegibilidade do dispositivo do simulador.
//
//   node scripts/smoke.ts [--direct-url http://localhost:8080/api] [--proxy-url http://localhost:8787/pitaco]
//                         [--only direct|proxy] [--check-429]
//
// Os endereços padrão são os do computador (não os do `.env`, que podem ser `10.0.2.2` ou o IP da
// rede local). A chave vem de EXPO_PUBLIC_PITACO_API_KEY no `.env` (ou PITACO_API_KEY). O proxy
// precisa estar de pé (`npm run proxy`). `--check-429` estoura o limite por origem pelo proxy para
// provar que o `Retry-After` chega intacto — bloqueia a origem local por até um minuto.

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const EXAMPLE_ROOT = join(HERE, '..');
const SDK_VERSION = '1.0.0';

interface Question {
  readonly key: string;
  readonly type: string;
  readonly options?: readonly { readonly value: string }[];
  readonly range?: { readonly min: number; readonly max: number } | null;
  readonly condition?: {
    readonly sourceKey: string;
    readonly operator: string;
    readonly values?: readonly string[];
    readonly min?: number | null;
    readonly max?: number | null;
  } | null;
}
interface DeliverableSurvey {
  readonly surveyId: string;
  readonly versionId: string;
  readonly questions: readonly Question[];
}
interface Reply {
  readonly status: number;
  readonly headers: Headers;
  readonly body: unknown;
}

function readFlag(name: string): string | undefined {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? undefined : process.argv[index + 1];
}

function readApiKey(): string {
  if (process.env.PITACO_API_KEY) return process.env.PITACO_API_KEY;
  const envPath = join(EXAMPLE_ROOT, '.env');
  const env = existsSync(envPath) ? readFileSync(envPath, 'utf8') : '';
  const key = /^EXPO_PUBLIC_PITACO_API_KEY=(.+)$/m.exec(env)?.[1]?.trim();
  if (!key) throw new Error('Sem chave: rode `npm run seed` antes (ele escreve EXPO_PUBLIC_PITACO_API_KEY no .env).');
  return key;
}

async function post(baseUrl: string, path: string, body: unknown, apiKey: string | null): Promise<Reply> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Pitaco-Sdk-Version': SDK_VERSION,
  };
  if (apiKey !== null) headers['X-Pitaco-Key'] = apiKey;
  const response = await fetch(`${baseUrl}${path}`, { method: 'POST', headers, body: JSON.stringify(body) });
  const text = await response.text();
  let parsed: unknown = text;
  try {
    parsed = text === '' ? null : JSON.parse(text);
  } catch {
    // Corpo que não é JSON fica como texto, e a conferência abaixo acusa.
  }
  return { status: response.status, headers: response.headers, body: parsed };
}

let failures = 0;

function check(label: string, ok: boolean, detail: string): void {
  if (!ok) failures += 1;
  console.log(`  ${ok ? 'ok     ' : 'FALHOU '} ${label} — ${detail}`);
}

// Resposta válida para cada pergunta, avaliando a condição como o SDK avalia (falha fechado).
function buildAnswers(questions: readonly Question[]) {
  const given = new Map<string, string | number | string[]>();
  return questions.map((question) => {
    const condition = question.condition;
    if (condition) {
      const source = given.get(condition.sourceKey);
      const scalar = Array.isArray(source) ? null : source;
      const values = condition.values ?? [];
      const satisfied =
        source !== undefined &&
        ((condition.operator === 'between' &&
          typeof scalar === 'number' &&
          scalar >= (condition.min ?? -Infinity) &&
          scalar <= (condition.max ?? Infinity)) ||
          (condition.operator === 'equals' && String(scalar) === values[0]) ||
          (condition.operator === 'not_equals' && String(scalar) !== values[0]) ||
          (condition.operator === 'in' && values.includes(String(scalar))));
      if (!satisfied) return { questionKey: question.key, status: 'NOT_APPLICABLE', value: null };
    }
    let value: string | number | string[] | null = null;
    if (question.type === 'NPS') value = 9;
    else if (question.type === 'RATING' || question.type === 'SCALE') value = question.range?.max ?? 5;
    else if (question.type === 'SINGLE_CHOICE') value = question.options?.[0]?.value ?? null;
    else if (question.type === 'MULTIPLE_CHOICE') value = question.options?.slice(0, 1).map((option) => option.value) ?? null;
    // FREE_TEXT fica em branco: o smoke nunca manda texto digitado.
    if (value === null) return { questionKey: question.key, status: 'SKIPPED', value: null };
    given.set(question.key, value);
    return { questionKey: question.key, status: 'ANSWERED', value };
  });
}

async function runPath(label: string, baseUrl: string, apiKey: string, triggerEvent: string, surveyId: string) {
  console.log(`\n${label}: ${baseUrl}`);
  const deviceId = crypto.randomUUID();
  const respondent = { deviceId };

  const eligibility = await post(baseUrl, '/collect/eligibility', { event: triggerEvent, respondent }, apiKey);
  const survey = (eligibility.body as { survey?: DeliverableSurvey | null } | null)?.survey ?? null;
  check('elegibilidade', eligibility.status === 200 && survey?.surveyId === surveyId, `${eligibility.status}, pesquisa ${survey?.surveyId ?? 'null'}`);
  if (survey === null) return;

  const displayId = crypto.randomUUID();
  const opened = await post(
    baseUrl,
    '/collect/displays',
    { displayId, surveyId: survey.surveyId, versionId: survey.versionId, respondent, sdkVersion: SDK_VERSION },
    apiKey,
  );
  check('abertura da exibição', opened.status === 201, `${opened.status}, displayId ${displayId}`);

  const first = survey.questions[0];
  if (first === undefined) {
    check('pesquisa com perguntas', false, 'a elegibilidade veio sem perguntas');
    return;
  }
  const now = new Date().toISOString();
  const events = [
    {
      catalogVersion: 1,
      type: 'survey_presented',
      displayId,
      seq: 1,
      occurredAt: now,
      elapsedMs: 0,
      data: { presentation: 'bottom-sheet', questionCount: survey.questions.length, renderableCount: survey.questions.length, triggerEvent },
    },
    { catalogVersion: 1, type: 'question_viewed', displayId, seq: 2, occurredAt: now, elapsedMs: 5, questionKey: first.key, data: { position: 1, visit: 1, from: 'start' } },
  ];
  const batch = await post(baseUrl, `/collect/displays/${displayId}/events`, { events }, apiKey);
  const accepted = (batch.body as { accepted?: number } | null)?.accepted;
  check('lote de eventos', batch.status === 202 && accepted === events.length, `${batch.status}, ${JSON.stringify(batch.body)}`);

  const submission = { outcome: 'COMPLETED', answers: buildAnswers(survey.questions) };
  const submitted = await post(baseUrl, `/collect/displays/${displayId}/submission`, submission, apiKey);
  check('resposta', submitted.status === 204, `${submitted.status}${submitted.status === 204 ? '' : `, ${JSON.stringify(submitted.body)}`}`);

  const resent = await post(baseUrl, `/collect/displays/${displayId}/submission`, submission, apiKey);
  check('reenvio idêntico da resposta', resent.status === 204, `${resent.status}`);

  const again = await post(baseUrl, '/collect/eligibility', { event: triggerEvent, respondent }, apiKey);
  const againSurvey = (again.body as { survey?: unknown } | null)?.survey ?? null;
  check('elegibilidade depois de responder', again.status === 200 && againSurvey === null, `${again.status}, survey ${againSurvey === null ? 'null' : 'presente'}`);

  const noKey = await post(baseUrl, '/collect/eligibility', { event: triggerEvent, respondent }, null);
  const code = (noKey.body as { code?: string } | null)?.code;
  check(
    'sem chave vira 401 do Pitaco, em JSON',
    noKey.status === 401 && code === 'api_key.missing' && (noKey.headers.get('content-type') ?? '').includes('json'),
    `${noKey.status}, ${code ?? 'sem code'}, ${noKey.headers.get('content-type') ?? 'sem content-type'}`,
  );
}

async function checkProxyBoundary(proxyUrl: string) {
  const origin = new URL(proxyUrl);
  const prefix = origin.pathname.replace(/\/+$/, '');
  const admin = await fetch(`${origin.origin}${prefix}/applications`, { method: 'GET' });
  check('proxy não repassa a superfície administrativa', admin.status === 404, `GET ${prefix}/applications -> ${admin.status}`);
  const docs = await fetch(`${origin.origin}${prefix}/v3/api-docs`, { method: 'GET' });
  check('proxy não repassa a documentação da API', docs.status === 404, `GET ${prefix}/v3/api-docs -> ${docs.status}`);
}

async function check429(proxyUrl: string, apiKey: string, triggerEvent: string) {
  console.log('\nLimite por origem pelo proxy (--check-429):');
  for (let attempt = 1; attempt <= 200; attempt += 1) {
    const reply = await post(proxyUrl, '/collect/eligibility', { event: `${triggerEvent}.smoke-429`, respondent: { deviceId: crypto.randomUUID() } }, apiKey);
    if (reply.status === 429) {
      const retryAfter = reply.headers.get('retry-after');
      check('429 com Retry-After intacto', retryAfter !== null, `na tentativa ${attempt}, Retry-After=${retryAfter ?? 'ausente'}`);
      return;
    }
  }
  check('429 com Retry-After intacto', false, 'nenhum 429 em 200 tentativas');
}

async function main() {
  const directUrl = readFlag('direct-url') ?? 'http://localhost:8080/api';
  const proxyUrl = readFlag('proxy-url') ?? 'http://localhost:8787/pitaco';
  const only = readFlag('only');
  const apiKey = readApiKey();
  const seed = JSON.parse(readFileSync(join(EXAMPLE_ROOT, 'src', 'generated', 'seed-survey.json'), 'utf8')) as {
    triggerEvent: string;
    surveyId: string;
  };
  console.log(`Smoke do Pitaco — disparo "${seed.triggerEvent}", pesquisa ${seed.surveyId}`);

  if (only !== 'proxy') await runPath('Caminho direto', directUrl, apiKey, seed.triggerEvent, seed.surveyId);
  if (only !== 'direct') {
    await runPath('Pelo proxy', proxyUrl, apiKey, seed.triggerEvent, seed.surveyId);
    await checkProxyBoundary(proxyUrl);
    if (process.argv.includes('--check-429')) await check429(proxyUrl, apiKey, seed.triggerEvent);
  }

  console.log(failures === 0 ? '\nTudo certo.' : `\n${failures} verificação(ões) falharam.`);
  if (failures > 0) process.exitCode = 1;
}

main().catch((error: unknown) => {
  console.error('Falha no smoke (o backend e o proxy estão de pé?):');
  console.error(error);
  process.exitCode = 1;
});

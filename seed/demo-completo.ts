/// <reference types="node" />
// Seed "demo-completo": popula um backend Pitaco com dados fictícios suficientes para demonstrar
// tudo o que o painel faz — aplicações, chaves, pesquisas em todos os estados, versões, disparo,
// segmentação, cota, milhares de exibições com respostas, eventos de interação, supressões e erros
// de SDK — espalhados pelos últimos 90 dias.
//
// Como funciona:
//   1. Monta o catálogo pela API administrativa (`/applications`, `/surveys`, …).
//   2. Simula respondentes pelas MESMAS rotas que o SDK usa (`/collect/*`), então cada resposta
//      passa pela validação real do backend: elegibilidade, abertura, eventos e envio.
//   3. A API grava tudo com a hora do servidor. Para os gráficos terem história, o seed escreve um
//      SQL que move cada exibição (e o que pende dela) para a data simulada e o aplica com `psql`
//      quando `--database-url` é passado. Sem ele, o SQL fica salvo para aplicar à mão.
//
// Uso:
//   node seed/demo-completo.ts                                   # backend local, sem reescrever datas
//   node seed/demo-completo.ts --database-url postgres://myuser:secret@localhost:5432/mydatabase
//   node seed/demo-completo.ts --scale 0.3                        # versão rápida (~30% do volume)
//   node seed/demo-completo.ts --reset --database-url …           # apaga a demo anterior antes
//   node seed/demo-completo.ts --reset                            # só gera seed/out/demo-completo-reset.sql
//
// Requer Node 22.18+ (remove os tipos sozinho) e, para as datas, o `psql` no PATH.

import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(HERE, 'out');
const DAY = 24 * 60 * 60 * 1000;
const SDK_VERSIONS = ['1.0.0', '1.1.0', '1.2.0'] as const;

// --- Argumentos ------------------------------------------------------------------------------

interface Args {
  baseUrl: string;
  databaseUrl: string | null;
  scale: number;
  reset: boolean;
  concurrency: number;
  seed: number;
}

function parseArgs(argv: readonly string[]): Args {
  const args: Args = {
    baseUrl: process.env.PITACO_API_URL ?? 'http://localhost:8080/api',
    databaseUrl: process.env.DATABASE_URL ?? null,
    scale: 1,
    reset: false,
    concurrency: 8,
    seed: 20261001,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = (): string => {
      const value = argv[(i += 1)];
      if (value === undefined) throw new Error(`${arg} exige um valor`);
      return value;
    };
    if (arg === '--base-url') args.baseUrl = next().replace(/\/+$/, '');
    else if (arg === '--database-url') args.databaseUrl = next();
    else if (arg === '--scale') args.scale = Number(next());
    else if (arg === '--reset') args.reset = true;
    else if (arg === '--concurrency') args.concurrency = Number(next());
    else if (arg === '--seed') args.seed = Number(next());
    else if (arg === '--help' || arg === '-h') {
      console.log('Uso: node seed/demo-completo.ts [--base-url URL] [--database-url URL] [--scale N] [--reset]');
      process.exit(0);
    } else throw new Error(`Argumento desconhecido: ${arg}`);
  }
  if (!(args.scale > 0 && args.scale <= 5)) throw new Error('--scale deve estar entre 0 e 5');
  return args;
}

// --- Aleatoriedade determinística --------------------------------------------------------------

let rngState = 1;
function rand(): number {
  // mulberry32: a mesma semente produz a mesma demo.
  rngState = (rngState + 0x6d2b79f5) | 0;
  let t = rngState;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const chance = (p: number): boolean => rand() < p;
const int = (min: number, max: number): number => min + Math.floor(rand() * (max - min + 1));
function pick<T>(items: readonly T[]): T {
  return items[Math.floor(rand() * items.length)] as T;
}
function weighted<T>(entries: readonly (readonly [T, number])[]): T {
  const total = entries.reduce((sum, [, w]) => sum + w, 0);
  let roll = rand() * total;
  for (const [value, weight] of entries) {
    roll -= weight;
    if (roll <= 0) return value;
  }
  return entries[entries.length - 1]![0];
}
const clamp = (v: number, min: number, max: number): number => Math.max(min, Math.min(max, v));
function normal(mean: number, sd: number): number {
  const u = 1 - rand();
  const v = rand();
  return mean + sd * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

// --- HTTP ------------------------------------------------------------------------------------

class ApiError extends Error {
  readonly path: string;
  readonly status: number;
  readonly body: unknown;
  constructor(method: string, path: string, status: number, body: unknown) {
    super(`${method} ${path} -> ${status}: ${JSON.stringify(body)}`);
    this.path = path;
    this.status = status;
    this.body = body;
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function http<T>(
  baseUrl: string,
  method: string,
  path: string,
  body?: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  for (let attempt = 0; ; attempt += 1) {
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, {
        method,
        headers: { 'Content-Type': 'application/json', ...headers },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
    } catch (error) {
      if (attempt < 5) {
        await sleep(500 * 2 ** attempt);
        continue;
      }
      throw error;
    }
    if (response.status === 429 && attempt < 30) {
      const retryAfter = Number(response.headers.get('Retry-After') ?? '5');
      await sleep((Number.isFinite(retryAfter) ? retryAfter : 5) * 1000 + int(0, 500));
      continue;
    }
    const text = await response.text();
    const parsed = text === '' ? null : JSON.parse(text);
    if (!response.ok) throw new ApiError(method, path, response.status, parsed);
    return parsed as T;
  }
}

function admin(baseUrl: string) {
  return {
    get: <T>(p: string) => http<T>(baseUrl, 'GET', p),
    post: <T>(p: string, b: unknown = {}) => http<T>(baseUrl, 'POST', p, b),
    put: <T>(p: string, b: unknown = {}) => http<T>(baseUrl, 'PUT', p, b),
    patch: <T>(p: string, b: unknown = {}) => http<T>(baseUrl, 'PATCH', p, b),
    del: <T>(p: string) => http<T>(baseUrl, 'DELETE', p),
  };
}
type Admin = ReturnType<typeof admin>;

// --- Formas mínimas ------------------------------------------------------------------------------

interface PageResponse<T> {
  items: T[];
  totalPages: number;
}
interface Application {
  id: string;
  slug: string;
  name: string;
}
interface Survey {
  id: string;
  name: string;
  state: string;
}
interface DeliverableQuestion {
  key: string;
  position: number;
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RATING' | 'SCALE' | 'NPS' | 'FREE_TEXT';
  required: boolean;
  options?: { label: string; value: string }[];
  range?: { min: number; max: number };
  condition?: { sourceKey: string; operator: string; values?: string[]; min?: number; max?: number } | null;
}
interface Deliverable {
  surveyId: string;
  versionId: string;
  versionNumber: number;
  questions: DeliverableQuestion[];
}

async function findAll<T>(api: Admin, path: string): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page += 1) {
    const sep = path.includes('?') ? '&' : '?';
    const response = await api.get<PageResponse<T>>(`${path}${sep}page=${page}&size=100`);
    items.push(...response.items);
    if (page + 1 >= response.totalPages) return items;
  }
}

// --- Público simulado ----------------------------------------------------------------------------

type Plan = 'free' | 'pro' | 'premium';
type Platform = 'ios' | 'android';

interface Persona {
  reference: string | null;
  deviceId: string;
  ip: string;
  sdkVersion: string;
  attributes: Record<string, string>;
  plan: Plan;
  platform: Platform;
  /** Humor de base: desloca as notas para cima ou para baixo. */
  mood: number;
}

function makePersona(): Persona {
  const plan = weighted<Plan>([
    ['free', 55],
    ['pro', 30],
    ['premium', 15],
  ]);
  const platform = weighted<Platform>([
    ['ios', 45],
    ['android', 55],
  ]);
  const appVersion = weighted([
    ['3.2.0', 15],
    ['3.3.0', 35],
    ['3.4.1', 50],
  ] as const);
  const attributes: Record<string, string> = {
    plano: plan,
    plataforma: platform,
    pais: weighted([
      ['BR', 78],
      ['PT', 12],
      ['AR', 10],
    ] as const),
    versao_app: appVersion,
    canal: weighted([
      ['organico', 60],
      ['pago', 25],
      ['indicacao', 15],
    ] as const),
  };
  // Parte do público chega sem um atributo, para o recorte "sem o atributo" ter o que mostrar.
  if (chance(0.06)) delete attributes.canal;
  const sdkVersion = appVersion === '3.2.0' ? '1.0.0' : appVersion === '3.3.0' ? weighted([['1.0.0', 30], ['1.1.0', 70]] as const) : '1.2.0';
  return {
    reference: chance(0.7) ? `u-${randomUUID().slice(0, 8)}` : null,
    deviceId: randomUUID(),
    ip: `100.${int(64, 127)}.${int(0, 255)}.${int(1, 254)}`,
    sdkVersion,
    attributes,
    plan,
    platform,
    mood: (plan === 'premium' ? 1.2 : plan === 'pro' ? 0.4 : -0.3) + (platform === 'android' ? -0.4 : 0.2) + normal(0, 1),
  };
}

// --- Catálogo de pesquisas -----------------------------------------------------------------------

type Content =
  | { template: 'nps' | 'csat' | 'ces'; extra?: QuestionSpec[] }
  | { questions: QuestionSpec[] };

interface QuestionSpec {
  ref: string;
  statement: string;
  type: 'single_choice' | 'multiple_choice' | 'rating' | 'scale' | 'nps' | 'free_text';
  required?: boolean;
  options?: [string, string][];
  range?: { min: number; max: number; minLabel?: string | null; maxLabel?: string | null };
  condition?: { sourceRef: string; operator: 'between' | 'equals' | 'in' | 'not_equals'; min?: number; max?: number; values?: string[] };
}

type Fate = 'published' | 'paused' | 'ended' | 'draft' | 'quota';

interface SurveySpec {
  name: string;
  event: string;
  content: Content;
  displays: number;
  fate: Fate;
  sampling?: number;
  priority?: number;
  quota?: number;
  rules?: { attribute: string; operation: 'equals' | 'not_equals' | 'present' | 'absent'; value?: string }[];
  /** Publica a versão 2 (com o resumo da mudança) quando esta fração das exibições já ocorreu. */
  v2?: { at: number; changeKind: 'cosmetic' | 'semantic'; summary: string; apply: (api: Admin, base: string, questions: QuestionRef[]) => Promise<void> };
  /** Janela de dias atrás em que as exibições acontecem. */
  span: [number, number];
  completion: number;
  dismissal: number;
}

interface QuestionRef {
  id: string;
  key: string;
  type: string;
  statement: string;
}

const NPS_FOLLOW_UP: QuestionSpec[] = [
  {
    ref: 'motivo',
    statement: 'O que mais pesou na sua nota?',
    type: 'single_choice',
    options: [
      ['Preço', 'preco'],
      ['Prazo de entrega', 'entrega'],
      ['Atendimento', 'atendimento'],
      ['Estabilidade do app', 'estabilidade'],
      ['Variedade de produtos', 'variedade'],
    ],
    condition: { sourceRef: '$template', operator: 'between', min: 0, max: 8 },
  },
  {
    ref: 'elogio',
    statement: 'O que você mais gosta no app?',
    type: 'multiple_choice',
    options: [
      ['Ofertas', 'ofertas'],
      ['Rastreamento do pedido', 'rastreamento'],
      ['Pagamento com Pix', 'pix'],
      ['Programa de pontos', 'pontos'],
    ],
    condition: { sourceRef: '$template', operator: 'between', min: 9, max: 10 },
  },
  { ref: 'comentario', statement: 'Quer contar mais alguma coisa para a gente?', type: 'free_text' },
];

function catalog(scale: number): SurveySpec[] {
  const n = (value: number) => Math.max(12, Math.round(value * scale));
  return [
    {
      name: 'NPS trimestral',
      event: 'order.delivered',
      content: { template: 'nps', extra: NPS_FOLLOW_UP },
      displays: n(1500),
      fate: 'published',
      priority: 10,
      span: [88, 0],
      completion: 0.56,
      dismissal: 0.22,
      v2: {
        at: 0.55,
        changeKind: 'cosmetic',
        summary: 'Enunciado do comentário mais curto e convidativo.',
        apply: async (api, base, questions) => {
          const comment = questions.find((q) => q.type === 'free_text');
          if (comment === undefined) return;
          await api.put(`${base}/questions/${comment.id}`, {
            statement: 'Conta pra gente: o que faria você dar 10?',
            type: 'free_text',
            required: false,
          });
        },
      },
    },
    {
      name: 'Checkout — experiência de compra',
      event: 'checkout.completed',
      content: {
        questions: [
          {
            ref: 'facilidade',
            statement: 'Quão fácil foi concluir sua compra?',
            type: 'scale',
            required: true,
            range: { min: 1, max: 5, minLabel: 'Muito difícil', maxLabel: 'Muito fácil' },
          },
          {
            ref: 'pagamento',
            statement: 'Qual forma de pagamento você usou?',
            type: 'single_choice',
            required: true,
            options: [
              ['Pix', 'pix'],
              ['Cartão de crédito', 'credito'],
              ['Cartão de débito', 'debito'],
              ['Boleto', 'boleto'],
            ],
          },
          {
            ref: 'atrito',
            statement: 'Algo atrapalhou no caminho?',
            type: 'multiple_choice',
            options: [
              ['Frete caro', 'frete'],
              ['Cupom não funcionou', 'cupom'],
              ['Muitas etapas', 'etapas'],
              ['App travou', 'travou'],
              ['Nada atrapalhou', 'nada'],
            ],
          },
          {
            ref: 'estrelas',
            statement: 'Que nota você dá para o novo carrinho?',
            type: 'rating',
            required: true,
            range: { min: 1, max: 5 },
          },
          { ref: 'sugestao', statement: 'Como podemos deixar o checkout melhor?', type: 'free_text' },
        ],
      },
      displays: n(950),
      fate: 'published',
      sampling: 0.6,
      span: [75, 0],
      completion: 0.48,
      dismissal: 0.24,
      rules: [{ attribute: 'plano', operation: 'present' }],
    },
    {
      name: 'CSAT do atendimento',
      event: 'support.ticket_closed',
      content: {
        template: 'csat',
        extra: [
          {
            ref: 'canal_suporte',
            statement: 'Por qual canal você foi atendido?',
            type: 'single_choice',
            options: [
              ['Chat no app', 'chat'],
              ['WhatsApp', 'whatsapp'],
              ['E-mail', 'email'],
              ['Telefone', 'telefone'],
            ],
          },
          { ref: 'resolveu', statement: 'Seu problema foi resolvido?', type: 'single_choice', required: true, options: [['Sim', 'sim'], ['Em parte', 'parcial'], ['Não', 'nao']] },
        ],
      },
      displays: n(650),
      fate: 'published',
      span: [80, 0],
      completion: 0.62,
      dismissal: 0.2,
    },
    {
      name: 'Onboarding (CES)',
      event: 'onboarding.completed',
      content: { template: 'ces', extra: [{ ref: 'travou', statement: 'Em que etapa você pensou em desistir?', type: 'free_text' }] },
      displays: n(420),
      fate: 'published',
      span: [60, 0],
      completion: 0.58,
      dismissal: 0.18,
      rules: [{ attribute: 'plataforma', operation: 'equals', value: 'android' }],
    },
    {
      name: 'Próxima feature',
      event: 'changelog.viewed',
      content: {
        questions: [
          {
            ref: 'feature',
            statement: 'Qual destes recursos você quer ver primeiro no app?',
            type: 'single_choice',
            required: true,
            options: [
              ['Modo escuro', 'modo_escuro'],
              ['Lista de desejos compartilhada', 'lista_desejos'],
              ['Alerta de preço', 'alerta_preco'],
              ['Widget na tela inicial', 'widget'],
            ],
          },
          { ref: 'porque', statement: 'Por que esse recurso é o mais importante para você?', type: 'free_text' },
        ],
      },
      displays: n(700),
      fate: 'quota',
      quota: n(300),
      span: [45, 12],
      completion: 0.6,
      dismissal: 0.25,
    },
    {
      name: 'Feedback do novo app',
      event: 'app.updated',
      content: {
        questions: [
          { ref: 'nota_app', statement: 'De 1 a 5, como você avalia a nova versão do app?', type: 'rating', required: true, range: { min: 1, max: 5 } },
          {
            ref: 'mudou',
            statement: 'O que você notou de diferente?',
            type: 'multiple_choice',
            options: [
              ['Visual novo', 'visual'],
              ['Mais rápido', 'rapido'],
              ['Mais lento', 'lento'],
              ['Menu reorganizado', 'menu'],
            ],
          },
          { ref: 'bug', statement: 'Encontrou algum problema?', type: 'free_text' },
        ],
      },
      displays: n(380),
      fate: 'paused',
      span: [30, 6],
      completion: 0.52,
      dismissal: 0.28,
    },
    {
      name: 'Teste de preço — frete fixo (beta)',
      event: 'cart.viewed',
      content: {
        questions: [
          { ref: 'justo', statement: 'Um frete fixo de R$ 9,90 parece justo?', type: 'scale', required: true, range: { min: 1, max: 5, minLabel: 'Nada justo', maxLabel: 'Muito justo' } },
          { ref: 'assinaria', statement: 'Você assinaria frete grátis por R$ 14,90/mês?', type: 'single_choice', required: true, options: [['Sim', 'sim'], ['Talvez', 'talvez'], ['Não', 'nao']] },
        ],
      },
      displays: n(260),
      fate: 'ended',
      sampling: 0.25,
      span: [70, 40],
      completion: 0.55,
      dismissal: 0.3,
      rules: [{ attribute: 'pais', operation: 'equals', value: 'BR' }],
    },
    {
      name: 'Pesquisa de cancelamento',
      event: 'subscription.cancel_started',
      content: {
        questions: [
          {
            ref: 'motivo_cancel',
            statement: 'Por que você está cancelando?',
            type: 'single_choice',
            required: true,
            options: [
              ['Ficou caro', 'caro'],
              ['Não uso o suficiente', 'pouco_uso'],
              ['Encontrei outra opção', 'concorrente'],
              ['Outro motivo', 'outro'],
            ],
          },
          { ref: 'voltaria', statement: 'O que faria você ficar?', type: 'free_text' },
        ],
      },
      displays: 0,
      fate: 'draft',
      span: [0, 0],
      completion: 0,
      dismissal: 0,
    },
  ];
}

// --- Respostas plausíveis ------------------------------------------------------------------------

const OPEN_TEXTS: Record<string, string[]> = {
  positive: [
    'Entrega chegou antes do prazo, adorei!',
    'O app ficou muito mais rápido nessa versão.',
    'Pagamento com Pix é instantâneo, muito prático.',
    'Atendimento resolveu em minutos, nota 10.',
    'Gosto muito das ofertas relâmpago.',
    'Rastreamento em tempo real é ótimo.',
    'Interface limpa e fácil de achar as coisas.',
    'Programa de pontos vale muito a pena.',
  ],
  neutral: [
    'Funciona bem, mas poderia ter mais filtros na busca.',
    'Seria bom ter modo escuro.',
    'O frete às vezes fica caro para pedidos pequenos.',
    'Demorei um pouco para achar o cupom.',
    'Ok no geral, nada demais.',
    'Queria poder salvar mais de um endereço fácil.',
  ],
  negative: [
    'O app fecha sozinho no meu celular quando abro o carrinho.',
    'Pedido atrasou uma semana e ninguém avisou.',
    'Frete muito caro, desisti de duas compras.',
    'Cupom não funcionou no checkout.',
    'Atendimento demorou dois dias para responder.',
    'Muitas etapas para finalizar a compra.',
    'O app ficou lento depois da atualização.',
    'Cobraram duas vezes no cartão.',
  ],
};

function scoreFor(persona: Persona, min: number, max: number): number {
  const span = max - min;
  const center = min + span * clamp(0.84 + persona.mood * 0.09, 0.05, 1);
  return Math.round(clamp(normal(center, span * 0.17), min, max));
}

function sentimentOf(answers: Map<string, unknown>, questions: DeliverableQuestion[]): 'positive' | 'neutral' | 'negative' {
  for (const q of questions) {
    if ((q.type === 'NPS' || q.type === 'SCALE' || q.type === 'RATING') && typeof answers.get(q.key) === 'number') {
      const range = q.range ?? { min: 0, max: 10 };
      const share = ((answers.get(q.key) as number) - range.min) / (range.max - range.min);
      return share >= 0.8 ? 'positive' : share >= 0.55 ? 'neutral' : 'negative';
    }
  }
  return 'neutral';
}

function choiceFor(q: DeliverableQuestion, persona: Persona, answers: Map<string, unknown>, questions: DeliverableQuestion[]): string {
  const options = q.options ?? [];
  const values = options.map((o) => o.value);
  const sentiment = sentimentOf(answers, questions);
  // Correlações que a análise por recorte deve encontrar.
  const bias: Record<string, number> = {};
  if (persona.platform === 'android') Object.assign(bias, { estabilidade: 3, travou: 3, lento: 2.5 });
  if (persona.plan === 'free') Object.assign(bias, { preco: 2.2, frete: 2.5, caro: 2.5 });
  if (persona.plan === 'premium') Object.assign(bias, { pontos: 2.5, variedade: 1.6, rapido: 1.8 });
  if (sentiment === 'negative') Object.assign(bias, { entrega: 1.8, atendimento: 1.5, nao: 3, cupom: 1.6 });
  if (sentiment === 'positive') Object.assign(bias, { sim: 3, nada: 2.5, ofertas: 1.4, pix: 1.6, visual: 1.4 });
  const base: Record<string, number> = { pix: 4, credito: 3.2, debito: 1.2, boleto: 0.6, modo_escuro: 3, alerta_preco: 2.4, lista_desejos: 1.4, widget: 1, chat: 3.5, whatsapp: 2.5, email: 1, telefone: 0.6 };
  return weighted(values.map((v) => [v, (base[v] ?? 1) * (bias[v] ?? 1)] as const));
}

function conditionHolds(q: DeliverableQuestion, answers: Map<string, unknown>): boolean {
  const c = q.condition;
  if (c === undefined || c === null) return true;
  const source = answers.get(c.sourceKey);
  if (source === undefined) return false;
  const values = c.values ?? [];
  switch (c.operator) {
    case 'between':
      return typeof source === 'number' && source >= (c.min ?? -Infinity) && source <= (c.max ?? Infinity);
    case 'equals':
      return Array.isArray(source) ? source.includes(values[0]) : String(source) === values[0];
    case 'not_equals':
      return Array.isArray(source) ? !source.includes(values[0]) : String(source) !== values[0];
    case 'in':
      return Array.isArray(source) ? source.some((v) => values.includes(v)) : values.includes(String(source));
    default:
      return true;
  }
}

// --- Uma exibição de ponta a ponta ---------------------------------------------------------------

type Outcome = 'completed' | 'dismissed' | 'abandoned';

interface DisplayPlan {
  displayId: string;
  at: number; // epoch ms simulado
  outcome: Outcome;
}

interface Collector {
  key: string;
  baseUrl: string;
}

/** Falhas 5xx da coleta que sobraram depois das novas tentativas, por rota. */
const serverFailures = new Map<string, number>();

// As rotas de coleta são idempotentes (displayId, seq), então repetir depois de um 5xx é seguro.
async function collect<T>(c: Collector, persona: Persona, path: string, body: unknown): Promise<T> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await http<T>(c.baseUrl, 'POST', path, body, {
        'X-Pitaco-Key': c.key,
        'X-Pitaco-Sdk-Version': persona.sdkVersion,
        'CF-Connecting-IP': persona.ip,
      });
    } catch (error) {
      if (!(error instanceof ApiError) || error.status < 500 || attempt >= 3) throw error;
      await sleep(1000 * 2 ** attempt);
    }
  }
}

function recordServerFailure(path: string, error: ApiError): void {
  const route = path.replace(/[0-9a-f-]{36}/g, '{id}');
  const count = (serverFailures.get(route) ?? 0) + 1;
  serverFailures.set(route, count);
  if (count <= 3) {
    const traceId = (error.body as { traceId?: string } | null)?.traceId ?? '-';
    console.warn(`    ${error.status} em ${route} (traceId ${traceId}) — exibição pulada, o seed segue.`);
  }
}

interface Event {
  catalogVersion: 1;
  type: string;
  displayId: string;
  seq: number;
  occurredAt: string;
  elapsedMs: number;
  questionKey?: string;
  data: Record<string, unknown>;
}

async function runDisplay(
  c: Collector,
  spec: SurveySpec,
  persona: Persona,
  at: number,
  plannedOutcome: Outcome,
): Promise<DisplayPlan | null> {
  const respondent = { ...(persona.reference ? { reference: persona.reference } : {}), deviceId: persona.deviceId };
  const eligibility = await collect<{ survey?: Deliverable | null }>(c, persona, '/collect/eligibility', {
    event: spec.event,
    respondent,
    attributes: persona.attributes,
  });
  const survey = eligibility.survey ?? null;
  if (survey === null) return null;

  const displayId = randomUUID();
  await collect(c, persona, '/collect/displays', {
    displayId,
    surveyId: survey.surveyId,
    versionId: survey.versionId,
    respondent,
    attributes: persona.attributes,
    sdkVersion: persona.sdkVersion,
  });

  const questions = [...survey.questions].sort((a, b) => a.position - b.position);
  const events: Event[] = [];
  let seq = 0;
  let elapsed = 0;
  const push = (type: string, data: Record<string, unknown>, questionKey?: string) => {
    seq += 1;
    events.push({
      catalogVersion: 1,
      type,
      displayId,
      seq,
      occurredAt: new Date(at + elapsed).toISOString(),
      elapsedMs: elapsed,
      ...(questionKey !== undefined ? { questionKey } : {}),
      data,
    });
  };
  push('survey_presented', {
    presentation: weighted([
      ['bottom-sheet', 70],
      ['modal', 25],
      ['inline', 5],
    ] as const),
    questionCount: questions.length,
    renderableCount: questions.length,
    triggerEvent: spec.event,
  });

  const answers = new Map<string, unknown>();
  const statuses = new Map<string, 'ANSWERED' | 'SKIPPED' | 'NOT_APPLICABLE'>();
  // Onde a pessoa para, quando não conclui: mais cedo é mais provável.
  const stopAt =
    plannedOutcome === 'completed'
      ? Infinity
      : plannedOutcome === 'dismissed' && chance(0.7)
        ? 0
        : Math.min(questions.length - 1, Math.floor(Math.abs(normal(0, questions.length * 0.45))));
  let visible = 0;
  let stopped = false;
  let position = 0;

  for (let i = 0; i < questions.length; i += 1) {
    const q = questions[i]!;
    if (!conditionHolds(q, answers)) {
      statuses.set(q.key, 'NOT_APPLICABLE');
      push('question_not_applicable', { sourceKey: q.condition?.sourceKey ?? '' }, q.key);
      continue;
    }
    position += 1;
    push('question_viewed', { position, visit: 1, from: visible === 0 ? 'start' : 'next' }, q.key);
    if (visible === stopAt) {
      // Para aqui: dispensa ou abandono nesta pergunta.
      elapsed += int(1500, 9000);
      push('question_left', { visit: 1, to: 'dismiss', durationMs: 4000, activeMs: 3500, answered: false }, q.key);
      stopped = true;
      break;
    }
    visible += 1;
    const isText = q.type === 'FREE_TEXT';
    const skip = !q.required && (isText ? chance(0.55) : chance(0.12));
    let think = isText ? int(9000, 60000) : q.type === 'MULTIPLE_CHOICE' ? int(3500, 16000) : int(1500, 9000);
    if (persona.platform === 'android' && q.type === 'MULTIPLE_CHOICE') think += int(1000, 6000);

    if (q.required && chance(0.05)) push('validation_blocked', { reason: 'required_missing' }, q.key);

    if (skip) {
      statuses.set(q.key, 'SKIPPED');
      push('question_skipped', {}, q.key);
    } else {
      let value: unknown;
      switch (q.type) {
        case 'NPS':
        case 'SCALE':
        case 'RATING': {
          const range = q.range ?? { min: 0, max: 10 };
          value = scoreFor(persona, range.min, range.max);
          if (chance(0.09)) {
            const first = clamp((value as number) + pick([-2, -1, 1, 2]), range.min, range.max);
            push('answer_selected', { value: first }, q.key);
            push('answer_changed', { from: first, to: value }, q.key);
          } else push('answer_selected', { value }, q.key);
          break;
        }
        case 'SINGLE_CHOICE': {
          value = choiceFor(q, persona, answers, questions);
          if (chance(0.11)) {
            const first = pick((q.options ?? []).map((o) => o.value).filter((v) => v !== value));
            if (first !== undefined) {
              push('answer_selected', { value: first }, q.key);
              push('answer_changed', { from: first, to: value }, q.key);
            } else push('answer_selected', { value }, q.key);
          } else push('answer_selected', { value }, q.key);
          break;
        }
        case 'MULTIPLE_CHOICE': {
          const chosen = new Set<string>();
          const count = weighted([
            [1, 50],
            [2, 35],
            [3, 15],
          ] as const);
          for (let k = 0; k < count * 2 && chosen.size < count; k += 1) chosen.add(choiceFor(q, persona, answers, questions));
          if (chosen.has('nada') && chosen.size > 1) chosen.delete('nada');
          value = [...chosen];
          for (const v of chosen) push('answer_selected', { value: v }, q.key);
          if (chance(0.15)) {
            const extra = pick((q.options ?? []).map((o) => o.value).filter((v) => !chosen.has(v)));
            if (extra !== undefined) {
              push('answer_selected', { value: extra }, q.key);
              push('answer_deselected', { value: extra }, q.key);
            }
          }
          break;
        }
        case 'FREE_TEXT': {
          const text = pick(OPEN_TEXTS[sentimentOf(answers, questions)]!);
          value = text;
          push('text_focused', {}, q.key);
          push('text_edited', { length: text.length }, q.key);
          push('text_blurred', { length: text.length }, q.key);
          break;
        }
      }
      answers.set(q.key, value);
      statuses.set(q.key, 'ANSWERED');
    }
    elapsed += think;
    const last = i === questions.length - 1;
    push(
      'question_left',
      { visit: 1, to: last ? 'complete' : 'next', durationMs: think + int(0, 1500), activeMs: think, answered: !skip },
      q.key,
    );
    const nextQ = questions.slice(i + 1).find((n) => conditionHolds(n, answers));
    if (nextQ !== undefined) push('navigated_next', { toKey: nextQ.key }, q.key);
    // Algumas pessoas voltam para revisar a pergunta anterior.
    if (nextQ !== undefined && chance(0.04)) {
      push('navigated_back', { toKey: q.key }, nextQ.key);
      push('question_viewed', { position, visit: 2, from: 'back' }, q.key);
      elapsed += int(800, 3000);
      push('question_left', { visit: 2, to: 'next', durationMs: 1500, activeMs: 1200, answered: !skip }, q.key);
    }
  }

  if (chance(0.06)) {
    push('survey_backgrounded', {});
    push('survey_foregrounded', { backgroundMs: int(2000, 120000) });
  }

  const answeredCount = [...statuses.values()].filter((s) => s === 'ANSWERED').length;
  const outcome: Outcome = stopped ? plannedOutcome : 'completed';
  if (outcome === 'completed') {
    push('survey_completed', {
      answeredCount,
      skippedCount: [...statuses.values()].filter((s) => s === 'SKIPPED').length,
      notApplicableCount: [...statuses.values()].filter((s) => s === 'NOT_APPLICABLE').length,
      activeMs: elapsed,
    });
  } else if (outcome === 'dismissed') {
    push('survey_dismissed', {
      via: weighted([
        ['close_button', 46],
        ['swipe', 28],
        ['backdrop', 12],
        ['hardware_back', persona.platform === 'android' ? 12 : 0.5],
        ['navigation', 4],
        ['programmatic', 1],
      ] as const),
      position: Math.max(1, position),
      answeredCount,
    });
  }
  // Um SDK antigo (1.0.0) às vezes não manda eventos: a base "instrumentada" fica menor que a exibida.
  if (!(persona.sdkVersion === '1.0.0' && chance(0.35))) {
    const eventsPath = `/collect/displays/${displayId}/events`;
    try {
      for (let k = 0; k < events.length; k += 100) {
        await collect(c, persona, eventsPath, { events: events.slice(k, k + 100) });
      }
    } catch (error) {
      // Sem os eventos a exibição ainda vale para os resultados; só fica fora do comportamento.
      if (!(error instanceof ApiError) || error.status < 500) throw error;
      recordServerFailure(eventsPath, error);
    }
  }

  if (outcome !== 'abandoned') {
    const submission = questions
      .filter((q) => statuses.has(q.key) && (outcome === 'completed' || statuses.get(q.key) !== 'SKIPPED' || true))
      .map((q) => {
        const status = statuses.get(q.key)!;
        return status === 'ANSWERED'
          ? { questionKey: q.key, status, value: answers.get(q.key) }
          : { questionKey: q.key, status, value: null };
      });
    if (outcome === 'completed') {
      // Concluída: toda pergunta vista ou pulada vai no envio.
      for (const q of questions) {
        if (!statuses.has(q.key)) submission.push({ questionKey: q.key, status: q.condition ? 'NOT_APPLICABLE' : 'SKIPPED', value: null });
      }
    }
    await collect(c, persona, `/collect/displays/${displayId}/submission`, {
      outcome: outcome === 'completed' ? 'COMPLETED' : 'DISMISSED',
      answers: submission,
    });
  }

  return { displayId, at, outcome };
}

// --- Montagem do catálogo ------------------------------------------------------------------------

async function ensureApplication(api: Admin, name: string, slug: string, body: Record<string, unknown>): Promise<Application> {
  const existing = (await findAll<Application>(api, '/applications')).find((a) => a.slug === slug);
  if (existing !== undefined) {
    throw new Error(
      `A aplicação "${slug}" já existe. Rode com --reset --database-url … para recriar a demo do zero.`,
    );
  }
  const created = await api.post<{ id: string; slug: string }>('/applications', { name, slug, ...body });
  console.log(`Aplicação criada: ${name} (${created.id})`);
  return { id: created.id, slug: created.slug, name };
}

async function addQuestion(api: Admin, base: string, spec: QuestionSpec, keys: Map<string, string>): Promise<void> {
  const condition =
    spec.condition === undefined
      ? undefined
      : {
          sourceKey: keys.get(spec.condition.sourceRef),
          operator: spec.condition.operator,
          ...(spec.condition.min !== undefined ? { min: spec.condition.min, max: spec.condition.max } : {}),
          ...(spec.condition.values !== undefined ? { values: spec.condition.values } : {}),
        };
  const created = await api.post<{ key: string }>(`${base}/questions`, {
    statement: spec.statement,
    type: spec.type,
    required: spec.required ?? false,
    ...(spec.options ? { options: spec.options.map(([label, value]) => ({ label, value })) } : {}),
    ...(spec.range ? { range: { minLabel: null, maxLabel: null, ...spec.range } } : {}),
    ...(condition ? { condition } : {}),
  });
  keys.set(spec.ref, created.key);
}

interface SeededSurvey {
  spec: SurveySpec;
  id: string;
  base: string;
}

async function createSurvey(api: Admin, applicationId: string, spec: SurveySpec): Promise<SeededSurvey> {
  const created = await api.post<Survey>(`/applications/${applicationId}/surveys`, {
    name: spec.name,
    ...('template' in spec.content ? { template: spec.content.template } : {}),
  });
  const base = `/applications/${applicationId}/surveys/${created.id}`;
  const keys = new Map<string, string>();
  if ('template' in spec.content) {
    const detail = await api.get<{ content?: { questions: QuestionRef[] } }>(base);
    const first = detail.content?.questions[0];
    if (first !== undefined) keys.set('$template', first.key);
    for (const q of spec.content.extra ?? []) await addQuestion(api, base, q, keys);
  } else {
    for (const q of spec.content.questions) await addQuestion(api, base, q, keys);
  }

  await api.patch(base, {
    ignoresQuietPeriod: true,
    freeTextNoticeEnabled: true,
    ...(spec.priority !== undefined ? { priority: spec.priority } : {}),
    ...(spec.quota !== undefined ? { responseQuota: spec.quota } : {}),
  });
  await api.put(`${base}/trigger`, {
    eventName: spec.event,
    windowStart: new Date(Date.now() - 120 * DAY).toISOString().replace(/\.\d+Z$/, 'Z'),
    samplingRate: spec.sampling ?? 1.0,
  });
  for (const rule of spec.rules ?? []) await api.post(`${base}/trigger/rules`, rule);

  if (spec.fate !== 'draft') {
    await api.post(`${base}/publication`, {});
  }
  console.log(`  ${spec.name}: ${spec.fate === 'draft' ? 'rascunho' : 'publicada'} (${created.id})`);
  return { spec, id: created.id, base };
}

// --- Datas simuladas -------------------------------------------------------------------------------

/** Um instante nos últimos `from`..`to` dias, com mais tráfego em dias úteis, à noite e mais recente. */
function simulatedInstant(from: number, to: number, now: number): number {
  for (;;) {
    const daysAgo = to + rand() * (from - to);
    const growth = 1 - (daysAgo / Math.max(from, 1)) * 0.45; // tráfego cresce ao longo do tempo
    const date = new Date(now - daysAgo * DAY);
    const weekday = date.getUTCDay();
    const weekFactor = weekday === 0 ? 0.65 : weekday === 6 ? 0.8 : 1;
    if (rand() > growth * weekFactor) continue;
    const hour = weighted([
      [9, 6], [11, 8], [12, 10], [13, 9], [15, 7], [17, 8], [19, 12], [20, 14], [21, 13], [22, 9], [23, 4], [2, 1],
    ] as const);
    date.setUTCHours(hour, int(0, 59), int(0, 59), 0);
    const at = date.getTime();
    if (at < now - 45 * 60 * 1000) return at; // abandono só existe passado o prazo de 30 min
  }
}

// --- Fila com concorrência limitada -------------------------------------------------------------

async function pool<T>(items: readonly T[], size: number, work: (item: T, index: number) => Promise<void>): Promise<void> {
  let cursor = 0;
  const runners = Array.from({ length: Math.min(size, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      await work(items[index]!, index);
    }
  });
  await Promise.all(runners);
}

// --- Principal --------------------------------------------------------------------------------------

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  rngState = args.seed;
  const api = admin(args.baseUrl);
  const now = Date.now();
  console.log(`Seed demo-completo em ${args.baseUrl} (escala ${args.scale}).`);

  if (args.reset) {
    if (args.databaseUrl === null) {
      // Sem acesso direto ao banco (ex.: rodando num container sem psql): só gera o SQL e para.
      const file = writeResetSql();
      console.log(`SQL de reset salvo em ${file}. Aplique e rode o seed de novo, sem --reset.`);
      return;
    }
    resetDemo(args.databaseUrl);
  }

  // Aplicações: a principal, uma secundária pequena e uma inativa.
  const app = await ensureApplication(api, 'Loja Aurora', 'demo-completo', { quietPeriodDays: 14, retentionDays: 365, openTextRetentionDays: 180 });
  const secondary = await ensureApplication(api, 'Aurora Entregas', 'demo-completo-entregas', {});
  const legacy = await ensureApplication(api, 'Aurora Legado (desativado)', 'demo-completo-legado', {});

  // Chaves: uma por plataforma, uma de staging revogada.
  const ios = await api.post<{ id: string; secret: string }>(`/applications/${app.id}/api-keys`, { label: 'ios-producao' });
  const android = await api.post<{ id: string; secret: string }>(`/applications/${app.id}/api-keys`, { label: 'android-producao' });
  const staging = await api.post<{ id: string; secret: string }>(`/applications/${app.id}/api-keys`, { label: 'staging-antigo' });
  const secondaryKey = await api.post<{ id: string; secret: string }>(`/applications/${secondary.id}/api-keys`, { label: 'app-entregador' });
  await api.post(`/applications/${legacy.id}/api-keys`, { label: 'legado' });
  console.log('Chaves emitidas: ios-producao, android-producao, staging-antigo, app-entregador, legado.');

  const collectors: Record<Platform, Collector> = {
    ios: { key: ios.secret, baseUrl: args.baseUrl },
    android: { key: android.secret, baseUrl: args.baseUrl },
  };

  const specs = catalog(args.scale);
  const surveys: SeededSurvey[] = [];
  for (const spec of specs) surveys.push(await createSurvey(api, app.id, spec));

  // Uma pesquisa na aplicação secundária, para o seletor de aplicação ter o que trocar.
  const courier = await createSurvey(api, secondary.id, {
    name: 'Satisfação do entregador',
    event: 'delivery.finished',
    content: { template: 'csat' },
    displays: Math.max(12, Math.round(180 * args.scale)),
    fate: 'published',
    span: [40, 0],
    completion: 0.7,
    dismissal: 0.15,
  });

  const plans: { surveyId: string; plan: DisplayPlan }[] = [];
  let rejected = 0;
  const identities: { reference: string | null; deviceId: string }[] = [];
  const transitions: { surveyId: string; at: number; kind: 'published' | 'paused' | 'ended' | 'v2' }[] = [];

  async function simulate(target: SeededSurvey, collectorsFor: (p: Persona) => Collector, count: number, span: [number, number]) {
    const instants = Array.from({ length: count }, () => simulatedInstant(span[0], span[1], now)).sort((a, b) => a - b);
    let shown = 0;
    let completedSoFar = 0;
    await pool(instants, args.concurrency, async (at) => {
      if (target.spec.quota !== undefined && completedSoFar >= target.spec.quota) return;
      const persona = makePersona();
      const roll = rand();
      const outcome: Outcome = roll < target.spec.completion ? 'completed' : roll < target.spec.completion + target.spec.dismissal ? 'dismissed' : 'abandoned';
      try {
        const plan = await runDisplay(collectorsFor(persona), target.spec, persona, at, outcome);
        if (plan !== null) {
          plans.push({ surveyId: target.id, plan });
          identities.push({ reference: persona.reference, deviceId: persona.deviceId });
          shown += 1;
          if (plan.outcome === 'completed') completedSoFar += 1;
        }
      } catch (error) {
        if (error instanceof ApiError && error.status >= 500) {
          recordServerFailure(error.path, error);
          return;
        }
        if (error instanceof ApiError && (error.status === 409 || error.status === 422 || error.status === 404)) {
          rejected += 1;
          if (rejected <= 3) console.warn(`    envio recusado: ${error.message.slice(0, 400)}`);
          return;
        }
        throw error;
      }
      if (shown > 0 && shown % 200 === 0) console.log(`    ${target.spec.name}: ${shown} exibições`);
    });
    return shown;
  }

  for (const target of surveys) {
    const spec = target.spec;
    if (spec.fate === 'draft') continue;
    transitions.push({ surveyId: target.id, at: now - (spec.span[0] + 2) * DAY, kind: 'published' });
    console.log(`Simulando ${spec.name}…`);
    const pick = (p: Persona) => collectors[p.platform];
    if (spec.v2 !== undefined) {
      const firstShare = Math.round(spec.displays * spec.v2.at);
      const switchDaysAgo = spec.span[1] + (spec.span[0] - spec.span[1]) * (1 - spec.v2.at);
      await simulate(target, pick, firstShare, [spec.span[0], switchDaysAgo]);
      await api.post(`${target.base}/versions`, {});
      const detail = await api.get<{ content?: { questions: QuestionRef[] } }>(target.base);
      await spec.v2.apply(api, target.base, detail.content?.questions ?? []);
      await api.post(`${target.base}/publication`, { changeKind: spec.v2.changeKind, changeSummary: spec.v2.summary });
      transitions.push({ surveyId: target.id, at: now - switchDaysAgo * DAY, kind: 'v2' });
      console.log(`  versão 2 publicada (${spec.v2.changeKind}).`);
      await simulate(target, pick, spec.displays - firstShare, [switchDaysAgo, spec.span[1]]);
    } else {
      await simulate(target, pick, spec.displays, spec.span);
    }
    if (spec.fate === 'paused') {
      await api.post(`${target.base}/pause`);
      transitions.push({ surveyId: target.id, at: now - spec.span[1] * DAY, kind: 'paused' });
    }
    if (spec.fate === 'ended') {
      await api.post(`${target.base}/end`);
      transitions.push({ surveyId: target.id, at: now - spec.span[1] * DAY, kind: 'ended' });
    }
    if (spec.fate === 'quota') transitions.push({ surveyId: target.id, at: now - spec.span[1] * DAY, kind: 'ended' });
  }
  console.log('Simulando Satisfação do entregador (Aurora Entregas)…');
  transitions.push({ surveyId: courier.id, at: now - 42 * DAY, kind: 'published' });
  await simulate(courier, () => ({ key: secondaryKey.secret, baseUrl: args.baseUrl }), courier.spec.displays, courier.spec.span);

  // Saúde do SDK: supressões (versão antiga sem um tipo de pergunta) e erros internos.
  const nps = surveys[0]!;
  const npsDeliverable = await collect<{ survey?: Deliverable | null }>(collectors.android, makePersona(), '/collect/eligibility', {
    event: nps.spec.event,
    respondent: { deviceId: randomUUID() },
    attributes: { plano: 'free' },
  });
  if (npsDeliverable.survey) {
    for (let i = 0; i < Math.round(40 * args.scale) + 5; i += 1) {
      const persona = makePersona();
      await collect(collectors.android, { ...persona, sdkVersion: '1.0.0' }, '/collect/suppressions', {
        surveyId: npsDeliverable.survey.surveyId,
        versionId: npsDeliverable.survey.versionId,
        reason: weighted([['unknown_question_type', 3], ['unsupported_feature', 1]] as const),
        questionTypes: ['MATRIX'],
        features: [],
        deviceId: persona.deviceId,
      });
    }
  }
  const errorKinds = [
    ['render_error', 'Tipo de pergunta sem renderizador: MATRIX', { questionType: 'MATRIX', stage: 'render' }],
    ['network_error', 'Timeout ao consultar elegibilidade após 3000ms', { route: 'eligibility', timeoutMs: 3000 }],
    ['storage_error', 'AsyncStorage: quota exceeded ao gravar a fila', { storage: 'async-storage', queueSize: 212 }],
    ['malformed_response', 'Resposta de elegibilidade sem campo survey', { route: 'eligibility', status: 200 }],
    ['unknown', 'TypeError: undefined is not an object (evaluating theme.colors)', { stage: 'theme' }],
  ] as const;
  for (let i = 0; i < Math.round(60 * args.scale) + 8; i += 1) {
    const [kind, message, context] = weighted(errorKinds.map((e, idx) => [e, [10, 7, 3, 2, 2][idx]!] as const));
    const persona = makePersona();
    const occurredAt = new Date(simulatedInstant(28, 0, now)).toISOString();
    await collect(collectors[persona.platform], { ...persona, sdkVersion: pick(SDK_VERSIONS) }, '/collect/sdk-errors', { kind, message, context, occurredAt });
  }
  console.log('Supressões e erros de SDK registrados.');

  // Ciclo de vida das chaves e das aplicações.
  await api.del(`/applications/${app.id}/api-keys/${staging.id}`);
  await api.post(`/applications/${legacy.id}/deactivate`);

  // Direitos do titular: três exclusões auditadas — duas pela referência do app, uma pelo aparelho.
  const byReference = identities.filter((identity) => identity.reference !== null).slice(0, 2);
  const byDevice = identities.find((identity) => identity.reference === null);
  for (const identity of byReference) {
    await api.del(`/applications/${app.id}/respondents?reference=${encodeURIComponent(identity.reference!)}`);
  }
  if (byDevice !== undefined) {
    await api.del(`/applications/${app.id}/respondents?deviceId=${encodeURIComponent(byDevice.deviceId)}`);
  }
  console.log('Exclusões de titular registradas na auditoria.');

  // --- Datas ---
  const sql = buildBackdateSql(app.id, secondary.id, plans, transitions, surveys, now);
  mkdirSync(OUT_DIR, { recursive: true });
  const sqlPath = join(OUT_DIR, 'demo-completo-datas.sql');
  writeFileSync(sqlPath, sql);
  if (args.databaseUrl !== null) {
    runPsql(args.databaseUrl, sqlPath);
    console.log('Datas reescritas: as exibições agora se espalham pelos últimos 90 dias.');
  } else {
    console.log(`SQL das datas salvo em ${sqlPath}.`);
    console.log('Aplique com: psql "$DATABASE_URL" -f seed/out/demo-completo-datas.sql');
    console.log('  (em produção: docker compose exec -T postgres psql -U $POSTGRES_USER -d $POSTGRES_DB < seed/out/demo-completo-datas.sql)');
  }

  const counts = plans.reduce<Record<Outcome, number>>(
    (acc, p) => ({ ...acc, [p.plan.outcome]: acc[p.plan.outcome] + 1 }),
    { completed: 0, dismissed: 0, abandoned: 0 },
  );
  console.log('');
  console.log('Resumo da demo:');
  console.log(`  Aplicações: Loja Aurora (${app.id}), Aurora Entregas, Aurora Legado (inativa)`);
  console.log(`  Pesquisas: ${surveys.length + 1} — publicadas, pausada, encerrada, encerrada por cota e rascunho`);
  console.log(`  Exibições: ${plans.length} (${counts.completed} concluídas, ${counts.dismissed} dispensadas, ${counts.abandoned} abandonadas)`);
  console.log(`  Painel: /aplicacoes/${app.id}`);
  if (serverFailures.size > 0) {
    console.log('');
    console.log('Erros 5xx do backend que persistiram depois de 4 tentativas (o seed seguiu sem eles):');
    for (const [route, count] of serverFailures) console.log(`  ${route}: ${count}`);
    console.log('Veja o motivo no log da API: docker compose logs api | grep -A30 ERROR');
  }
}

function sqlString(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

function buildBackdateSql(
  appId: string,
  secondaryId: string,
  plans: { surveyId: string; plan: DisplayPlan }[],
  transitions: { surveyId: string; at: number; kind: string }[],
  surveys: SeededSurvey[],
  now: number,
): string {
  const lines: string[] = ['-- Gerado por seed/demo-completo.ts: move a demo para datas passadas.', 'begin;'];
  lines.push('create temp table demo_shift (display_id varchar(64) primary key, at timestamptz not null) on commit drop;');
  for (let i = 0; i < plans.length; i += 500) {
    const chunk = plans.slice(i, i + 500).map(({ plan }) => `(${sqlString(plan.displayId)}, ${sqlString(new Date(plan.at).toISOString())})`);
    lines.push(`insert into demo_shift (display_id, at) values\n${chunk.join(',\n')};`);
  }
  lines.push(`
-- Desloca cada exibição mantendo as durações relativas (abertura → eventos → respostas → fechamento).
create temp table demo_delta on commit drop as
  select d.id as display_id, s.at - d.opened_at as delta
  from survey_displays d join demo_shift s on s.display_id = d.id;

update survey_answers a set answered_at = a.answered_at + x.delta
  from demo_delta x where x.display_id = a.display_id;
update survey_display_events e set occurred_at = e.occurred_at + x.delta, received_at = e.received_at + x.delta
  from demo_delta x where x.display_id = e.display_id;
update survey_displays d set opened_at = d.opened_at + x.delta,
       closed_at = case when d.closed_at is null then null
                        else d.opened_at + x.delta + least(d.closed_at - d.opened_at, interval '4 minutes') + (random() * interval '90 seconds') end
  from demo_delta x where x.display_id = d.id;

update respondents r set first_seen_at = m.first_at, last_seen_at = m.last_at
  from (select respondent_id, min(opened_at) as first_at, max(opened_at) as last_at
          from survey_displays where application_id in (${sqlString(appId)}, ${sqlString(secondaryId)})
         group by respondent_id) m
 where m.respondent_id = r.id;

-- Versões: a 1 publicada antes da primeira exibição, as seguintes no momento da troca.
update survey_versions v set published_at = coalesce(
    (select min(d.opened_at) - interval '6 hours' from survey_displays d where d.version_id = v.id), v.published_at)
 where v.survey_id in (select id from surveys where application_id in (${sqlString(appId)}, ${sqlString(secondaryId)}))
   and v.published_at is not null;
update surveys s set created_at = coalesce(
    (select min(v.published_at) - interval '3 days' from survey_versions v where v.survey_id = s.id), s.created_at - interval '1 day')
 where s.application_id in (${sqlString(appId)}, ${sqlString(secondaryId)});
`);
  for (const t of transitions) {
    if (t.kind === 'v2') continue;
    const state = t.kind === 'published' ? 'PUBLISHED' : t.kind === 'paused' ? 'PAUSED' : 'ENDED';
    lines.push(
      `update survey_state_transitions set occurred_at = ${sqlString(new Date(t.at).toISOString())} where survey_id = ${sqlString(t.surveyId)} and upper(to_state) = '${state}';`,
    );
  }
  lines.push(`
update applications set created_at = now() - interval '120 days', updated_at = now() - interval '2 days'
 where id in (${sqlString(appId)}, ${sqlString(secondaryId)});
update api_keys set created_at = now() - interval '110 days' where application_id = ${sqlString(appId)};
update api_keys set revoked_at = now() - interval '35 days' where application_id = ${sqlString(appId)} and revoked_at is not null;
update deletion_audits set performed_at = now() - (random() * interval '20 days') where application_id = ${sqlString(appId)};

-- Saúde: erros recentes espalhados e uso por versão do SDK ao longo de 30 dias.
update sdk_error_reports set received_at = occurred_at where application_id = ${sqlString(appId)};
update suppression_events set occurred_at = now() - (random() * interval '20 days') where application_id = ${sqlString(appId)};
insert into sdk_version_daily_usage (application_id, version, day, request_count)
  select ${sqlString(appId)}, v.version, (current_date - g.d)::date,
         greatest(1, round(v.base * (1 + g.d * v.trend) * (0.8 + random() * 0.4)))::bigint
    from (values ('1.0.0', 60.0, 0.03), ('1.1.0', 220.0, 0.01), ('1.2.0', 520.0, -0.02)) as v(version, base, trend)
   cross join generate_series(1, 30) as g(d)
  on conflict (application_id, version, day) do update set request_count = excluded.request_count;
update sdk_version_usage set first_seen_at = now() - interval '95 days' where application_id = ${sqlString(appId)};
`);
  void surveys;
  void now;
  lines.push('commit;');
  return `${lines.join('\n')}\n`;
}

function runPsql(databaseUrl: string, file: string): void {
  const result = spawnSync('psql', [databaseUrl, '-v', 'ON_ERROR_STOP=1', '-q', '-f', file], { stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`psql falhou (status ${result.status}). O SQL está em ${file}.`);
}

function resetDemo(databaseUrl: string): void {
  runPsql(databaseUrl, writeResetSql());
  console.log('Demo anterior removida.');
}

function writeResetSql(): string {
  // Apaga as três aplicações da demo e tudo que pende delas, na ordem das chaves estrangeiras.
  const apps = `(select id from applications where slug like 'demo-completo%')`;
  const surveys = `(select id from surveys where application_id in ${apps})`;
  const sql = `
begin;
delete from survey_displays where application_id in ${apps};
delete from respondents where application_id in ${apps};
delete from suppression_events where application_id in ${apps};
delete from sdk_error_reports where application_id in ${apps};
delete from sdk_version_daily_usage where application_id in ${apps};
delete from sdk_version_usage where application_id in ${apps};
delete from aggregate_snapshots where survey_id in ${surveys};
delete from surveys where application_id in ${apps};
delete from application_attributes where application_id in ${apps};
delete from application_events where application_id in ${apps};
delete from deletion_audits where application_id in ${apps};
delete from retention_runs where application_id in ${apps};
delete from api_keys where application_id in ${apps};
delete from applications where id in ${apps};
commit;
`;
  mkdirSync(OUT_DIR, { recursive: true });
  const file = join(OUT_DIR, 'demo-completo-reset.sql');
  writeFileSync(file, sql);
  return file;
}

main().catch((error: unknown) => {
  console.error('Falha no seed demo-completo:');
  console.error(error);
  process.exitCode = 1;
});

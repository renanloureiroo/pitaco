// Validação da configuração do Provider. Configuração inválida nunca lança: vira aviso com a
// correção em desenvolvimento e silêncio em produção. Sem `baseUrl` ou `apiKey` válidos o SDK
// fica desligado; o resto cai no padrão.

import { isPresentation, type Presentation } from '../catalog/events';
import type { Logger } from './logger';

export interface Respondent {
  readonly reference?: string | null;
}

export type AttributeValue = string | number | boolean;
export type Attributes = Readonly<Record<string, AttributeValue>>;

export const DEFAULT_ELIGIBILITY_TIMEOUT_MS = 3000;
export const DEFAULT_REQUEST_TIMEOUT_MS = 10000;
// Prazo máximo de retenção do controle de onde e quando exibir (feature 6/8): usado tanto por
// `defer()`/`release()` quanto pela pesquisa que chega durante um `block()` ativo — mesmo
// conceito (pesquisa esperando liberação), então uma segunda opção só para o bloqueio não parecia
// justificar a complexidade extra. Cinco minutos dá tempo de trocar de tela sem guardar a
// pesquisa por tempo demais (ela deixaria de refletir o momento que a disparou).
export const DEFAULT_DEFER_TIMEOUT_MS = 5 * 60 * 1000;

export const LIMITS = {
  eventName: 80,
  reference: 200,
  attributes: 50,
  attributeName: 80,
  attributeValue: 200,
  blockReason: 80,
} as const;

export interface ConfigInput {
  readonly baseUrl?: unknown;
  readonly apiKey?: unknown;
  readonly presentation?: unknown;
  readonly errorReporting?: unknown;
  readonly debug?: unknown;
  readonly eligibilityTimeoutMs?: unknown;
  readonly deferTimeoutMs?: unknown;
}

export interface ResolvedConfig {
  readonly baseUrl: string;
  readonly apiKey: string;
  readonly presentation: Presentation;
  readonly errorReporting: boolean;
  readonly debug: boolean;
  readonly eligibilityTimeoutMs: number;
  readonly deferTimeoutMs: number;
  readonly requestTimeoutMs: number;
}

function describe(value: unknown): string {
  return typeof value === 'string' ? value : typeof value;
}

const PROVIDER_EXAMPLE =
  '<PitacoProvider baseUrl="https://pitaco.exemplo.com/api" apiKey="pk_..."> (baseUrl é o ' +
  'prefixo que serve /collect: o Pitaco ou o gateway do app)';

export function normalizeBaseUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim().replace(/\/+$/, '');
  if (!/^https?:\/\/[^\s/?#]+/i.test(trimmed)) return null;
  if (/[?#]/.test(trimmed)) return null;
  return trimmed;
}

export function resolveConfig(input: ConfigInput, logger: Logger): ResolvedConfig | null {
  const baseUrl = normalizeBaseUrl(input.baseUrl);
  const apiKey = typeof input.apiKey === 'string' ? input.apiKey.trim() : '';

  let valid = true;
  if (baseUrl === null) {
    valid = false;
    logger.warn(
      input.baseUrl === undefined || input.baseUrl === ''
        ? `baseUrl é obrigatório e não foi informado. Configure ${PROVIDER_EXAMPLE}. O SDK fica desligado até isso ser corrigido.`
        : `baseUrl "${describe(input.baseUrl)}" não é um endereço http(s) válido. Use o endereço sem query nem fragmento, como ${PROVIDER_EXAMPLE}. O SDK fica desligado até isso ser corrigido.`,
    );
  }
  if (apiKey === '') {
    valid = false;
    logger.warn(
      'apiKey é obrigatório e não foi informado. Copie a chave ativa da aplicação no painel do Pitaco e passe apiKey="pk_..." ao PitacoProvider. O SDK fica desligado até isso ser corrigido.',
    );
  }
  if (!valid || baseUrl === null) return null;

  let presentation: Presentation = 'bottom-sheet';
  if (input.presentation !== undefined) {
    if (isPresentation(input.presentation)) {
      presentation = input.presentation;
    } else {
      logger.warn(
        `presentation "${describe(input.presentation)}" não existe. Use "bottom-sheet", "modal" ou "inline". Usando "bottom-sheet".`,
      );
    }
  }

  let eligibilityTimeoutMs = DEFAULT_ELIGIBILITY_TIMEOUT_MS;
  if (input.eligibilityTimeoutMs !== undefined) {
    const value = input.eligibilityTimeoutMs;
    if (typeof value === 'number' && Number.isFinite(value) && value >= 250 && value <= 30000) {
      eligibilityTimeoutMs = Math.round(value);
    } else {
      logger.warn(
        `eligibilityTimeoutMs precisa ser um número entre 250 e 30000 (milissegundos). Usando ${DEFAULT_ELIGIBILITY_TIMEOUT_MS}.`,
      );
    }
  }

  let deferTimeoutMs = DEFAULT_DEFER_TIMEOUT_MS;
  if (input.deferTimeoutMs !== undefined) {
    const value = input.deferTimeoutMs;
    if (typeof value === 'number' && Number.isFinite(value) && value >= 1000 && value <= 3_600_000) {
      deferTimeoutMs = Math.round(value);
    } else {
      logger.warn(
        `deferTimeoutMs precisa ser um número entre 1000 e 3600000 (milissegundos). Usando ${DEFAULT_DEFER_TIMEOUT_MS}.`,
      );
    }
  }

  return {
    baseUrl,
    apiKey,
    presentation,
    errorReporting: input.errorReporting !== false,
    debug: input.debug === true,
    eligibilityTimeoutMs,
    deferTimeoutMs,
    requestTimeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
  };
}

export function sanitizeEventName(value: unknown, logger: Logger): string | null {
  if (typeof value !== 'string' || value.trim() === '') {
    logger.warn('track() precisa do nome do evento, como track("checkout_completed"). Nada foi consultado.');
    return null;
  }
  const name = value.trim();
  if (name.length > LIMITS.eventName) {
    logger.warn(
      `O evento "${name.slice(0, 20)}…" passa de ${LIMITS.eventName} caracteres e não casa com nenhum disparo. Encurte o nome no app e no painel.`,
    );
    return null;
  }
  return name;
}

export function sanitizeReference(value: unknown, logger: Logger): string | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  if (typeof value !== 'string') {
    logger.warn('respondent.reference precisa ser texto. Ignorado; vale o deviceId.');
    return undefined;
  }
  const reference = value.trim();
  if (reference.length > LIMITS.reference) {
    logger.warn(
      `respondent.reference passa de ${LIMITS.reference} caracteres. Use uma referência opaca curta (um hash do id do usuário, nunca e-mail ou nome). Ignorado; vale o deviceId.`,
    );
    return undefined;
  }
  return reference === '' ? undefined : reference;
}

export const DEFAULT_BLOCK_REASON = 'default';

// `block(reason)`/`unblock(reason)`/`<PitacoBlock reason? />`: sem motivo informado, usa
// `DEFAULT_BLOCK_REASON`. Motivo inválido (não é texto, ou passa do limite) também cai nele, com
// aviso em desenvolvimento — melhor um bloqueio genérico do que nenhum.
export function sanitizeBlockReason(value: unknown, logger: Logger): string {
  if (value === undefined || value === null || value === '') return DEFAULT_BLOCK_REASON;
  if (typeof value !== 'string') {
    logger.warn('block()/unblock(): reason precisa ser texto. Usando o motivo padrão.');
    return DEFAULT_BLOCK_REASON;
  }
  const reason = value.trim();
  if (reason === '') return DEFAULT_BLOCK_REASON;
  if (reason.length > LIMITS.blockReason) {
    logger.warn(`block()/unblock(): reason passa de ${LIMITS.blockReason} caracteres. Usando o motivo padrão.`);
    return DEFAULT_BLOCK_REASON;
  }
  return reason;
}

// Atributos de segmentação: só texto, número ou booleano, convertidos em texto como o contrato
// pede. Nunca dado pessoal; o SDK não tem como saber, então só aplica os limites de formato.
export function sanitizeAttributes(
  value: unknown,
  logger: Logger,
  source: string,
): Record<string, string> {
  if (value === undefined || value === null) return {};
  if (typeof value !== 'object' || Array.isArray(value)) {
    logger.warn(`${source}: attributes precisa ser um objeto { nome: valor }. Ignorado.`);
    return {};
  }

  const result: Record<string, string> = {};
  const dropped: string[] = [];
  for (const [name, raw] of Object.entries(value as Record<string, unknown>)) {
    const key = name.trim();
    const valid =
      key !== '' &&
      key.length <= LIMITS.attributeName &&
      (typeof raw === 'string' ||
        typeof raw === 'boolean' ||
        (typeof raw === 'number' && Number.isFinite(raw)));
    const text = valid ? String(raw) : '';
    if (!valid || text.length > LIMITS.attributeValue || Object.keys(result).length >= LIMITS.attributes) {
      dropped.push(key || '(sem nome)');
      continue;
    }
    result[key] = text;
  }

  if (dropped.length > 0) {
    logger.warn(
      `${source}: ${dropped.length} atributo(s) ignorado(s) (${dropped.slice(0, 5).join(', ')}). Cada atributo precisa de nome até ${LIMITS.attributeName} caracteres e valor texto, número ou booleano até ${LIMITS.attributeValue}; no máximo ${LIMITS.attributes}. Nunca mande dado pessoal.`,
    );
  }
  return result;
}

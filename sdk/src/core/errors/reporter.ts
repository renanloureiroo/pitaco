// Relatório de falhas internas do SDK (`POST /collect/sdk-errors`). Canal do Pitaco, nunca a
// ferramenta de erro do app. Desligável com `errorReporting={false}`.
//
// Descreve o SDK, nunca o usuário: a mensagem é montada pelo próprio SDK (etapa e nome do erro,
// nunca `error.message`, que pode trazer conteúdo), e o contexto só aceita chaves técnicas e
// valores curtos. Falha ao reportar desiste em silêncio e não gera outro relatório. Há teto por
// sessão e o mesmo erro não é reportado duas vezes.

import type { Clock } from '../clock';
import { toIsoString } from '../clock';
import type { Logger } from '../logger';
import type { CollectApi } from '../transport/api';

export type SdkErrorKind = 'render_error' | 'network_error' | 'malformed_response' | 'storage_error' | 'unknown';

export type ErrorContextValue = string | number | boolean;
export type ErrorContext = Readonly<Record<string, ErrorContextValue>>;

const MAX_MESSAGE = 500;
const MAX_CONTEXT_KEYS = 10;
const MAX_CONTEXT_VALUE = 80;
const CONTEXT_KEY = /^[a-zA-Z][a-zA-Z0-9]{0,39}$/;
// Rede de segurança: nomes que sugerem dado de usuário nunca entram, mesmo que alguém tente.
const PERSONAL_KEY = /(mail|user|device|answer|text|value|token|reference|respondent|attribute|name|phone|key|id$)/i;

export function describeError(error: unknown): string {
  if (error instanceof Error) return error.name || 'Error';
  return typeof error;
}

export function sanitizeContext(context: ErrorContext): Record<string, ErrorContextValue> {
  const result: Record<string, ErrorContextValue> = {};
  for (const [key, value] of Object.entries(context)) {
    if (Object.keys(result).length >= MAX_CONTEXT_KEYS) break;
    if (!CONTEXT_KEY.test(key) || PERSONAL_KEY.test(key)) continue;
    if (typeof value === 'string') {
      result[key] = value.slice(0, MAX_CONTEXT_VALUE);
    } else if (typeof value === 'boolean' || (typeof value === 'number' && Number.isFinite(value))) {
      result[key] = value;
    }
  }
  return result;
}

export interface ErrorReporterOptions {
  readonly enabled: boolean;
  readonly api: CollectApi | null;
  readonly clock: Clock;
  readonly logger: Logger;
  readonly maxPerSession?: number;
}

export class ErrorReporter {
  private sent = 0;
  private readonly seen = new Set<string>();

  constructor(private readonly options: ErrorReporterOptions) {}

  report(kind: SdkErrorKind, message: string, context: ErrorContext = {}): void {
    try {
      this.options.logger.debug(`falha interna (${kind}): ${message}`);
      const api = this.options.api;
      if (!this.options.enabled || api === null) return;

      const fingerprint = `${kind}|${message}`;
      if (this.seen.has(fingerprint) || this.sent >= (this.options.maxPerSession ?? 10)) return;
      this.seen.add(fingerprint);
      this.sent += 1;

      void api
        .reportError({
          kind,
          message: message.slice(0, MAX_MESSAGE),
          context: sanitizeContext(context),
          occurredAt: toIsoString(this.options.clock.now().wall),
        })
        .then(
          () => undefined,
          () => undefined,
        );
    } catch {
      // Desistência silenciosa: o relatório nunca vira problema.
    }
  }
}

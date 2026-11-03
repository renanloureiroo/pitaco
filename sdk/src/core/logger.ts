// Avisos do SDK. Em desenvolvimento (`__DEV__`) o integrador vê o que está errado e como
// corrigir; em produção o SDK fica em silêncio. Nenhum aviso vai para ferramenta de terceiros.

export interface Logger {
  readonly isDev: boolean;
  warn(message: string): void;
  warnOnce(key: string, message: string): void;
  debug(message: string): void;
}

export function isDevEnvironment(): boolean {
  const flag = (globalThis as { __DEV__?: unknown }).__DEV__;
  return flag === true;
}

export interface LoggerOptions {
  readonly debug?: boolean;
  readonly isDev?: boolean;
  readonly sink?: (message: string) => void;
}

export function createLogger(options: LoggerOptions = {}): Logger {
  const isDev = options.isDev ?? isDevEnvironment();
  const sink = options.sink ?? ((message: string) => console.warn(message));
  const seen = new Set<string>();

  const write = (message: string) => {
    try {
      sink(`[Pitaco] ${message}`);
    } catch {
      // Um console quebrado não pode virar erro do app.
    }
  };

  return {
    isDev,
    warn(message) {
      if (isDev) write(message);
    },
    warnOnce(key, message) {
      if (!isDev || seen.has(key)) return;
      seen.add(key);
      write(message);
    },
    debug(message) {
      if (isDev && options.debug === true) write(message);
    },
  };
}

export const silentLogger: Logger = {
  isDev: false,
  warn: () => undefined,
  warnOnce: () => undefined,
  debug: () => undefined,
};

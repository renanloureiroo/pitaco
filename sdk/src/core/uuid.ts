// UUID v4 gerado no dispositivo: `deviceId` e `displayId` (a chave de idempotência da exibição).
// Usa `crypto.randomUUID` quando existe, depois `crypto.getRandomValues`, e por último um gerador
// próprio. O React Native não traz `crypto` sem polyfill, então o caminho próprio é o comum.

export interface CryptoLike {
  randomUUID?: () => string;
  getRandomValues?: (array: Uint8Array) => Uint8Array;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

export function isUuidV4(value: unknown): value is string {
  return typeof value === 'string' && UUID_V4_PATTERN.test(value);
}

const HEX: readonly string[] = Array.from({ length: 256 }, (_, byte) =>
  byte.toString(16).padStart(2, '0'),
);

// Formata 16 bytes aleatórios como UUID v4 (RFC 9562): versão 4 no nibble alto do byte 6 e
// variante 10xx no byte 8.
export function formatUuidV4(source: Uint8Array): string {
  if (source.length < 16) {
    throw new RangeError('UUID v4 precisa de 16 bytes');
  }
  const bytes = Uint8Array.from(source.subarray(0, 16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;

  let out = '';
  for (let index = 0; index < 16; index += 1) {
    if (index === 4 || index === 6 || index === 8 || index === 10) out += '-';
    out += HEX[bytes[index] ?? 0];
  }
  return out;
}

export interface UuidEnvironment {
  readonly getCrypto?: () => CryptoLike | undefined;
  readonly random?: () => number;
}

const globalCrypto = (): CryptoLike | undefined => (globalThis as { crypto?: CryptoLike }).crypto;

export function createUuidGenerator(environment: UuidEnvironment = {}): () => string {
  const getCrypto = environment.getCrypto ?? globalCrypto;
  const random = environment.random ?? Math.random;

  return () => {
    const crypto = safely(getCrypto);

    if (crypto && typeof crypto.randomUUID === 'function') {
      const value = safely(() => crypto.randomUUID?.());
      if (isUuidV4(value)) return value.toLowerCase();
    }

    const bytes = new Uint8Array(16);
    let filled = false;
    if (crypto && typeof crypto.getRandomValues === 'function') {
      filled = safely(() => {
        crypto.getRandomValues?.(bytes);
        return true;
      }) === true;
    }
    if (!filled) {
      for (let index = 0; index < bytes.length; index += 1) {
        bytes[index] = Math.floor(random() * 256) & 0xff;
      }
    }
    return formatUuidV4(bytes);
  };
}

function safely<T>(read: () => T): T | undefined {
  try {
    return read();
  } catch {
    return undefined;
  }
}

export const uuidV4: () => string = createUuidGenerator();

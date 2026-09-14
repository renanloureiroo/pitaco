import type { PitacoStorage } from './types';

export type StorageOperation = 'read' | 'write' | 'remove' | 'parse';

// Envolve o adaptador do app: nenhuma falha de armazenamento escapa. O que não pôde ser gravado
// fica numa cópia em memória, para a sessão atual continuar funcionando, e a falha é informada a
// quem criou (que reporta como `storage_error`).
export class SafeStorage {
  private readonly fallback = new Map<string, string>();
  private chain: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly adapter: PitacoStorage,
    private readonly onError: (operation: StorageOperation, error: unknown) => void,
  ) {}

  async read(key: string): Promise<string | null> {
    try {
      const value = await this.adapter.getItem(key);
      if (typeof value === 'string') return value;
      return this.fallback.get(key) ?? null;
    } catch (error) {
      this.fail('read', error);
      return this.fallback.get(key) ?? null;
    }
  }

  // As gravações passam uma de cada vez e na ordem em que foram pedidas: a última sempre vence.
  write(key: string, value: string): Promise<boolean> {
    this.fallback.set(key, value);
    const next = this.chain.then(async () => {
      try {
        await this.adapter.setItem(key, value);
        return true;
      } catch (error) {
        this.fail('write', error);
        return false;
      }
    });
    this.chain = next;
    return next;
  }

  remove(key: string): Promise<boolean> {
    this.fallback.delete(key);
    const next = this.chain.then(async () => {
      try {
        await this.adapter.removeItem(key);
        return true;
      } catch (error) {
        this.fail('remove', error);
        return false;
      }
    });
    this.chain = next;
    return next;
  }

  async readJson(key: string): Promise<unknown> {
    const raw = await this.read(key);
    if (raw === null) return null;
    try {
      return JSON.parse(raw) as unknown;
    } catch (error) {
      this.fail('parse', error);
      return null;
    }
  }

  writeJson(key: string, value: unknown): Promise<boolean> {
    let serialized: string;
    try {
      serialized = JSON.stringify(value);
    } catch (error) {
      this.fail('write', error);
      return Promise.resolve(false);
    }
    return this.write(key, serialized);
  }

  private fail(operation: StorageOperation, error: unknown) {
    try {
      this.onError(operation, error);
    } catch {
      // Quem reporta também não pode derrubar nada.
    }
  }
}

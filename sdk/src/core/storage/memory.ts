import type { PitacoStorage } from './types';

const MEMORY = Symbol.for('pitaco.memory-storage');

export interface MemoryStorage extends PitacoStorage {
  readonly [MEMORY]: true;
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

// Padrão quando o app não passa `storage`. Funciona, mas a fila não sobrevive a um reinício do
// app; o SDK avisa isso em desenvolvimento.
export function createMemoryStorage(initial: Readonly<Record<string, string>> = {}): MemoryStorage {
  const values = new Map<string, string>(Object.entries(initial));
  return {
    [MEMORY]: true,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value);
    },
    removeItem: (key) => {
      values.delete(key);
    },
  };
}

export function isMemoryStorage(storage: unknown): storage is MemoryStorage {
  return typeof storage === 'object' && storage !== null && MEMORY in storage;
}

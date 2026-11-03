// `@pitaco/react-native/storage/mmkv`
//
// Adaptador para uma instância do `react-native-mmkv` que o app já criou. O SDK não importa o
// pacote. Aceita a API das versões 2 e 3 (`delete`) e a da 4 (`remove`).
//
//   import { MMKV } from 'react-native-mmkv';
//   import { createMmkvAdapter } from '@pitaco/react-native/storage/mmkv';
//   const storage = new MMKV({ id: 'pitaco' });
//   <PitacoProvider storage={createMmkvAdapter(storage)} ... />

import { createLogger } from '../core/logger';
import { createMemoryStorage } from '../core/storage/memory';
import type { PitacoStorage } from '../core/storage/types';

export type { PitacoStorage };

export interface MmkvLike {
  getString(key: string): string | undefined;
  set(key: string, value: string): void;
  delete?: (key: string) => void;
  remove?: (key: string) => unknown;
}

function looksLikeMmkv(instance: unknown): instance is MmkvLike {
  if (typeof instance !== 'object' || instance === null) return false;
  const candidate = instance as Partial<Record<keyof MmkvLike, unknown>>;
  return (
    typeof candidate.getString === 'function' &&
    typeof candidate.set === 'function' &&
    (typeof candidate.delete === 'function' || typeof candidate.remove === 'function')
  );
}

export function createMmkvAdapter(instance: MmkvLike): PitacoStorage {
  if (!looksLikeMmkv(instance)) {
    createLogger().warn(
      'createMmkvAdapter recebeu algo que não é uma instância do MMKV. Crie a instância no app ' +
        '(new MMKV() ou createMMKV()) e passe-a: createMmkvAdapter(instancia). Até lá a fila fica ' +
        'em memória e não sobrevive a um reinício do app.',
    );
    return createMemoryStorage();
  }

  return {
    getItem: (key) => instance.getString(key) ?? null,
    setItem: (key, value) => {
      instance.set(key, value);
    },
    removeItem: (key) => {
      if (typeof instance.remove === 'function') {
        instance.remove(key);
      } else {
        instance.delete?.(key);
      }
    },
  };
}

// `@pitaco/react-native/storage/async-storage`
//
// Adaptador para o AsyncStorage que o app já usa. Recebe a instância: o SDK não importa
// `@react-native-async-storage/async-storage` e não força a dependência em quem não a tem.
//
//   import AsyncStorage from '@react-native-async-storage/async-storage';
//   import { createAsyncStorageAdapter } from '@pitaco/react-native/storage/async-storage';
//   <PitacoProvider storage={createAsyncStorageAdapter(AsyncStorage)} ... />

import { createLogger } from '../core/logger';
import { createMemoryStorage } from '../core/storage/memory';
import type { PitacoStorage } from '../core/storage/types';

export type { PitacoStorage };

export interface AsyncStorageLike {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

function looksLikeAsyncStorage(instance: unknown): instance is AsyncStorageLike {
  if (typeof instance !== 'object' || instance === null) return false;
  const candidate = instance as Partial<Record<keyof AsyncStorageLike, unknown>>;
  return (
    typeof candidate.getItem === 'function' &&
    typeof candidate.setItem === 'function' &&
    typeof candidate.removeItem === 'function'
  );
}

export function createAsyncStorageAdapter(instance: AsyncStorageLike): PitacoStorage {
  if (!looksLikeAsyncStorage(instance)) {
    createLogger().warn(
      'createAsyncStorageAdapter recebeu algo que não é o AsyncStorage. Passe a instância ' +
        'importada de "@react-native-async-storage/async-storage": ' +
        'createAsyncStorageAdapter(AsyncStorage). Até lá a fila fica em memória e não sobrevive ' +
        'a um reinício do app.',
    );
    return createMemoryStorage();
  }

  return {
    getItem: (key) => instance.getItem(key),
    setItem: (key, value) => instance.setItem(key, value),
    removeItem: (key) => instance.removeItem(key),
  };
}

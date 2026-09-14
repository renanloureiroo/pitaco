// Armazém mínimo fora do React para os logs do painel de depuração. Fica fora do estado da raiz
// de propósito: um evento ou uma requisição a mais não deve renderizar o app inteiro de novo,
// só quem está olhando o log (via `useSyncExternalStore`).
import { useSyncExternalStore } from 'react';

export interface LogStore<T> {
  push(entry: T): void;
  update(match: (entry: T) => boolean, patch: (entry: T) => T): void;
  clear(): void;
  getSnapshot(): readonly T[];
  subscribe(listener: () => void): () => void;
}

export function createLogStore<T>(maxEntries: number): LogStore<T> {
  let entries: readonly T[] = [];
  const listeners = new Set<() => void>();
  let notifyScheduled = false;

  // O `onEvent` do SDK pode chegar no meio do render de outro componente. Avisar os ouvintes numa
  // microtarefa evita o "Cannot update a component while rendering a different component".
  const notify = () => {
    if (notifyScheduled) return;
    notifyScheduled = true;
    queueMicrotask(() => {
      notifyScheduled = false;
      listeners.forEach((listener) => listener());
    });
  };

  return {
    push(entry) {
      const next = [...entries, entry];
      entries = next.length > maxEntries ? next.slice(next.length - maxEntries) : next;
      notify();
    },
    update(match, patch) {
      entries = entries.map((entry) => (match(entry) ? patch(entry) : entry));
      notify();
    },
    clear() {
      entries = [];
      notify();
    },
    getSnapshot: () => entries,
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

export function useLogEntries<T>(store: LogStore<T>): readonly T[] {
  return useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
}

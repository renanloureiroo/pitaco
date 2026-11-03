// Condições de rede simuladas pelo embrulho de `fetch` do exemplo (`networkLog.ts`), só para as
// chamadas ao Pitaco. Não é recurso do SDK: o SDK só vê um `fetch` que falha ou demora, como veria
// num aparelho de verdade.
//
// - `offline` (cenário 13, "Simular sem rede"): a requisição falha como falha de rede
//   (`TypeError: Network request failed`), sem sair do aparelho. O simulador iOS não tem modo
//   avião; em aparelho físico, prefira o modo avião real.
// - `latencyMs` (cenário 14, "API lenta"): espera antes de repassar a requisição. Se o SDK desistir
//   antes (o `AbortSignal` dispara no timeout de elegibilidade), a espera termina na hora com
//   `AbortError`, e o painel mostra a duração até a desistência.
//
// Fica só em memória, de propósito: fechar e reabrir o app volta sempre com rede e sem latência.
// É isso que o cenário 13 precisa ("reabrir com rede"), e evita um app preso sem rede por engano.
import { useSyncExternalStore } from 'react';

export interface NetworkConditions {
  readonly offline: boolean;
  readonly latencyMs: number;
}

const NORMAL: NetworkConditions = { offline: false, latencyMs: 0 };

let current: NetworkConditions = NORMAL;
const listeners = new Set<() => void>();

export function getNetworkConditions(): NetworkConditions {
  return current;
}

export function setNetworkConditions(patch: Partial<NetworkConditions>): void {
  const next = { ...current, ...patch };
  if (next.offline === current.offline && next.latencyMs === current.latencyMs) return;
  current = next;
  listeners.forEach((listener) => listener());
}

export function resetNetworkConditions(): void {
  setNetworkConditions(NORMAL);
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useNetworkConditions(): NetworkConditions {
  return useSyncExternalStore(subscribe, getNetworkConditions, getNetworkConditions);
}

// O mesmo erro que o `fetch` do React Native lança sem rede.
export function simulatedNetworkError(): TypeError {
  return new TypeError('Network request failed (simulado: "Simular sem rede")');
}

// Espera `ms`, ou termina antes com `AbortError` se o sinal disparar (o SDK desistiu).
export function waitUnlessAborted(ms: number, signal: AbortSignal | null | undefined): Promise<void> {
  return new Promise((resolve, reject) => {
    const abortError = () => {
      const error = new Error('O SDK desistiu da requisição durante a latência simulada.');
      error.name = 'AbortError';
      return error;
    };
    if (signal?.aborted === true) {
      reject(abortError());
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      reject(abortError());
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

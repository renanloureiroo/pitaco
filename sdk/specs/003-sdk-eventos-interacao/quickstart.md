# Quickstart — validar `003-sdk-eventos-interacao`

Catálogo em [`contracts/event-catalog.md`](./contracts/event-catalog.md); rota em
[`contracts/collect-events-route.md`](./contracts/collect-events-route.md).

## Pré-requisitos

- `cd sdk && npm ci && npm ci --prefix example`
- O monorepo inteiro (o teste contra a enumeração lê `backend/` pelo caminho relativo)

---

## 1. O portão

```bash
cd sdk
npm run verify
```

## 2. Só esta fatia

```bash
# contrato do catálogo (OpenAPI versionado e enumeração do backend)
npx jest --ci src/catalog

# cada evento, a ordem e os tempos com relógio falso
npx jest --ci src/core/machine

# mesma sequência: UI padrão × substituída × headless; headless sobre o Provider real
npx jest --ci src/ui/content/__tests__/sameSequence src/react/__tests__/hooks

# envio em lote, dedup por seq, teto, lotes de 100
npx jest --ci src/core/queue src/core/runtime
```

## 3. Atualizar o contrato depois de mudar o catálogo no backend

```bash
# backend no ar em localhost:8080
npm run gen:api
npx jest --ci src/catalog
npm run typecheck
```

Se o backend acrescentou um tipo, o primeiro teste mostra `onlyInBackend`; se o SDK acrescentou sem
o backend, `onlyInSdk`. O `typecheck` falha se a forma de um `<Tipo>Event` gerado divergir.

## 4. O que precisa estar coberto

| # | Cenário | Onde |
| --- | --- | --- |
| 1 | Os 18 tipos, `seq` de 1 em diante, `elapsedMs` monotônico, `questionKey` só nos de pergunta | `machine.test.ts` — "envelope válido em todo evento" |
| 2 | Roteiros completo e de dispensa cobrem os 18 tipos | `machine.test.ts` |
| 3 | Ordem de `next`, `back`, conclusão, dispensa | `machine.test.ts` |
| 4 | `text_edited` com debounce de 1 s; `text_blurred` ao sair com foco | `machine.test.ts` — "texto livre" |
| 5 | `activeMs` desconta segundo plano, inclusive ainda em curso | `machine.test.ts` — "segundo plano" |
| 6 | Servidor recebe o mesmo que o `onEvent` | `runtime.test.ts` |
| 7 | Lote alguns segundos depois durante a resposta | `runtime.test.ts` |
| 8 | Lotes de 100, teto de 500, reenvio como `duplicated` | `queue.test.ts` |
| 9 | Tipos, versão, campos, `questionKey`, união, `placement_` fora | `catalog.contract.test.ts` |
| 10 | Nomes iguais à enumeração do backend | `contract.test.ts` |
| 11 | Mesma assinatura nas três UIs, sem `fetch` | `sameSequence.contract.test.tsx` |
| 12 | Mesma assinatura headless com `AppState` real | `hooks.test.tsx` — "roteiro de interação" |
| 13 | `onEvent` que lança não derruba nada | `runtime.test.ts` |

## 5. No aparelho

No app de exemplo, abrir a mesma pesquisa nas formas **Sheet do SDK**, **Gorhom** e **Tela**,
percorrer do mesmo jeito e, na aba **Depuração**, agrupar por "Exibição": cada `displayId` é uma
forma, e as três sequências precisam ser iguais. Na seção de rede, cada `POST …/events` responde `202` com `accepted` e, num
reenvio, `duplicated`.

# Implementation Plan: SDK React Native — eventos de interação

**Branch**: `003-sdk-eventos-interacao` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-sdk-eventos-interacao/spec.md`

## Summary

Fazer a máquina de estado do core emitir o catálogo de eventos de interação versão 1, entregar os
eventos ao `onEvent` do app e à fila persistente, enviá-los em lote para
`POST /collect/displays/{displayId}/events`, e provar por teste que o catálogo bate com o backend e
que qualquer UI produz a mesma sequência.

Quatro movimentos:

1. **Catálogo como módulo tipado único** (D-01): `src/catalog/events.ts` com a união discriminada,
   os campos de `data` por tipo e os detectores.
2. **Emissão dentro da transição** (D-02 a D-05): `transition()` devolve `{ estado, eventos }`; o
   envelope, o `seq` e o `elapsedMs` são montados num só lugar (`Run.emit`), e a ordem por
   transição é fixa e documentada no topo de `machine.ts`.
3. **Dois destinos, uma origem** (D-06, D-07): o runtime grava os eventos da transição na fila e os
   entrega ao `onEvent` na mesma passada; a fila os agrupa por exibição e os manda em lote.
4. **Dois testes de contrato** (D-08, D-09): o catálogo contra o OpenAPI versionado (e contra a
   enumeração Java), e a mesma sequência entre UI padrão, substituída e headless contra a máquina.

## Technical Context

**Language/Version**: TypeScript 5.9 estrito

**Primary Dependencies**: nenhuma nova. Tipos do contrato de `src/api/openapi.ts` (gerado).

**Storage**: a fila persistente da `001`, item `events` por exibição.

**Testing**: Jest 29; relógio falso para os tempos; roteiros `STANDARD_SCRIPT` e `DISMISS_SCRIPT`
em `src/__tests__/support/script.ts`; servidor falso que conta `accepted`/`duplicated`.

**Target Platform**: iOS, Android e web (preview)

**Project Type**: biblioteca React Native

**Performance Goals**: no máximo um envio de eventos agendado a cada 5 s por exibição durante a
resposta; lote de até 100.

**Constraints**: nenhum texto livre nem rótulo em evento; catálogo fechado; `placement_` fora do
catálogo enviado.

**Scale/Scope**: 18 tipos, 13 com `questionKey`; 1 rota consumida; 2 testes de contrato.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | Nada novo de runtime; `AppState` é do React Native. | ✅ |
| **II. Degradação Silenciosa** | `deliver()` engole erro do `onEvent` com aviso único; falha de envio fica na fila. | ✅ |
| **III. O Servidor Decide** | Eventos descrevem interação; não influenciam elegibilidade. | ✅ |
| **IV. Catálogo Fixo** | Módulo único, `CATALOG_VERSION`, sem API de emissão; teste de contrato contra OpenAPI e enumeração do backend; `placement_` fora. | ✅ |
| **V. Sem Dado Pessoal** | Texto só como `length`; `value` da opção, nunca rótulo; escolha em texto livre nunca vira evento. | ✅ |
| **VI. Três Camadas** | Só a máquina emite; o teste de mesma sequência prova que a UI não interfere. | ✅ |
| **VII. Acessível** | n/a | ✅ |
| **VIII. Teste É Contrato** | Teste de cada evento, da ordem e dos tempos com relógio falso; contrato do catálogo; mesma sequência. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

- **`onEvent` recebe o mesmo objeto que vai para a fila.** Copiar cada evento custaria uma alocação
  por evento para proteger contra um uso fora do contrato; os tipos são `readonly`. Aceito.
- **O teste contra a enumeração Java lê um arquivo fora do pacote.** Ele só funciona no monorepo;
  o teste contra o OpenAPI versionado é o que vale para quem tem só o pacote. Aceito como reforço.

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/003-sdk-eventos-interacao/
├── plan.md
├── research.md          # D-01 a D-10
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── event-catalog.md          # os 18 tipos, envelope e data
│   └── collect-events-route.md   # POST /collect/displays/{displayId}/events
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
src/catalog/
├── events.ts                          catálogo v1
└── __tests__/
    ├── catalog.contract.test.ts       contra openapi/pitaco.json
    └── contract.test.ts               contra InteractionEventType.java do backend

src/core/machine/machine.ts            emissão (Run.emit, ordem por transição)
src/core/session/session.ts            debounce de text_edited por tick
src/core/runtime/runtime.ts            onTransition → fila + deliver; scheduleFlush; onAppState
src/core/queue/queue.ts                item events: dedup por seq, lote de 100, teto de 500
src/core/transport/api.ts              sendEvents

src/__tests__/support/script.ts        roteiros e assinatura
src/core/machine/__tests__/machine.test.ts
src/core/runtime/__tests__/runtime.test.ts
src/core/queue/__tests__/queue.test.ts
src/react/__tests__/hooks.test.tsx     roteiros headless sobre o Provider real (com AppState)
src/ui/content/__tests__/sameSequence.contract.test.tsx
```

**Structure Decision**: o catálogo fica fora de `core/` (`src/catalog/`) porque é contrato
exportado ao app, ao painel e aos testes; `core/` o importa, nunca o contrário.

## Complexity Tracking

Sem violação. O segundo teste de contrato (contra a enumeração Java) é redundante com o do OpenAPI
quando o snapshot está atualizado, e existe para pegar o caso em que o snapshot ficou velho.

# Implementation Plan: SDK React Native — controle de onde e quando a pesquisa aparece

**Branch**: `004-sdk-controle-onde-quando` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-sdk-controle-onde-quando/spec.md`

## Summary

Acrescentar ao runtime o limite de uma pesquisa por sessão de app, o bloqueio por motivo com
contagem de referências, o adiamento com liberação e o prazo comum de retenção, todos expostos ao
app por `usePitaco()`, `<PitacoBlock />`, `deferTimeoutMs` e nove eventos `placement_` só no
`onEvent`.

Três movimentos:

1. **Limite antes da rede** (D-01, D-02): `sessionSurveyShown` em memória, ligado no
   `survey_presented`; `track()` o checa logo depois de validar o nome do evento.
2. **Bloqueio e adiamento como portão padrão** (D-03 a D-07): o runtime nasce com um
   `PlacementGate` próprio, sobre a interface que a `001-sdk-core` já previa; a candidata retida
   guarda os `controls` do `consider()` que a reteve, a causa e o instante.
3. **Catálogo `placement_` à parte** (D-08): `src/catalog/placement.ts` com os nove tipos e o
   envelope sem `displayId` nem `seq`; `emitPlacement` vai só para `deliver()`.

## Technical Context

**Language/Version**: TypeScript 5.9 estrito

**Primary Dependencies**: nenhuma nova

**Storage**: nenhum — o limite e a retenção vivem em memória por decisão (D-01)

**Testing**: Jest 29 com relógio e timers falsos; `runtime.test.ts` ganha os blocos de sessão,
bloqueio, adiamento, reset e `placement_`; `PitacoBlock.test.tsx`; `PitacoProvider.lifecycle.test.tsx`

**Target Platform**: iOS e Android

**Project Type**: biblioteca React Native

**Performance Goals**: nenhuma consulta de elegibilidade depois da primeira exibição na sessão

**Constraints**: nenhum `placement_` na fila; nenhuma exibição de pesquisa retida além do prazo

**Scale/Scope**: 4 ações novas em `usePitaco()`, 1 componente, 1 prop, 9 tipos de evento

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | Nada novo de runtime. | ✅ |
| **II. Degradação Silenciosa** | `unblock` sem `block` e `release` sem `defer` são ignorados com aviso único; falha no portão vira relatório, nunca exceção; ações inertes fora do Provider. | ✅ |
| **III. O Servidor Decide** | O portão decide onde e quando; nunca reinterpreta a resposta da elegibilidade. O limite de sessão só evita nova consulta. | ✅ |
| **IV. Catálogo Fixo** | `placement_` tem catálogo próprio, fora do enviado; o teste de contrato da `003` confere que nenhum está no OpenAPI. | ✅ |
| **V. Sem Dado Pessoal** | Motivo de bloqueio é texto do app, limitado a 80 caracteres e nunca enviado ao servidor. | ✅ |
| **VI. Três Camadas** | Tudo no runtime (core); `<PitacoBlock />` é cola React sobre `usePitaco()`. As apresentações respeitam o portão sem mudança porque só a oferta torna a pesquisa disponível. | ✅ |
| **VII. Acessível** | n/a | ✅ |
| **VIII. Teste É Contrato** | Cada regra com teste em `runtime.test.ts` sobre timers falsos, antes do código. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

- **Um prazo para dois usos.** Uma segunda opção só para o bloqueio não se pagaria em clareza: é o
  mesmo conceito, pesquisa esperando liberação.
- **Desmonte do Provider sem `dispose()`.** O efeito de limpeza não distingue o StrictMode do
  desmonte definitivo; descartar ali quebraria a remontagem. O temporizador de uma retida pode
  ainda emitir pelo ouvinte da árvore desmontada, sem efeito no servidor. Registrado como lacuna
  aceita em Complexity Tracking.

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/004-sdk-controle-onde-quando/
├── plan.md
├── research.md          # D-01 a D-11
├── data-model.md
├── quickstart.md
├── contracts/
│   └── placement-api.md # usePitaco, PitacoBlock, deferTimeoutMs, placement_, PlacementGate
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
src/catalog/placement.ts               9 tipos placement_, envelope, isPlacementEvent
src/core/config.ts                     DEFAULT_DEFER_TIMEOUT_MS, deferTimeoutMs, sanitizeBlockReason
src/core/runtime/runtime.ts            sessionSurveyShown, blockReasons, deferRequested, held,
                                       block/unblock/defer/release/simulateAppReopen,
                                       considerPlacement, holdCandidate, expireHeld, tryReleaseHeld,
                                       emitPlacement, dispose
src/core/runtime/placement.ts          comentário do ponto de extensão (interface inalterada)
src/react/usePitaco.ts                 block, unblock, defer, release, diagnostics, simulateAppReopen
src/react/PitacoBlock.tsx              novo
src/react/PitacoProvider.tsx           prop deferTimeoutMs; dispose do runtime substituído
src/index.ts                           PitacoBlock, PLACEMENT_EVENT_TYPES, isPlacementEvent, tipos

src/core/runtime/__tests__/runtime.test.ts
src/react/__tests__/PitacoBlock.test.tsx
src/react/__tests__/PitacoProvider.lifecycle.test.tsx
src/react/__tests__/hooks.test.tsx     ações fora do Provider; diagnóstico
```

**Structure Decision**: o controle fica no runtime, sobre a interface `PlacementGate` já
existente; nenhuma mudança em `ui/` nem nas apresentações.

## Complexity Tracking

| Violação / risco | Por que é aceito | Alternativa rejeitada porque |
| --- | --- | --- |
| Desmonte definitivo do Provider só faz `detach()` | `dispose()` na limpeza quebraria o StrictMode, que remonta o mesmo runtime e perderia os assinantes do `useSyncExternalStore` | `dispose()` adiado e cancelável é possível, mas o efeito residual (um `placement_expired` pelo ouvinte de uma árvore desmontada) não abre exibição nem envia nada |

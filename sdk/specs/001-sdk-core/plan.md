# Implementation Plan: SDK React Native — core headless

**Branch**: `001-sdk-core` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-sdk-core/spec.md`

## Summary

Criar o pacote `@pitaco/react-native` em `sdk/` com o core headless: Provider, hooks, runtime,
máquina de estado da pesquisa, fila local persistente, transporte HTTP, identidade, storage e
relatório de erro, sobre o contrato `/collect` já publicado pelo backend.

A abordagem técnica cabe em seis movimentos:

1. **Máquina pura** (D-04): `transition(estado, ação, instante)` em `core/machine/machine.ts`,
   sem I/O. A sessão (`core/session/session.ts`) segura o estado e aplica o relógio; o runtime só
   reage ao resultado.
2. **Runtime orquestrador** (D-05): `core/runtime/runtime.ts` consulta a elegibilidade, normaliza,
   decide entre supressão e oferta, abre a exibição só no `present()` e serializa as gravações numa
   cadeia única.
3. **Fila persistente única** (D-08): abertura, envio, eventos e supressão num documento JSON por
   par (`baseUrl`, `apiKey`), com dependência explícita da abertura, backoff, pausa por `429` e
   tetos.
4. **Transporte que não lança** (D-06): `HttpClient.post` devolve um `HttpOutcome` classificado;
   timeout por corrida com `AbortController`.
5. **Superfície React mínima** (D-12): `PitacoProvider` cria o runtime no render sem efeito
   colateral e o liga no efeito; `usePitaco` e `usePitacoSurvey` são finos e inertes fora do
   Provider.
6. **Contrato gerado** (D-02): `openapi/pitaco.json` versionado + `scripts/gen-api.mjs`
   → `src/api/openapi.ts`; o SDK importa só de `src/api/types.ts`.

## Technical Context

**Language/Version**: TypeScript 5.9 estrito (`noUncheckedIndexedAccess`), saída ES via Babel

**Primary Dependencies**: `react >=18.2` e `react-native >=0.74` como peer; em desenvolvimento
React 19.2, React Native 0.87.1, `react-native-builder-bob`, `openapi-typescript` 7, ESLint 10 com
`typescript-eslint` e `eslint-plugin-react-hooks` 7. **Nenhuma dependência de runtime.**

**Storage**: interface `PitacoStorage` (`getItem`/`setItem`/`removeItem`, síncrona ou
assíncrona); memória por padrão, AsyncStorage e MMKV por adaptador que recebe a instância.

**Testing**: Jest 29 com `@react-native/jest-preset` e `@testing-library/react-native` 14;
relógio falso (`src/__tests__/support/fakeClock.ts`), servidor falso com a idempotência do contrato
(`fakeServer.ts`), fixtures do schema de referência (`fixtures.ts`) e roteiros reutilizáveis
(`script.ts`).

**Target Platform**: iOS e Android via React Native (inclusive Expo Go); web via
`react-native-web` sem garantia de apresentação

**Project Type**: biblioteca React Native (pacote npm com subpaths)

**Performance Goals**: sem meta numérica; garantias estruturais: elegibilidade nunca segura mais
que o timeout; nenhuma requisição de abertura sem exibição visível; eventos agrupados em lote para
caber no limite de 120 requisições por minuto por origem.

**Constraints**: zero módulo nativo; nenhum método público lança; nada de dado pessoal em payload
ou log; fila com teto de 200 itens e 3 dias.

**Scale/Scope**: 6 rotas consumidas, 1 Provider, 2 hooks, 2 adaptadores de storage em subpath,
1 máquina de estado com 13 ações, 4 tipos de item de fila.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | `package.json` só com `peerDependencies`; adaptadores de storage em `src/storage/` recebem a instância; `no-restricted-imports` no ESLint proíbe os seis pacotes nativos; `version.test.ts` confere que não há dependência de runtime. | ✅ |
| **II. Degradação Silenciosa** | `HttpClient` devolve `HttpOutcome`; `SafeStorage` engole falha do adaptador; `PitacoRuntime.create` devolve `null` em configuração inválida; todo método público tem `try/catch`; `Logger` só fala em `__DEV__`. | ✅ |
| **III. O Servidor Decide** | O runtime só pergunta e oferece; a única regra local é a `condition` (`core/survey/condition.ts`), falhando fechado; tipo desconhecido é pulado e suprimido. | ✅ |
| **IV. Catálogo Fixo** | Esta fatia consome o catálogo de `src/catalog/events.ts` sem estendê-lo; a forma dos eventos é da `003`. Os únicos `placement_` emitidos aqui (`available`, `suppressed`) vão só ao `onEvent`. | ✅ |
| **V. Sem Dado Pessoal** | Relatório de erro monta a mensagem pelo nome do erro e filtra o contexto (`core/errors/reporter.ts`); avisos nunca imprimem valor de atributo nem resposta; referência e atributos com limites e aviso. | ✅ |
| **VI. Três Camadas** | Tudo em `core/` é independente de React; `react/` só liga o runtime ao ciclo de vida. A máquina é a única forma de mudar estado; `SurveyController` é a interface que a UI consome. | ✅ |
| **VII. Acessível** | Sem UI nesta fatia; o estado exposto já carrega o que a UI precisa para anunciar erro e mover foco (`validationError`, `question`). | ✅ (n/a) |
| **VIII. Teste É Contrato** | Cada módulo de `core/` nasce com teste sobre relógio e servidor falsos; o `jest.setup.ts` bloqueia rede real. | ✅ |

**Gate de Phase 0**: aprovado, sem violação a justificar.

### Re-avaliação após Phase 1 (design)

Dois pontos ganharam escrutínio e sobreviveram:

- **O runtime nasce no render.** Parece efeito colateral, mas o construtor não toca storage, rede
  nem `AppState`; tudo isso acontece em `attach()`, chamado no efeito. É o que permite um `track`
  no efeito de montagem de um filho encontrar o runtime pronto (D-12).
- **`PlacementGate` entra antes de haver bloqueio.** É um ponto de extensão sem segundo caso nesta
  fatia, mas o desenho da `004-sdk-controle-onde-quando` já existia; o custo é uma interface de
  três métodos e o portão imediato. Registrado em Complexity Tracking.

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/001-sdk-core/
├── plan.md
├── research.md          # D-01 a D-20
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── public-api.md    # Provider, hooks, storage, tipos exportados
│   └── collect-routes.md # as rotas /collect consumidas e como cada resultado é tratado
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
package.json  tsconfig.json  tsconfig.build.json  babel.config.js
jest.config.js  jest.setup.ts  eslint.config.mjs
openapi/pitaco.json                 snapshot do OpenAPI do backend
scripts/gen-api.mjs                 npm run gen:api

src/
├── index.ts                        superfície pública de `@pitaco/react-native`
├── version.ts                      SDK_VERSION
├── api/
│   ├── openapi.ts                  gerado, não editar
│   └── types.ts                    aliases dos DTOs /collect
├── core/
│   ├── clock.ts  timers.ts  logger.ts  uuid.ts  config.ts  identity.ts
│   ├── storage/{types,memory,safe}.ts
│   ├── survey/{schema,condition,answers}.ts
│   ├── machine/{machine,path,submission}.ts
│   ├── session/{controller,session,snapshot}.ts
│   ├── transport/{http,api}.ts
│   ├── queue/queue.ts
│   ├── errors/reporter.ts
│   └── runtime/{runtime,placement}.ts
├── react/
│   ├── PitacoProvider.tsx  context.ts  ErrorBoundary.tsx
│   ├── usePitaco.ts  usePitacoSurvey.ts
│   └── SurfaceHost.tsx             ponto de montagem da apresentação (ver 002)
├── storage/
│   ├── async-storage.ts            @pitaco/react-native/storage/async-storage
│   └── mmkv.ts                     @pitaco/react-native/storage/mmkv
└── __tests__/
    ├── version.test.ts
    └── support/{fakeClock,fakeServer,fixtures,script}.ts

src/core/**/__tests__/              identity, uuid, storage, schema, condition, machine,
                                    http, queue, reporter, runtime
src/react/__tests__/                hooks.test.tsx, PitacoProvider.lifecycle.test.tsx
```

**Structure Decision**: um pacote único com três camadas por pasta (`core`, `ui`, `react` como
cola), e subpaths para o que não pode ir no ponto de entrada principal (adaptadores de storage,
que citam pacotes nativos em comentário, e o preview). `core/` não importa React nem UI, o que
permite testar a máquina, a fila e o runtime sem renderizar nada.

## Complexity Tracking

| Violação / risco | Por que é necessário | Alternativa mais simples rejeitada porque |
| --- | --- | --- |
| `PlacementGate` sem segundo caso nesta fatia | O controle de onde e quando (`004`) já estava desenhado e entraria sobre o mesmo ponto | Oferecer direto no runtime obrigaria a reescrever `consider()` na `004` |
| Cadeia serializada de gravação (`pipeline`) no runtime | Abertura precisa ser gravada antes dos eventos e do envio da mesma exibição, mesmo com storage assíncrono | Gravar em paralelo permitiria um envio persistido antes da abertura |

# Implementation Plan: SDK React Native — pré-visualização sem transporte

**Branch**: `005-sdk-preview-web` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-sdk-preview-web/spec.md`

## Summary

Publicar `@pitaco/react-native/preview`: `createPreviewController` (a máquina sobre um schema em
memória), `PitacoPreviewProvider` (o mesmo contexto do Provider, sem runtime) e
`<PitacoPreview />` (o Provider de preview mais `<PitacoSurveyContent />` inline, ou o
`PitacoSurfaceHost` em `bottom-sheet`/`modal`).

Três movimentos:

1. **Mesma interface de controlador** (D-01): `createPreviewController` implementa
   `SurveyController` com uma `SurveySession` sobre o schema normalizado; `usePitacoSurvey()` não
   sabe se está sobre o runtime ou sobre o preview.
2. **Contexto sem runtime** (D-02, D-03): `PitacoPreviewProvider` entrega `runtime: null`, o
   controlador, `reportRenderError` repassando a `onRenderError` e a configuração de UI; o
   `onEvent` entra já na criação do controlador.
3. **Componente declarativo** (D-04 a D-06): `<PitacoPreview />` é só composição; reinício por
   `resetKey`; contêiner do SDK opcional.

## Technical Context

**Language/Version**: TypeScript 5.9 estrito

**Primary Dependencies**: nenhuma nova; consumido no painel por `react-native-web`

**Storage**: N/A

**Testing**: Jest 29 + Testing Library; `fetch` espião em todos os testes do preview

**Target Platform**: navegador (via `react-native-web`), iOS e Android

**Project Type**: subpath de biblioteca React Native

**Performance Goals**: N/A

**Constraints**: nenhuma requisição de rede; nenhum storage persistente; nenhum módulo nativo

**Scale/Scope**: 1 subpath, 1 componente, 1 Provider, 1 fábrica de controlador

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | O subpath só importa máquina, schema, sessão e UI; roda em `react-native-web`. | ✅ |
| **II. Degradação Silenciosa** | Schema inválido não desenha nada; `onEvent` que lança é engolido; falha de renderizador vai a `onRenderError`. | ✅ |
| **III. O Servidor Decide** | Não há servidor no preview; nada de elegibilidade. | ✅ |
| **IV. Catálogo Fixo** | Mesmos eventos da máquina, só para o `onEvent`. | ✅ |
| **V. Sem Dado Pessoal** | Nada sai do dispositivo. | ✅ |
| **VI. Três Camadas** | O preview reaproveita core e UI sem cópia; implementa `SurveyController`. | ✅ |
| **VII. Acessível** | Mesma UI; no web, o foco vai para o elemento do título. | ✅ |
| **VIII. Teste É Contrato** | `preview.test.tsx`, `PitacoPreview.test.tsx`, `PitacoPreview.container.test.tsx`, e o teste de mesma sequência roda sobre o `PitacoPreviewProvider`. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

- **`<PitacoPreview />` também é exportado pelo ponto de entrada principal.** O app que já importa
  `@pitaco/react-native` não precisa de outro import; o subpath continua sendo o caminho que não
  arrasta o runtime, para o painel.
- **`presentation` deixou de ser só um rótulo.** Com `bottom-sheet`/`modal`, o preview abre o
  contêiner de verdade; o padrão continua `inline`, então quem usava o padrão não muda.

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/005-sdk-preview-web/
├── plan.md
├── research.md          # D-01 a D-07
├── data-model.md
├── quickstart.md
├── contracts/
│   └── preview-api.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
src/preview/
├── index.ts                 barril de @pitaco/react-native/preview
├── controller.ts            createPreviewController, PitacoPreviewProvider
├── PitacoPreview.tsx        <PitacoPreview />
└── __tests__/
    ├── preview.test.tsx
    ├── PitacoPreview.test.tsx
    └── PitacoPreview.container.test.tsx

src/react/SurfaceHost.tsx    alterado: props opcionais onFinish e thankYouDurationMs
src/ui/content/PitacoSurveyContent.tsx   foco no elemento do DOM em Platform.OS === 'web'
src/index.ts                 exporta PitacoPreview e PitacoPreviewProps
package.json                 exports "./preview"
```

**Structure Decision**: `controller.ts` separado de `index.ts` evita o ciclo `index.ts` →
`PitacoPreview.tsx` → `index.ts`.

## Complexity Tracking

Sem violação.

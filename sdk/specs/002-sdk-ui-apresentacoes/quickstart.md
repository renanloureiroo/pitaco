# Quickstart — validar `002-sdk-ui-apresentacoes`

Como provar que a UI padrão e as apresentações funcionam. Superfície em
[`contracts/ui-public-api.md`](./contracts/ui-public-api.md); comportamento de cada contêiner em
[`contracts/presentation-behavior.md`](./contracts/presentation-behavior.md).

## Pré-requisitos

- `cd sdk && npm ci && npm ci --prefix example`

---

## 1. O portão

```bash
cd sdk
npm run verify
```

## 2. Só esta fatia

```bash
# tema, textos, primitivos e isolamento
npx jest --ci src/ui/theme src/ui/strings src/ui/primitives src/ui/registry

# os seis renderizadores
npx jest --ci src/ui/questions

# conteúdo, níveis de customização e acessibilidade
npx jest --ci src/ui/content

# apresentações (gesto, insets, teclado, movimento reduzido) e o host
npx jest --ci src/ui/presentation src/react/__tests__/SurfaceHost
```

## 3. O que precisa estar coberto

| # | Cenário | Onde |
| --- | --- | --- |
| 1 | Folha abre e `survey_presented` só sai depois da entrada | `SurfaceHost.test.tsx` |
| 2 | Vias `close_button`, `backdrop`, `hardware_back`, `programmatic` | `SurfaceHost.test.tsx` |
| 3 | Via `swipe` pelo arrasto além do limiar; volta abaixo dele | `gesture.test.ts` |
| 4 | Movimento reduzido abre sem esperar a animação | `SurfaceHost.test.tsx`, `useReduceMotion.test.tsx` |
| 5 | Folha ancorada embaixo (`flex: 1` + `flex-end`) | `SurfaceHost.test.tsx` |
| 6 | Modal em tela cheia com os insets e voltar do Android | `SurfaceHost.test.tsx` |
| 7 | `inline` não desenha nada e não deixa contêiner preso | `SurfaceHost.test.tsx` |
| 8 | Teclado `padding`/`height` por plataforma | `keyboard.test.ts` |
| 9 | Insets: `getInsets` > `insets` > padrão da plataforma | `insets.test.ts` |
| 10 | Seis tipos: papel, `value` em vez de rótulo, tema escuro, erro anunciado, 44 pt | `src/ui/questions/__tests__/*` |
| 11 | Tema claro/escuro/inválido degradando com aviso; contraste | `theme.test.ts`, `customization.levels.test.tsx` |
| 12 | Textos substituídos; cinco slots substituídos | `strings.test.ts`, `customization.levels.test.tsx` |
| 13 | Renderizador e slot que lançam caem no padrão e reportam | `SafeCustom.test.tsx`, `customization.levels.test.tsx` |
| 14 | Desmonte em andamento dispensa com `navigation`, uma vez | `PitacoSurveyContent.test.tsx` |
| 15 | Foco vai para o título a cada navegação (sozinho, no sheet, no modal) | `a11y.focus.test.tsx` |
| 16 | Teto de escala de fonte no título e no texto livre | `a11y.fontScale.test.tsx` |
| 17 | Alça com 44 pt; agradecimento anunciado | `SurfaceHost.a11y.test.tsx`, `DefaultThankYou.test.tsx` |

## 4. No aparelho

No app de exemplo (`006-sdk-app-exemplo`), na tela "Testar pesquisas", escolher a forma **Sheet do
SDK**, abrir a pesquisa "Todos os tipos" e conferir, em iOS e Android:

- a folha abre ancorada embaixo, com a alça;
- arrastar a alça para baixo fecha; tocar no fundo fecha; o voltar do Android fecha sem sair da tela;
- no texto livre, o teclado sobe e a folha acompanha, com contador e "Enviar" à vista;
- tema claro e escuro trocando pelo seletor;
- com o leitor de tela ligado, o foco vai para o título a cada "Próxima".

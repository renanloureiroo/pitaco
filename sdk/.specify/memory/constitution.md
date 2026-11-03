<!--
Sync Impact Report
==================
Version change: (template não ratificado) → 1.0.0
Bump rationale: MAJOR inicial — primeira ratificação da constituição do SDK
`@pitaco/react-native`, com conteúdo concreto substituindo o scaffold de placeholders. Nenhum
princípio anterior existia para o SDK: até aqui ele seguia apenas as restrições combinadas na
abertura do projeto, que esta constituição torna obrigatórias e verificáveis.

Modified principles:
- [PRINCIPLE_1_NAME] → I. Zero Módulo Nativo (NÃO NEGOCIÁVEL)
- [PRINCIPLE_2_NAME] → II. Degradação Silenciosa
- [PRINCIPLE_3_NAME] → III. O Servidor Decide a Elegibilidade
- [PRINCIPLE_4_NAME] → IV. Catálogo de Eventos Fixo e Versionado
- [PRINCIPLE_5_NAME] → V. Sem Dado Pessoal
- (novo) → VI. Três Camadas, Um Core Só
- (novo) → VII. Acessível por Padrão
- (novo) → VIII. Teste É Contrato

Added sections:
- Stack e Restrições Técnicas (era [SECTION_2_NAME])
- Fluxo de Desenvolvimento e Portões de Qualidade (era [SECTION_3_NAME])

Removed sections: nenhuma.

Follow-up TODOs:
- Orçamento numérico de tamanho de bundle e de tempo de abertura da folha deliberadamente
  ausentes: entram por emenda quando houver medição em apps reais.
-->

# Pitaco SDK React Native Constitution

Esta constituição descreve o padrão já escrito no código do SDK `@pitaco/react-native` e o torna
obrigatório. Ela não é aspiracional: cada regra abaixo tem exemplo vivo em `src/` e, quase sempre,
um teste que falha se a regra for quebrada.

O SDK roda dentro do app de outra pessoa. Tudo aqui deriva disso: ele não pode pesar na
instalação, não pode derrubar nada, não pode decidir pelo servidor, não pode vazar dado de quem
usa o app e não pode mudar de comportamento conforme a aparência que o app escolher.

## Core Principles

### I. Zero Módulo Nativo (NÃO NEGOCIÁVEL)

Instalar o SDK é `npm install`, sem rebuild nativo, e ele funciona no Expo Go.

- As únicas dependências de runtime MUST ser `react` e `react-native`, declaradas como
  `peerDependencies` (`react >=18.2.0`, `react-native >=0.74.0`). `dependencies` MUST permanecer
  vazio; `src/__tests__/version.test.ts` falha se isso mudar.
- O código em `src/` MUST NOT importar `react-native-reanimated`,
  `react-native-gesture-handler`, `@gorhom/bottom-sheet`, `react-native-safe-area-context`,
  `@react-native-async-storage/async-storage` nem `react-native-mmkv`. A regra é aplicada por
  `no-restricted-imports` no `eslint.config.mjs`, não por revisão.
- O que o SDK precisa de uma dessas bibliotecas MUST ser recebido do app: o adaptador de storage
  recebe a instância (`createAsyncStorageAdapter(AsyncStorage)`, `createMmkvAdapter(mmkv)`), a
  safe area chega por `insets`/`getInsets`, e quem já usa gorhom ou React Navigation encaixa
  `<PitacoSurveyContent />` no próprio contêiner.
- Animação, gesto, teclado e voltar do Android MUST usar só o que o React Native traz (`Modal`,
  `Animated` com `useNativeDriver`, `PanResponder`, `KeyboardAvoidingView`, `BackHandler`,
  `AccessibilityInfo`).
- O app de exemplo (`example/`) MUST rodar no Expo Go consumindo o código-fonte do SDK. Se um
  cenário exigir build nativo, o defeito é do SDK.

**Rationale**: fricção de integração é o maior risco de adoção de um SDK de pesquisa. Um módulo
nativo transforma "testar o Pitaco" em rebuild, plugin de Babel e conflito de versão major com o
que o app já usa.

### II. Degradação Silenciosa

Nenhuma falha do Pitaco chega ao usuário final nem derruba a árvore do app.

- Nenhum método público MUST lançar nem rejeitar: `track`, `reset`, as ações do
  `usePitacoSurvey()` e os adaptadores de storage capturam tudo. Rede fora, timeout, corpo
  ilegível, status de erro e storage quebrado viram valores tratados (`HttpOutcome`,
  `SafeStorage`), nunca exceção.
- Tudo o que o SDK desenha MUST rodar dentro de `PitacoErrorBoundary`; renderizador ou slot
  substituído pelo app roda no próprio boundary e cai no padrão.
- Configuração inválida MUST desligar o SDK (sem `baseUrl` ou `apiKey`) ou cair no padrão (o
  resto), com `console.warn` em `__DEV__` explicando a correção, e silêncio em produção. O
  `Logger` é a única saída de aviso; `no-console` só permite `warn`.
- Uso incorreto pelo app (`unblock` sem `block`, `release` sem `defer`, hook fora do Provider)
  MUST virar aviso único em desenvolvimento e operação nula, nunca exceção.
- Erro lançado pelo `onEvent` do app MUST ser engolido e avisado uma vez; não é falha do SDK e
  não vai para o relatório de erro.

**Rationale**: o app hospedeiro não pode pagar pela indisponibilidade de um serviço acessório. O
aviso em desenvolvimento existe para que o silêncio de produção não esconda um erro de
integração.

### III. O Servidor Decide a Elegibilidade

O SDK exibe o que a API mandou; ele não decide se alguém deve ver uma pesquisa.

- Amostragem, histórico, cota, descanso, janela, segmentação e desempate MUST ficar no servidor.
  O SDK MUST NOT guardar histórico de exibição para filtrar pesquisas.
- O SDK avalia localmente apenas o que o contrato manda avaliar localmente: a `condition` entre
  perguntas de uma versão já entregue, falhando fechado.
- Controle de **onde e quando** (bloqueio de tela, adiamento, uma pesquisa por sessão de app) é
  do app e do SDK; controle de **se** é do servidor. O limite de sessão impede nova consulta,
  mas nunca reinterpreta a resposta do servidor.
- Tipo de pergunta desconhecido MUST ser pulado em silêncio; sem pergunta renderizável, o SDK
  MUST NOT exibir nem abrir exibição, e informa a supressão ao servidor.

**Rationale**: regra de elegibilidade duplicada no cliente diverge do servidor na primeira
mudança de política, e SDK antigo continuaria aplicando a regra velha por anos nos aparelhos.

### IV. Catálogo de Eventos Fixo e Versionado

Os eventos de interação são definidos pelo Pitaco, iguais para toda aplicação.

- O catálogo MUST morar num único módulo tipado (`src/catalog/events.ts`), com união
  discriminada por `type` e `data` fechado por tipo.
- O app hospedeiro MAY ouvir os eventos em `onEvent`; MUST NOT criar tipo novo nem alterar
  payload. Não existe API pública de emissão.
- Tipo, campo ou valor novo MUST vir com `CATALOG_VERSION` nova, e o servidor precisa aceitar as
  versões anteriores.
- Eventos que acontecem antes de existir exibição (pesquisa disponível, suprimida, bloqueada,
  adiada, liberada, vencida, limite de sessão) MUST usar o prefixo `placement_`, ir só para o
  `onEvent` e nunca entrar na fila nem no servidor.
- O teste de contrato (`src/catalog/__tests__/`) MUST falhar se um tipo existir no SDK e não no
  backend, ou o contrário, se a versão divergir, ou se o `data` de um tipo mudar.

**Rationale**: um catálogo aberto viraria uma ferramenta de analytics genérica, com custo de
armazenamento sem teto e sem comparabilidade entre aplicações. Fechado e versionado, ele é
contrato.

### V. Sem Dado Pessoal

O SDK descreve interação, nunca a pessoa.

- Nenhum evento MUST carregar conteúdo de texto livre: texto aparece só como `length`. Escolha
  aparece pelo `value` da opção ou pelo número, nunca pelo rótulo.
- O SDK MUST NOT enviar nada para ferramenta de terceiros, inclusive o rastreador de erro do app
  hospedeiro. O único canal de diagnóstico é `POST /collect/sdk-errors`, desligável.
- O relatório de erro MUST ser montado pelo SDK (etapa e nome do erro, nunca `error.message`),
  com contexto filtrado por lista de chaves permitidas e bloqueio de nomes que sugerem dado
  pessoal.
- `respondent.reference` MUST ser tratada como referência opaca e `attributes` como segmentação;
  o SDK aplica limites de formato e avisa, em desenvolvimento, que nenhum dos dois pode carregar
  dado pessoal.
- O log de desenvolvimento MUST NOT imprimir resposta, texto digitado nem chave inteira.

**Rationale**: o SDK roda em apps de terceiros sob LGPD; a forma mais barata de não vazar dado
pessoal é nunca tê-lo no payload.

### VI. Três Camadas, Um Core Só

```
core (headless)   máquina de estado, sessão, fila, transporte, identidade, tempo
ui (padrão)       renderizadores por tipo, tema, textos, slots, <PitacoSurveyContent />
presentation      bottom sheet (padrão), modal em tela cheia, inline
```

- A máquina de estado (`src/core/machine/machine.ts`) MUST ser a única forma de mudar o estado
  de uma exibição: selecionar, trocar, desmarcar, digitar, avançar, voltar, pular, dispensar e
  concluir são ações do core. Ela é função pura `transition(estado, ação, instante)`.
- Os eventos do catálogo MUST ser emitidos pelo core como consequência das transições, nunca por
  componente de UI. Consequência obrigatória: a UI padrão, uma UI com renderizador substituído e
  uma UI headless produzem a mesma sequência de eventos para o mesmo roteiro, e há teste que
  prova isso.
- A UI padrão MUST usar apenas a API pública (`usePitacoSurvey`, `usePitacoTheme`,
  `usePitacoStrings`). Ela não tem acesso privilegiado ao core.
- O core MUST NOT importar de `ui/` nem de `react/`; a apresentação MUST NOT ser exportada como
  API pública — o app escolhe por `presentation` ou monta a própria sobre
  `<PitacoSurveyContent />`.
- Runtime de verdade e preview MUST implementar a mesma interface `SurveyController`, para a UI
  não saber qual dos dois está por baixo.

**Rationale**: customizar a aparência nunca pode quebrar a medição. Se o tracking morasse na UI,
cada app que trocasse um componente mediria uma coisa diferente.

### VII. Acessível por Padrão

A UI padrão é usável com leitor de tela, fonte ampliada e movimento reduzido sem configuração.

- Todo controle MUST ter papel e rótulo de acessibilidade, e alvo de toque de ao menos 44 pt
  (`MIN_TOUCH_TARGET`), inclusive a alça do bottom sheet.
- O foco de acessibilidade MUST ir para o título da pergunta nova a cada navegação; erro de
  validação e agradecimento MUST ser anunciados.
- Texto MUST respeitar a escala de fonte do sistema com teto coerente
  (`DEFAULT_MAX_FONT_SIZE_MULTIPLIER`).
- Animação MUST ser cortada quando o sistema pede movimento reduzido.
- O tema MUST ser validado token a token e o contraste dos pares que a UI padrão desenha MUST
  ser verificado em desenvolvimento, com aviso abaixo de 4,5:1.

**Rationale**: pesquisa que exclui quem usa tecnologia assistiva mede uma amostra enviesada, além
de ser uma barreira para a própria pessoa.

### VIII. Teste É Contrato

Toda regra não óbvia nasce com teste que falha antes do código que a satisfaz.

- Testes MUST ficar em `__tests__/` ao lado do módulo, com Jest e `@testing-library/react-native`,
  descrições em português e asserção sobre comportamento observável (eventos, chamadas de rede,
  papéis e rótulos), nunca sobre estrutura interna de componente.
- Tempo MUST ser injetado (`Clock`, `Timers`) e controlado por relógio falso; rede MUST ser
  falsa (`fakeServer` com a idempotência do contrato). Nenhum teste toca a rede real: o
  `jest.setup.ts` troca o `fetch` global por um que falha.
- Correção de defeito MUST entrar com o teste que o reproduz, escrito antes da correção.
- Os roteiros de interação reutilizáveis (`src/__tests__/support/script.ts`) são a referência
  única de "mesma sequência de eventos".

**Rationale**: o SDK roda em versões de React Native, plataformas e apps que o projeto não
controla. O teste é o único registro executável de como ele deve se comportar em cada um.

## Stack e Restrições Técnicas

- **Linguagem**: TypeScript 5.9 em modo estrito, com `noUncheckedIndexedAccess`. `any` é
  proibido; use `unknown` com estreitamento.
- **Build**: `react-native-builder-bob` gera `lib/module`, `lib/commonjs` e `lib/typescript`. O
  `package.json` declara `exports` para `.`, `./storage/async-storage`, `./storage/mmkv` e
  `./preview`, com `sideEffects: false`. Subpath novo é emenda de superfície pública.
- **Contrato com o backend**: os tipos das rotas `/collect` são gerados do OpenAPI com
  `openapi-typescript` (`npm run gen:api`), a partir do snapshot versionado
  `openapi/pitaco.json`. O resto do SDK importa de `src/api/types.ts`, nunca do gerado direto.
- **Testes**: Jest 29 com `@react-native/jest-preset` e `@testing-library/react-native` 14.
- **Lint**: ESLint em flat config com `recommendedTypeChecked` e `eslint-plugin-react-hooks` 7.
  Estado de ref só é escrito em efeito; estado derivado é ajustado durante o render.
- **Ambiente**: Node 20 ou mais novo para desenvolver; React 19 e React Native 0.87 em
  desenvolvimento, sem elevar os mínimos dos peers.
- **Idioma**: comentários, mensagens de aviso, rótulos padrão e descrições de teste em português;
  identificadores, tipos de evento e campos de payload em inglês.
- Dependência nova de desenvolvimento MUST ser justificada; de runtime é proibida (Princípio I).

## Fluxo de Desenvolvimento e Portões de Qualidade

**Ordem de trabalho** para uma funcionalidade nova:
1. Contrato: tipo do catálogo, interface pública ou rota consumida.
2. Core: teste da máquina, da fila ou do runtime sobre relógio e servidor falsos; depois o código.
3. UI: teste de componente pelo comportamento (papel, rótulo, evento emitido); depois o código.
4. Superfície pública em `src/index.ts` ou no subpath, com tipo exportado.
5. Prova no app de exemplo, contra o backend local.

**Portão obrigatório** — `cd sdk && npm run verify`, que roda em sequência:
- `eslint .` sem erro nem aviso;
- `tsc --noEmit` sem erro;
- `jest --ci` verde, incluindo os testes de contrato do catálogo e de mesma sequência;
- `bob build` com os três alvos;
- `typecheck` e `lint` do app de exemplo.

A integração contínua (`.github/workflows/sdk.yml`) roda o mesmo `verify` e um `expo export` do
exemplo para iOS e Android, provando que o bundle monta sem módulo nativo exigido pelo SDK.
Nenhum merge passa com o portão vermelho.

**Review** verifica explicitamente os oito princípios. Violação consciente é aceitável apenas se
justificada no PR com o motivo e o custo da alternativa.

**Complexidade** precisa se pagar: abstração nova entra quando existe o segundo caso concreto que
a exige. O `PlacementGate` existiu como ponto de extensão antes do bloqueio e do adiamento porque
o desenho já previa os dois.

## Governance

Esta constituição prevalece sobre qualquer outra prática do SDK. Onde ela conflitar com um hábito,
um exemplo antigo ou uma sugestão de ferramenta, ela vence.

**Emenda** é feita por PR que altera este arquivo, descrevendo a mudança, o motivo e o impacto no
código existente. Emenda que invalida código já escrito acompanha o plano de migração.

**Versionamento** semântico:
- **MAJOR** — princípio removido ou redefinido de forma incompatível com o código existente.
- **MINOR** — princípio ou seção nova, ou orientação materialmente ampliada.
- **PATCH** — esclarecimento, correção de texto, refinamento sem mudança de significado.

**Conformidade** é verificada em todo review. Os comandos do Spec Kit (`/speckit-plan`,
`/speckit-tasks`, `/speckit-implement`, `/speckit-analyze`) leem este arquivo em tempo de execução
e devem recusar planos que o violem. O contrato `/collect` consumido pelo SDK é o de
`backend/docs/backend/contrato-sdk.md`; mudança nele é coordenada com a constituição do backend.

**Version**: 1.0.0 | **Ratified**: 2026-09-12 | **Last Amended**: 2026-09-12

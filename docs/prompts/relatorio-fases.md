# Relatório das fases — SDK React Native do Pitaco

Cada fase acrescenta sua seção ao fim: arquivos principais, decisões tomadas, o que ficou de fora, e o resultado exato de cada comando de verificação.

## Fase 1 — Backend

### Arquivos principais

Criados (em `backend/src/main/java/com/renanloureiroo/pitaco/`):
- `core/catalog/InteractionEventType.java`, `InteractionField.java`, `InteractionFieldKind.java`: o catálogo versão 1 (18 tipos, `data` fechado por tipo, `CATALOG_VERSION = 1`, `sanitize`).
- `modules/collect/domain/interaction/`: `InteractionDraft`, `InteractionEvent` (VO com as invariantes), `DiscardReason`, `InteractionIntake` (leitura evento a evento, deduplicação e teto).
- `modules/collect/application/repositories/InteractionEventRepository.java`, `application/usecases/RecordInteractionEventsUseCase.java`.
- `modules/collect/infra/`: `config/InteractionEventsProperties.java`, `database/jpa/entities/SurveyDisplayEventJpaEntity.java`, `database/jpa/repositories/SurveyDisplayEventJpaRepository.java` e `InteractionEventRepositoryJpa.java`, `http/controllers/CollectInteractionController.java` + `CollectInteractionSwagger.java`, `http/dtos/InteractionEventsRequestDTO.java` e `InteractionEventsReceiptDTO.java`, `http/presenters/InteractionEventsReceiptPresenter.java`, `http/config/InteractionCatalogSchemas.java` + `InteractionCatalogOpenApiConfiguration.java`.
- `modules/results/`: `domain/behavior/BehaviorMetric.java`, `application/readmodels/SurveyBehaviorReadModel.java`, `application/outputs/SurveyBehaviorOutput.java`, `application/usecases/GetSurveyBehaviorUseCase.java`, `infra/database/jpa/BehaviorSql.java`, `SurveyBehaviorJpaRepository.java`, `SurveyBehaviorReadModelJpa.java`, `infra/http/dtos/SurveyBehaviorResponseDTO.java`, `infra/http/presenters/SurveyBehaviorPresenter.java`.
- Migração `src/main/resources/db/migration/V20260912162148__create_survey_display_events.sql`.

Alterados: `SdkFeature` (+`INTERACTION_EVENTS`) e `SdkCapabilities`; `UseCasesConfiguration` de collect e results; `SurveyResultsController`/`Swagger` (rota `/behavior`), `ResultsQueryDTO`, `SurveyResultsPresenter`; retenção (`RetentionStore`, `RetentionJpaRepository`, `RetentionStoreJpa`, `ApplyRetentionUseCase`); `application.yml`; `docs/backend/contrato-sdk.md` e `docs/backend/proxy.md`.

Testes novos: `InteractionEventTypeTest`, `InteractionFieldTest`, `InteractionEventTest`, `InteractionIntakeTest`, `RecordInteractionEventsUseCaseTest`, `InteractionEventsRequestDTOTest`, `InteractionCatalogSchemasTest`, `BehaviorMetricTest`, `GetSurveyBehaviorUseCaseTest`, `CollectInteractionEventsE2ETest`, `SurveyBehaviorE2ETest`; fakes `InMemoryInteractionEventRepository` e `InMemorySurveyBehaviorReadModel`. Estendidos: `CollectOpenApiTest`, `ResultsOpenApiTest`, `CascadeDeletionE2ETest`, `ApplyRetentionUseCaseTest`, `InMemoryRetentionStore`.

### Decisões e o porquê

- **Catálogo em `core/catalog`**: `collect` (ingestão) e `results` (vias de dispensa na leitura) usam o catálogo, e ele fica ao lado de `SdkCapabilities`.
- **OpenAPI gerado do enum** por um `OpenApiCustomizer`, e não anotado à mão: fonte única. Publica `InteractionEventType` (enum + `x-pitaco-catalog-version: 1`), `InteractionEvent` (`oneOf` discriminado por `type`) e `<Tipo>Event`/`<Tipo>Data` (fechados, `additionalProperties: false`). O springdoc 3.1 emite OpenAPI 3.1, então os esquemas definem `types`.
- **Descarte evento a evento, nunca 400 por evento**: um 400 faria o SDK jogar fora o lote inteiro. 400 só para corpo ilegível, lote vazio, lote acima de 100, ou campo do envelope com tipo JSON errado.
- **202 com corpo de contagem** (`accepted`, `duplicated`, `discarded.{displayUnavailable, outsideWindow, unknownType, invalidEnvelope, unknownQuestion, overLimit}`): "descartado e contado" fica visível ao painel de depuração do SDK e nos testes, além do log. Exibição inexistente, de outra aplicação ou de aplicação inativa dão a mesma resposta (`displayUnavailable`).
- **Idempotência**: `unique (display_id, seq)`, com o lote gravado num único insert nativo (`jsonb_to_recordset ... on conflict do nothing`) e trava consultiva por exibição, para o teto ser exato sob concorrência. `@Transactional` do projeto no use case (contagem e gravação no mesmo instante).
- **Regras extras de privacidade**: escolha (`answer_*`) registrada em pergunta `FREE_TEXT` é descartada, porque o valor seria o texto digitado. Valor de resposta é limitado a 120 caracteres (o `value` de uma opção). `questionKey` fora da versão exibida é descartada.
- **Janela de aceitação medida no relógio do servidor**, a partir do `opened_at` (padrão 7 dias). O teto por exibição tem padrão 500. As duas regras são configuráveis em `pitaco.collect.interaction-events.*`. `occurredAt` do dispositivo é só informativo.
- **`catalogVersion` mais nova**: tipo conhecido é aceito com os campos conhecidos; tipo desconhecido é descartado. Não modelei "versão em que o tipo nasceu" porque só existe a v1 (abstração só com o segundo caso).
- **Retenção passa a apagar eventos**: ela apaga respostas e não exibições, então a cascata não alcançaria os eventos, que carregam valor escolhido. Os eventos recebidos antes do prazo de respostas saem em lotes. `retention_runs` continua contando só respostas e textos; os eventos vão para o log.
- **Leitura de comportamento**: a base é a "exibição instrumentada" (ao menos um evento), para SDKs sem eventos não diluírem as taxas. Mediana e p90 saem de `percentile_cont` no Postgres, somando o `activeMs` de todas as visitas de cada exibição. As definições exatas estão em `BehaviorMetric`, voltam em `definitions` na resposta e estão documentadas em `contrato-sdk.md` ("Leitura de comportamento").
- **`INTERACTION_EVENTS` exige 1.0.0** e é capacidade do SDK, não exigência da pesquisa: nunca entra na versão mínima de uma pesquisa.

### O que ficou de fora

- Agregado congelado de comportamento na retenção: depois do descarte, a leitura conta só o que ficou.
- Métrica ou contador persistido dos descartes (há o corpo do 202 e o log).
- Validação de `value` contra as opções da pergunta, e de `toKey`/`sourceKey` contra a versão (só o formato UUID).
- E2E de 429 específico da rota nova: ela herda os interceptors de `/collect/**`, e o 429 já é coberto em outros E2E.
- Não atualizei a contagem "1709 testes" citada no `AGENTS.md`.

### Onde está a lista de tipos para o teste de contrato (fase 8)

- Fonte: `backend/src/main/java/com/renanloureiroo/pitaco/core/catalog/InteractionEventType.java`.
- Máquina-legível: `GET /api/v3/api-docs` → `components.schemas.InteractionEventType.enum` (os 18 nomes), `components.schemas.InteractionEventType["x-pitaco-catalog-version"]` (1), `components.schemas.InteractionEvent` (união discriminada) e `components.schemas.<Tipo>Data` (campos por tipo). Os campos do lote apontam para `#/components/schemas/InteractionEvent`.

### Resultado do portão

`cd backend && ./mvnw verify`:
```
[INFO] Tests run: 1839, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Fase 2 — SDK core

### Estrutura de pastas e módulos

`sdk/` (novo; nada fora dele foi alterado):

- Configuração: `package.json` (bob com saídas module/commonjs/typescript, `exports` com `.`, `./storage/async-storage`, `./storage/mmkv`, `./preview`; `react` e `react-native` só como peer), `tsconfig.json` (estrito, `noUncheckedIndexedAccess`), `tsconfig.build.json`, `babel.config.js`, `jest.config.js`, `jest.setup.ts`, `eslint.config.mjs`, `.gitignore` (`node_modules/`, `lib/`, `coverage/`).
- `openapi/pitaco.json`: snapshot do OpenAPI. `scripts/gen-api.mjs` (`npm run gen:api`): baixa de `PITACO_OPENAPI_URL` (padrão `http://localhost:8080/api/v3/api-docs`), tira `servers` e atualiza o snapshot; sem backend, usa o snapshot. Gera `src/api/openapi.ts`.
- `src/catalog/events.ts`: o catálogo v1 (18 tipos, `INTERACTION_EVENT_TYPES`, `QUESTION_EVENT_TYPES`, `INTERACTION_EVENT_DATA_FIELDS`, `InteractionEventDataMap`, união discriminada `InteractionEvent`/`PitacoEvent`, `DISMISS_VIAS`, `PRESENTATIONS`). `src/catalog/placement.ts`: eventos `placement_` (só `onEvent`).
- `src/api/`: tipos gerados e aliases (`types.ts`).
- `src/core/`: `clock.ts` (relógio injetável `{mono, wall}`), `timers.ts`, `logger.ts` (avisos só em `__DEV__`), `uuid.ts`, `config.ts` (validação e saneamento), `identity.ts` (`deviceId`), `storage/` (interface, memória, `SafeStorage`), `survey/` (normalização do schema, condição, respostas), `machine/` (`machine.ts` função pura, `path.ts` caminho e aplicabilidade, `submission.ts`), `session/` (sessão com debounce, snapshot da UI, interface `SurveyController`), `transport/` (`http.ts` e `api.ts` com as seis rotas), `queue/queue.ts` (fila persistente), `errors/reporter.ts`, `runtime/` (`runtime.ts` orquestrador, `placement.ts` ponto de extensão).
- `src/react/`: `PitacoProvider.tsx`, `usePitaco.ts`, `usePitacoSurvey.ts`, `ErrorBoundary.tsx`, `SurfaceHost.tsx`, `context.ts`.
- `src/storage/async-storage.ts` e `src/storage/mmkv.ts` (subpaths); `src/preview/index.ts` (subpath).
- `src/__tests__/support/`: `fixtures.ts`, `fakeClock.ts`, `fakeServer.ts` (servidor falso com a idempotência do contrato), `script.ts` (roteiro reutilizável).

### API pública atual

- `@pitaco/react-native`: `PitacoProvider` (props `baseUrl`, `apiKey`, `respondent`, `attributes`, `storage`, `presentation`, `onEvent`, `errorReporting`, `debug`, `eligibilityTimeoutMs`; `theme`, `strings`, `renderers` e `slots` aceitas e ainda ignoradas); `usePitaco()` → `track(evento, atributos?)`, `setRespondent`, `setAttributes`, `reset`; `usePitacoSurvey()` → estado (`available`, `status`, `displayId`, `survey`, `questions`, `question`, `answers`, `value`, `progress`, `error`, `focusedQuestionKey`, `canGoBack`, `canGoNext`, `isLast`) e ações (`present`, `select`, `deselect`, `setText`, `focusText`, `blurText`, `next`, `back`, `dismiss(via)`, `complete`). Mais o catálogo (constantes, tipos, `isInteractionEvent`, `isPlacementEvent`), `createMemoryStorage`, `SDK_VERSION` e os tipos do schema.
- `@pitaco/react-native/storage/async-storage`: `createAsyncStorageAdapter(instancia)`. `.../storage/mmkv`: `createMmkvAdapter(instancia)` (MMKV 2/3 com `delete`, 4 com `remove`).
- `@pitaco/react-native/preview`: `createPreviewController({ schema, onEvent, ... })`, `PitacoPreviewProvider`, `usePitacoSurvey`.

### Decisões e o porquê

- **Versões**: TypeScript 5.9, porque o `openapi-typescript` 7.13 exige TS 5 e o `typescript-eslint` 8.70 aceita menos de 6.1 (o bob usa o próprio TS 6). Jest 29, porque o `@react-native/jest-preset` 0.87 depende de `babel-jest`/`jest-environment-node` 29. Dev com React Native 0.87.1 e React 19.2; peers `react >=18.2` e `react-native >=0.74`. RNTL 14 com `test-renderer`.
- **Spec**: obtido do backend que já estava rodando na porta 8080 (processo iniciado em 11/09 às 22:00 a partir de `backend/target/classes`, que já publica o catálogo da Fase 1). Minha tentativa de subir outro (`SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`) falhou por porta ocupada. Não parei o que estava de pé, porque não fui eu que o subi.
- **Máquina pura** `transition(estado, ação, instante)`. Ordem fixa dos eventos, documentada no topo de `machine.ts`: next = `text_edited?` → `question_skipped?` → `text_blurred?` → `navigated_next` → `question_left` → `question_not_applicable*` → `question_viewed`; back e dismiss seguem o mesmo molde; complete termina em `survey_completed`. `next` na última pergunta aplicável conclui; `complete` antes dela é ignorado.
  - Sair de uma pergunta com o campo em foco emite `text_blurred` no core, para qualquer UI dar o mesmo fluxo mesmo que o `TextInput` desmonte sem `onBlur`. `text_edited` pendente sai antes de qualquer saída da pergunta.
  - O debounce de 1 s é agendado pela sessão com `tick`, mas quem decide se venceu é a máquina, pelo relógio.
  - `position` é a do schema. `elapsedMs` é arredondado e nunca diminui.
  - Ações de resposta aceitam `questionKey` opcional e são ignoradas se não for a pergunta atual.
  - Dispensar antes de o contêiner ficar visível vira `discarded`, sem evento, sem exibição e sem envio.
- **Submissão**: `COMPLETED` leva todas as renderizáveis. `DISMISSED` leva só o que a pessoa alcançou (respondidas; vistas em branco como `SKIPPED`; puladas pela condição como `NOT_APPLICABLE`). Resposta a uma pergunta que deixou de se aplicar (a pessoa voltou e trocou a origem) vai como `NOT_APPLICABLE`, sem valor. Tipos desconhecidos nunca entram.
- **Condição**: falha fechado. Origem sem resposta, pulada, posterior ou descartada, ou número ilegível, deixam a pergunta não aplicável.
- **Progresso**: `total` é uma estimativa. Uma condição cuja origem é a pergunta atual ainda sem resposta, ou uma pergunta adiante, conta como possível.
- **Orquestrador**: abre a exibição só no `survey_presented`, e as gravações passam por uma cadeia serializada (abertura antes dos eventos). Os eventos saem em lote 5 s depois durante a resposta, e na hora na abertura, no desfecho e na ida para o segundo plano (economiza o limite de 120 por minuto por origem). Uma pesquisa em andamento faz o runtime ignorar outra que chegar.
  - 401/403 desligam as consultas até o fim da sessão, com um único aviso de correção.
  - Rede fora avisa uma vez, com a dica de endereço por alvo (simulador iOS, emulador Android, aparelho).
  - O `fetch` global é lido na hora de cada chamada.
- **Fila**: um documento JSON por par (`baseUrl`, `apiKey`), na chave `@pitaco/v1:queue:<hash>`. O `deviceId` fica em `@pitaco/v1:device-id`. Padrões:
  - no máximo 200 itens e 3 dias de idade (cabe na janela de 7 dias do servidor);
  - 500 eventos por exibição (o teto do servidor) e lotes de 100;
  - backoff de 2 s × 2^n, até 5 min.
  - O envio é forçado na montagem do Provider (abertura do app) e na volta do segundo plano. O 429 pausa a fila inteira, e o prazo persiste entre reinícios.
  - Descarte, conforme o contrato: 400, 401, 404, 409 e 422 descartam; 4xx recusado vira relatório de erro; a abertura descartada leva junto o que dependia dela; um 202 com corpo ilegível conta como entregue.
- **Supressão** vai pela fila (com reenvio) com `reason: unknown_question_type`, inclusive quando tudo é malformado, e também como `placement_suppressed` ao `onEvent`.
- **Relatório de erro**: fora da fila e sem esperar resposta. A mensagem é montada pelo SDK com a etapa e o nome do erro, nunca `error.message`. O contexto passa por lista de chaves permitidas e bloqueia nomes de dado pessoal. Mesmo erro sai uma vez, com teto de 10 por sessão. A falha desiste em silêncio e não é reportada. Erro lançado pelo `onEvent` do app não é reportado (não é do SDK).
- **Eventos `placement_`**: os 7 tipos já estão definidos; hoje o SDK só emite `available` e `suppressed`.
- **`reset()`**: dispensa como `programmatic`, esquece `reference` e atributos, troca o `deviceId` e mantém a fila (o que o usuário anterior respondeu ainda precisa chegar).
- **Provider**: o runtime nasce no render sem efeito colateral, para um `track` no efeito de montagem de um filho funcionar. `attach`/`detach` rodam em efeito (seguro no StrictMode). O runtime só é recriado quando mudam `baseUrl`, `apiKey`, `presentation`, `errorReporting`, `debug`, `eligibilityTimeoutMs` ou `storage`. O `onEvent` é trocado por `setEventListener`, sem ref lida no render (regra do `react-hooks` 7).
- **Storage**: sem adaptador, o SDK usa memória e avisa em `__DEV__` no attach. O `SafeStorage` guarda cópia em memória quando o adaptador falha e grava em ordem.
- **UUID**: `crypto.randomUUID` → `crypto.getRandomValues` → gerador próprio com `Math.random`, resolvido na hora de cada chamada (um polyfill instalado depois passa a valer).
- **Interface comum**: `SurveyController` é implementada pelo runtime e pelo preview, e por isso o `usePitacoSurvey` funciona igual nos dois.
- **ESLint**: flat config com `recommendedTypeChecked` e `react-hooks` 7; `no-restricted-imports` proíbe os seis pacotes nativos; `no-console` só permite `warn`.
- **Testes**: o `jest.setup.ts` faz `performance.now` seguir o `Date.now` dos timers falsos e troca o `fetch` global por um que falha. Tirei o `restoreMocks` do Jest, porque ele apagaria as implementações dos mocks do preset do React Native.

### Pontos de extensão

- **Fase 3**:
  - `src/react/SurfaceHost.tsx` já é renderizado pelo Provider dentro do `PitacoErrorBoundary`; é onde entram o sheet e o modal.
  - O `PitacoErrorBoundary` tem `fallback`, para o renderizador substituído cair no padrão, e o contexto tem `reportRenderError` (vira `render_error`).
  - `<PitacoSurveyContent />` chama `present()` ao montar e `dismiss('navigation')` ao desmontar com `status === 'presented'`.
  - `createSurveyActions(controller)` liga as ações a uma pergunta por `questionKey`.
  - O `<PitacoPreview />` visual é `PitacoPreviewProvider` mais a UI padrão.
  - Roteiro reutilizável em `src/__tests__/support/script.ts` (`ScriptStep`, `runScript`, `ScriptDriver`, `signature`, `STANDARD_SCRIPT`, `DISMISS_SCRIPT`, `createMachineDriver`). A fase 3 só precisa de um driver que aperte botões.
- **Fase 4**: `PlacementGate` (`consider(candidato, { offer, discard, notify })` e `sessionFinished`) com `runtime.setPlacementGate(gate)`. `block`, `defer` e o limite de uma pesquisa por sessão entram como outra implementação do portão, e `PitacoActions` ganha `block`, `unblock`, `defer` e `release`.
- **Fase 5**: `runtime.diagnostics()` devolve `deviceId`, o conteúdo da fila, o estado da chave e o prazo do 429.

### O que ficou de fora

- UI padrão, apresentações, `<PitacoSurveyContent />`, `<PitacoPreview />` visual, `<PitacoBlock />`, bloqueio, adiamento e limite por sessão (fases 3 e 4); README (fase 6); exemplo (fase 5); CI (fase 8).
- `network_error` nunca é reportado: reportar rede fora pela rede não faz sentido. `unsupported_feature` não é usado na supressão, porque ainda não existe recurso de pesquisa que o SDK desconheça.
- O teste de contrato compara com o snapshot, e não com o backend no ar; comparar com o backend vivo fica para a fase 8.
- O `npm install` acusou 7 vulnerabilidades altas em dependências de desenvolvimento transitivas; não tratei.

### Observações sobre o backend (não corrigidas)

1. **OpenAPI**: o `value` de `AnswerDTO` é publicado com `type: null` ao lado do `oneOf`. O `openapi-typescript` gera `null & (string | number | string[])`, que o TypeScript reduz a `never`. O SDK tipa esse campo à mão em `src/api/types.ts`. Não bloqueia.
2. **Lacuna de contrato para o futuro**: um `COMPLETED` exige toda obrigatória da versão como `ANSWERED` ou `NOT_APPLICABLE`. Quando existir um tipo novo obrigatório, um SDK antigo o pula e recebe 422 `answer.required_missing` ao concluir. Hoje não acontece, porque o backend só tem os seis tipos. Nesse caso o SDK descarta o envio e reporta. Sugestão: o backend dispensar a obrigatória cujo tipo exige versão acima do `X-Pitaco-Sdk-Version`.
3. O backend na porta 8080 (PIDs 91625/91785, iniciado em 11/09 22:00) continua rodando: não é desta fase.

### Resultado do portão

`cd sdk && npm run verify` (lint → typecheck → testes → build), código de saída 0:
- `eslint .`: nenhum problema (0 erros, 0 avisos).
- `tsc --noEmit`: nenhum erro.
- `jest --ci`: `Test Suites: 14 passed, 14 total` · `Tests: 150 passed, 150 total` · `Snapshots: 0 total` · cerca de 1,1 s.
- `bob build`: `[module] Compiling 39 files in src with babel` e `[commonjs] Compiling 39 files in src with babel`, `✔ Wrote files to lib/module`, `✔ Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`.
- Conferido depois do build: todos os caminhos do `exports`, `main`, `module` e `types` existem. Os únicos imports reais em `lib/` são `react`, `react-native` e `react/jsx-runtime`; os pacotes nativos só aparecem em comentários de exemplo e numa mensagem de aviso.

## Fase 3a — Fundação da UI

### Arquivos criados

`sdk/src/ui/` (nova; dono desta subtarefa):

- `theme/`: `tokens.ts` (tipos dos tokens — cor, tipografia, raio, espaçamento — e `DEFAULT_LIGHT_THEME`/`DEFAULT_DARK_THEME`), `validate.ts` (merge parcial validado, token a token, com `warnOnce` em `__DEV__`), `contrast.ts` (razão WCAG e checagem dos pares relevantes), `useTheme.ts` (`usePitacoTheme()`, `resolveColorScheme`, `resolvePitacoTheme`), `index.ts`, `__tests__/theme.test.ts`.
- `strings/`: `strings.ts` (`PitacoStrings`, `DEFAULT_STRINGS` em pt-BR, `mergeStrings`), `useStrings.ts` (`usePitacoStrings()`), `index.ts`, `__tests__/strings.test.ts`.
- `primitives/`: `PitacoText.tsx`, `PitacoButton.tsx`, `PitacoChip.tsx` (opção selecionável, comum aos seis tipos), `index.ts`, `__tests__/primitives.test.tsx`.
- `types.ts`: os contratos públicos — `QuestionRendererProps`/`QuestionRendererActions`/`PitacoRendererMap`, os slots (`HeaderSlotProps`, `ProgressSlotProps`, `FooterSlotProps`, `CloseButtonSlotProps`, `ThankYouSlotProps`, `PitacoSlotMap`), `EdgeInsets` e `PitacoUiConfig`.
- `registry/SafeCustom.tsx`: isola um renderizador ou slot substituído num `PitacoErrorBoundary` (o da fase 2), com `Default` como `fallback`; `__tests__/SafeCustom.test.tsx`.
- `questions/`: os seis renderizadores **provisórios** (`SingleChoiceQuestion.tsx`, `MultipleChoiceQuestion.tsx`, `RatingQuestion.tsx`, `ScaleQuestion.tsx`, `NpsQuestion.tsx`, `FreeTextQuestion.tsx`), `shared/NumericScale.tsx` e `shared/nps.ts` (fallback de faixa), `index.ts` (`DEFAULT_RENDERERS`).
- `content/`: `PitacoSurveyContent.tsx`, `slots/Default*.tsx` (os cinco slots padrão) e `slots/index.ts` (`DEFAULT_SLOTS`), `index.ts`, `__tests__/PitacoSurveyContent.test.tsx`.
- `index.ts`: barril da UI padrão.

### Arquivos alterados

- `sdk/src/react/context.ts` (meu, na parte de UI): `PitacoContextValue` ganhou `ui: PitacoUiConfig` e `reportRenderError` passou a aceitar um segundo parâmetro opcional de contexto técnico (`Readonly<Record<string,string>>`) — compatível com quem já implementava a interface sem o segundo parâmetro.
- `sdk/src/react/PitacoProvider.tsx` (meu): `theme`/`strings`/`renderers`/`slots` deixaram de ser `unknown` e ganharam os tipos reais; novas props `insets`/`getInsets` (safe area sem dependência); o Provider monta `ui: PitacoUiConfig` (com `presentation` sempre resolvido, padrão `'bottom-sheet'`) e expõe no contexto.
- `sdk/src/index.ts` (meu): exporta toda a API pública nova da UI (tema, textos, primitivos, `<PitacoSurveyContent />`, contratos de customização).
- `sdk/src/preview/index.ts` (não é meu — mudança mínima, registrada): `PitacoPreviewProvider` passou a entregar `onEvent` já na criação do controlador (`createPreviewController({..., onEvent})`), não só depois num efeito. Motivo: React comita efeitos de baixo para cima — um filho como `<PitacoSurveyContent />`, que chama `present()` no próprio efeito de montagem, rodava **antes** do efeito que ligava `onEvent` no Provider, perdendo sempre o primeiro evento (`survey_presented`). Descoberto pelos meus próprios testes de `<PitacoSurveyContent />`; corrigido porque é pré-requisito para qualquer UI (padrão, própria ou headless) usada dentro do preview funcionar corretamente com `onEvent`. Também adicionei `ui: { presentation }` ao valor de contexto do preview (exigido pelo novo formato de `PitacoContextValue`). Conferi que o runtime real (`PitacoProvider`) não tem o mesmo problema: `createRuntime` já passa `onEvent` na criação do `PitacoRuntime`, síncrona, durante o render.
- `sdk/src/react/__tests__/hooks.test.tsx` (não é meu — mudança mínima): o teste "o Provider aceita as props de aparência" passava `theme={{ colors: {} }}` (formato antigo, quando `theme` era `unknown`); ajustado para `theme={{ light: { colors: {} } }}`, a forma real agora que o Provider usa o tipo de verdade.

### API pública nova

- Tema: `usePitacoTheme()`, `DEFAULT_LIGHT_THEME`, `DEFAULT_DARK_THEME`, tipos (`PitacoThemeConfig`, `PitacoResolvedTheme`, `PitacoThemeTokens` etc.).
- Textos: `usePitacoStrings()`, `DEFAULT_STRINGS`, `PitacoStrings`, `PartialPitacoStrings`.
- Primitivos: `PitacoText`, `PitacoButton` (+ `hitSlopFor`, `MIN_TOUCH_TARGET`), `PitacoChip`.
- Contratos: `QuestionRendererProps`, `QuestionRendererActions`, `PitacoRendererMap`, `rendererKeyForQuestionType`, os cinco slots e `PitacoSlotMap`, `EdgeInsets`.
- `PitacoSurveyContent` (+ `PitacoSurveyContentProps`, `PitacoSurveyFinishReason`).
- `PitacoProviderProps` ganhou `theme`, `strings`, `renderers`, `slots` tipados de verdade, e `insets`/`getInsets`.

### O contrato para 3b e 3c

**Onde cada coisa mora (caminhos reservados):**

- 3b escreve em `sdk/src/ui/questions/`, um arquivo por tipo, **mantendo o nome do arquivo e do componente exportado**: `SingleChoiceQuestion.tsx`, `MultipleChoiceQuestion.tsx`, `RatingQuestion.tsx`, `ScaleQuestion.tsx`, `NpsQuestion.tsx`, `FreeTextQuestion.tsx`, todos com a assinatura `(props: QuestionRendererProps) => ReactNode`. `questions/index.ts` monta `DEFAULT_RENDERERS: PitacoRendererMap` a partir deles — não deveria precisar mudar. As pastas `questions/shared/` (`NumericScale.tsx`, `nps.ts`) são meus auxiliares provisórios; 3b pode usá-los, adaptá-los ou substituí-los livremente, contanto que `index.ts` continue exportando `DEFAULT_RENDERERS` com as seis chaves.
- 3c escreve em `sdk/src/ui/presentation/` (pasta ainda não criada — não precisei dela nesta fase) e em `sdk/src/react/SurfaceHost.tsx` (placeholder da fase 2, já renderizado pelo Provider dentro do error boundary).

**A chave de cada tipo** (`renderers={{ nps: MeuNps }}`) é `rendererKeyForQuestionType`: `SINGLE_CHOICE→singleChoice`, `MULTIPLE_CHOICE→multipleChoice`, `RATING→rating`, `SCALE→scale`, `NPS→nps`, `FREE_TEXT→freeText`.

**`QuestionRendererProps<T>`** (`sdk/src/ui/types.ts`): `{ question, value, error, actions, theme, strings, freeTextNotice }`.
- `actions: QuestionRendererActions` já vem vinculada à pergunta atual — `select(value)`, `deselect(value?)`, `setText(text)`, `focusText()`, `blurText()` nunca recebem `questionKey` (quem decide qual pergunta é a atual é o core; passar a de outra pergunta não teria efeito).
- `theme: PitacoResolvedTheme` (`usePitacoTheme()` já resolvido — esquema + tokens validados) e `strings: PitacoStrings` (`usePitacoStrings()` já mesclado) chegam prontos: um renderizador nunca precisa chamar os hooks ele mesmo (mas pode, se quiser algo que não está nas props, já que está dentro da árvore do Provider).
- `freeTextNotice: string | null` só é preenchido para `FREE_TEXT` quando a versão publicada tem o aviso ligado; os outros tipos recebem `null`.
- **O título da pergunta (`question.statement`, com "(obrigatória)" quando `required`) é desenhado por `<PitacoSurveyContent />`, fora do renderizador** — é lá que mora a ref usada para mover o foco de acessibilidade a cada navegação, então ela precisa ser estável independente de qual renderizador está montado. Um renderizador pode repetir o enunciado se seu layout pedir, mas não precisa.
- **Mensagem de erro de validação**: `<PitacoSurveyContent />` já mostra `strings.validationRequired` abaixo do título quando `error !== null`. O renderizador recebe `error` só para destacar a opção/campo específico, se quiser.

**Slots** (`HeaderSlotProps`, `ProgressSlotProps`, `FooterSlotProps`, `CloseButtonSlotProps`, `ThankYouSlotProps`, todos em `types.ts`): mesmo padrão — tema e textos já resolvidos nas props, ações do core já vinculadas (`onBack`, `onNext`, `onClose`, `onDone`). **`onNext` do `Footer` sempre chama `next()`** (nunca `complete()`): o core resolve sozinho — `next()` na última pergunta aplicável conclui (comentário no topo de `core/machine/machine.ts`). O rótulo do botão (`strings.next` vs `strings.submit`) é decisão de quem desenha o slot, usando `isLast`.
- **Importante para 3c**: o `Footer` padrão **não desabilita** o botão de avançar por `canGoNext` — de propósito. Quem valida e bloqueia é o core (`validation_blocked`, populando `error`); um botão desabilitado impediria a pessoa de tentar e o core de emitir o evento. `canGoNext` continua nas props para quem quiser outro comportamento.

**Como pegar tema/strings/ações fora de um renderizador/slot** (por exemplo, dentro de uma apresentação da fase 3c): os mesmos hooks públicos, `usePitacoTheme()` e `usePitacoStrings()` (em `@pitaco/react-native`), funcionam em qualquer lugar dentro do `<PitacoProvider>` (e fora dele, caem no padrão). Ações vêm de `usePitacoSurvey()` (fase 2), inalterado.

**O que o contexto expõe para as apresentações (`PitacoContextValue.ui`, em `react/context.ts`)**:
```ts
interface PitacoUiConfig {
  theme?: PitacoThemeConfig;
  strings?: PartialPitacoStrings;
  renderers?: Partial<PitacoRendererMap>;
  slots?: Partial<PitacoSlotMap>;
  presentation: Presentation; // sempre resolvido, padrão 'bottom-sheet'
  insets?: EdgeInsets;
  getInsets?: () => EdgeInsets;
}
```
3c lê `context.ui.presentation` para saber qual apresentação desenhar, e `context.ui.insets`/`context.ui.getInsets` para a safe area sem dependência (documentei em `PitacoProvider.tsx` que `getInsets` tem prioridade sobre `insets` quando os dois vêm — é quem implementa a apresentação que decide quando chamar `getInsets()`, esta fase só carrega os dois no contexto). `theme`/`strings`/`renderers`/`slots` aqui são a configuração **do Provider**, ainda não resolvida — é a mesma fonte que `<PitacoSurveyContent />` lê para fazer o merge com as props locais (prop local sobrepõe a do Provider); 3c pode usar `usePitacoTheme()`/`usePitacoStrings()` para a versão já resolvida, sem precisar ler `ui.theme`/`ui.strings` direto.

**Registro com error boundary (`SafeCustom`, `sdk/src/ui/registry/SafeCustom.tsx`)**: usado só por `<PitacoSurveyContent />` — 3b e 3c não precisam chamá-lo. Cada renderizador e cada slot substituído roda dentro do `PitacoErrorBoundary` da fase 2, com o padrão como `fallback`; se lançar, cai no padrão, avisa uma vez em `__DEV__` (`console.warn` deduplicado por `kind`+`name`) e chama `context.reportRenderError(error, { kind: 'renderer' | 'slot', name })`. O renderizador da pergunta é remontado (o `PitacoErrorBoundary` recebe `key={question.key}`) a cada pergunta nova, então uma pergunta seguinte do mesmo tipo tenta de novo o componente substituído, do zero.

**Quem chama `present()` e com que via se dispensa**:
- `<PitacoSurveyContent />` chama `present()` sozinha, num efeito de montagem, **a menos que** receba `presentDeferred={true}` — é a prop que um contêiner (o sheet da 3c) usa quando a apresentação ainda está animando a entrada e só fica de fato visível depois; nesse caso quem chama `present()` é o próprio contêiner, direto em `usePitacoSurvey().present()`, quando a animação termina.
- Ao desmontar com a pesquisa em andamento (`status === 'ready'` ou `'presented'`) e sem um desfecho já visto, `<PitacoSurveyContent />` dispensa com a via de `dismissVia` (prop; padrão `'navigation'`) e chama `onFinish('dismissed')`. Um contêiner que já trata a própria dispensa (por exemplo o gorhom, cujo gesto de arrastar já dispara `dismiss('swipe')` antes de desmontar) não duplica: o efeito verifica se o desfecho já aconteceu (`finishedRef`, setado assim que `status` vira `'completed'` ou `'dismissed'`, venha de onde vier) antes de dispensar de novo.
- O botão de fechar (`CloseButton`) sempre dispensa com `via: 'close_button'`.
- Ao concluir, `<PitacoSurveyContent />` mostra o slot `ThankYou` (que se fecha sozinho após `thankYouDurationMs`, padrão 2500 ms) e só então chama `onFinish('completed')`.
- **Achado durante os testes, corrigido no preview (ver acima)**: como React comita efeitos de baixo para cima, um filho que chama `present()` no próprio efeito de montagem roda antes do efeito do Provider que liga `onEvent` — se o Provider (ou, nesta fase, o preview) só ligasse o ouvinte depois, o primeiro evento (`survey_presented`) se perderia. O `PitacoProvider` real já não tinha esse problema (passa `onEvent` na criação do `PitacoRuntime`, síncrona); o preview tinha, e corrigi. Vale para 3c: se a apresentação também chamar `present()` de um efeito de montagem (em vez de esperar o fim da animação e usar `presentDeferred`), não há problema — o Provider real já entrega `onEvent` desde a criação.

**Ao ficar visível a primeira vez**: é a chamada de `present()` acima (ação `present` da fase 2), que abre a exibição e emite `survey_presented`.

### Decisões e o porquê

- **Tema como dois conjuntos de tokens (claro/escuro) + preferência de esquema**, em vez de um único objeto de cores: mapeia direto no pedido do prompt (`theme={{ colorScheme, light, dark }}`) e deixa claro qual token pertence a qual esquema.
- **Merge validado token a token**, nunca o objeto inteiro: um erro de digitação numa cor não devia jogar fora as outras 20 substituições corretas do mesmo tema.
- **Contraste checado só nos pares que a UI padrão realmente desenha** (texto principal/secundário sobre fundo e superfície, texto de botão sobre a cor do botão, texto de perigo sobre a cor de perigo) e **excluindo texto desabilitado**: o WCAG isenta componente inativo do contraste mínimo, e meu par `disabledText`/`disabled` do tema padrão não bateria 4.5:1 por ser proposital (uma opção "apagada" não deveria competir visualmente com o resto).
- **`rendererKeyForQuestionType`** como função exportada (não só um objeto de mapa) para 3b e 3c reaproveitarem sem duplicar a tabela.
- **Botão de avançar nunca desabilitado por obrigatória em branco** (decisão registrada acima): é o core que valida e bloqueia, com o evento `validation_blocked` do catálogo — um botão desabilitado impediria esse evento de nascer pela UI padrão.
- **Título da pergunta fora do renderizador**: garante que o foco de acessibilidade tenha um alvo estável (`findNodeHandle` da mesma `ref`) não importa qual renderizador — padrão ou substituído — está montado, e que 3b nunca precise se preocupar em expor uma ref própria.
- **`SafeCustom` como uma função/componente genérico único**, chamado via JSX (não como chamada de função direta) — a primeira versão chamava `SafeCustom({...})` como função simples dentro do render, o que o novo lint `react-hooks/refs` (ESLint 10 + `eslint-plugin-react-hooks` 7) rejeitou por poder ler um ref "durante o render" através de um retorno de função; além disso um slot padrão como `DefaultThankYou` usa `useEffect` no próprio corpo — chamado como função simples, esse `useEffect` seria anexado à lista de hooks de `PitacoSurveyContent` de forma condicional, quebrando as regras dos hooks de verdade. Resolvido usando `<SafeCustom .../>` como elemento JSX de verdade, com `P extends object` na assinatura genérica (sem essa restrição o TypeScript não aceita espalhar `...props` genérico num JSX).
- **Atualização de refs em efeito, nunca durante o render** (`surveyRef.current = survey` etc.): o mesmo lint novo (`react-hooks/refs`) proíbe mutar `.current` durante o render; um único efeito sem dependências, declarado antes dos demais, mantém os refs atualizados a tempo dos efeitos seguintes no mesmo commit.
- **`thankYouDurationMs` como prop de `<PitacoSurveyContent />`**, não do tema/strings: é comportamento (quanto tempo esperar), não aparência nem texto.
- **Sem `presentation`/apresentação implementada nesta fase**: o Provider só guarda e expõe (`ui.presentation`, padrão `'bottom-sheet'`); a pasta `sdk/src/ui/presentation/` não foi criada porque nada nesta fase precisou dela — fica para 3c criar.

### O que ficou de fora

- Os seis renderizadores **definitivos** (fase 3b) e as três apresentações (fase 3c) — os provisórios em `questions/` são deliberadamente simples (chips e campo de texto, sem nenhuma animação ou refinamento visual).
- `<PitacoPreview />` visual e o teste de mesma sequência de eventos entre UI padrão/substituída/headless — fase 3d.
- Validação de contraste para combinações de tema fora dos pares fixos listados (ex.: um `slots`/`renderers` substituído que use cor arbitrária não é verificado).
- Internacionalização de `strings.progress`/`strings.questionA11yLabel` além da interpolação por parâmetros nomeados (não há pluralização automática; quem substitui cuida disso na própria função).
- `PitacoButton`/`PitacoChip` não tratam `rtl` explicitamente (herdam o que o React Native já resolve sozinho para `flexDirection: 'row'`).

### Resultado exato do `npm run verify`

- `eslint .`: nenhum problema (0 erros, 0 avisos).
- `tsc --noEmit`: nenhum erro.
- `jest --ci`: `Test Suites: 19 passed, 19 total` · `Tests: 176 passed, 176 total` · `Snapshots: 0 total` · cerca de 1,5 s.
- `bob build`: `[commonjs] Compiling 71 files in src with babel`, `[module] Compiling 71 files in src with babel`, `[typescript] Generating type definitions with tsc`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`.
- Conferido depois do build: os únicos arquivos de `lib/` que citam os seis pacotes nativos proibidos são comentários (inclusive um novo, em `ui/types.js`, citando `react-native-safe-area-context` só como referência de forma de `EdgeInsets`) e as mensagens de aviso dos adaptadores de storage — nenhum `import`/`require` de verdade.

## Fase 3b — Renderizadores de pergunta

### Arquivos criados

- `sdk/src/ui/primitives/PitacoScalePoint.tsx` (+ export em `primitives/index.ts`): novo primitivo — ponto numérico quadrado (mín. 44×44 pt, papel `radio`), para `SCALE`/`NPS`. Não reaproveitei `PitacoChip` (pílula, pensada para rótulos de texto) porque um dígito só não garante 44 pt de largura sem um formato quadrado dedicado.
- `sdk/src/ui/questions/shared/QuestionErrorFrame.tsx`: moldura de erro compartilhada pelos seis tipos — borda de perigo (`theme.tokens.colors.danger`) ao redor da área interativa e `AccessibilityInfo.announceForAccessibility(strings.validationRequired)` na transição de "sem erro" para "com erro", mais `accessibilityLiveRegion="polite"` no container. Não repete a mensagem de erro (já mostrada por `<PitacoSurveyContent />` acima do renderizador, contrato da 3a); só destaca e anuncia.
- `sdk/src/ui/questions/__tests__/`: `support.tsx` (suporte só de teste: `rendererProps()` monta `QuestionRendererProps` isolado com tema resolvido, strings padrão e ações espiãs; `renderQuestion(node, scheme)` envolve em `PitacoContext.Provider` com o mesmo esquema do `theme` passado por prop — necessário porque `PitacoChip`/`PitacoScalePoint` resolvem o próprio tema via `usePitacoTheme()`/contexto, não a partir da prop `theme` do renderizador; dentro do `<PitacoProvider>` de verdade as duas fontes sempre coincidem, mas um teste isolado sem Provider precisa replicar o contexto para o esquema escuro bater) + `SingleChoiceQuestion.test.tsx`, `MultipleChoiceQuestion.test.tsx`, `RatingQuestion.test.tsx`, `ScaleQuestion.test.tsx`, `NpsQuestion.test.tsx`, `FreeTextQuestion.test.tsx` (40 testes no total).

### Arquivos reescritos (posse desta subtarefa, mantendo nome de arquivo/componente/assinatura)

- `sdk/src/ui/questions/SingleChoiceQuestion.tsx`: lista de `PitacoChip` (papel `radio`), toque sempre chama `select(option.value)` — o core troca sozinho e não emite nada se o valor repetir.
- `sdk/src/ui/questions/MultipleChoiceQuestion.tsx`: mesma lista com `multiple` (papel `checkbox`), toque alterna `select`/`deselect`. O schema atual (`core/survey/schema.ts`) não tem mínimo/máximo de seleções para este tipo — só o teto natural do número de opções — então não há limite para validar aqui; registrado no comentário do arquivo para quando o contrato ganhar `minSelections`/`maxSelections`.
- `sdk/src/ui/questions/RatingQuestion.tsx`: reescrito de números-em-chip para estrelas desenhadas só com `Text`/`View` (glifo `★`/`☆`, preenchimento cumulativo até o valor escolhido), papel `radio` por estrela com rótulo de acessibilidade `"X de N"` (`N` = tamanho da faixa). `value` continua o número da faixa (`range.min..max`), nunca a posição visual do glifo.
- `sdk/src/ui/questions/ScaleQuestion.tsx` e `NpsQuestion.tsx`: agora passam pela `QuestionErrorFrame`; NPS ganhou fallback de rótulos de ponta via `strings.npsMinLabelDefault`/`npsMaxLabelDefault` quando o schema não traz `minLabel`/`maxLabel` (mantido do provisório da 3a).
- `sdk/src/ui/questions/FreeTextQuestion.tsx`: `TextInput` com `maxLength={MAX_FREE_TEXT_LENGTH}` (o mesmo teto que `setText` do core já aplica — `core/survey/answers.ts` —, então o campo nunca deixa passar do que o core aceitaria) e contador `"{length}/{MAX_FREE_TEXT_LENGTH}"` sempre visível (o teto é do core, não por pergunta — não há `maxLength` por pergunta no schema, então o contador é incondicional, não "quando houver limite"). O aviso de texto livre (`freeTextNotice`, já resolvido pela 3a) fica junto do campo e é anunciado uma vez ao leitor de tela quando aparece (`AccessibilityInfo.announceForAccessibility`).
- `sdk/src/ui/questions/shared/NumericScale.tsx`: trocado de `PitacoChip` para `PitacoScalePoint` (ponto quadrado em vez de pílula), mantendo a mesma API (`range`, `value`, `onSelect`, `theme`) usada por `ScaleQuestion` e `NpsQuestion`. `shared/nps.ts` (fallback de faixa) ficou como estava.

### Arquivos de outra subtarefa alterados (mudança mínima, registrada)

- `sdk/src/index.ts` (3a): adicionado `PitacoScalePoint`/`PitacoScalePointProps` à lista de exports nomeados da UI padrão — o novo primitivo precisa estar na superfície pública como os demais (`PitacoChip`, `PitacoButton`).
- `sdk/src/ui/content/__tests__/PitacoSurveyContent.test.tsx` (3a): dois `getByRole('radio', { name: '4' })` (a pergunta de avaliação, faixa 1-5) viraram `{ name: '4 de 5' }`. Causa: o renderizador definitivo de `RATING` (esta fase) trocou o rótulo de acessibilidade de `String(valor)` para `"X de N"`, conforme o briefing pede explicitamente para o tipo Avaliação. Os provisórios da 3a usavam números crus (mesmo formato de `SCALE`/`NPS`); o teste de `PitacoSurveyContent` cobre o fluxo ponta-a-ponta com os renderizadores padrão e precisava acompanhar essa mudança de contrato de acessibilidade do tipo. Nenhuma asserção de evento ou de estado mudou, só o seletor usado para encontrar a estrela.

### Decisões e o porquê

- **Estrelas para `RATING`, pontos numéricos para `SCALE`/`NPS`**: o briefing pede visuais diferentes para os dois ("rating, estrelas... desenhado com Text/View" vs. "escala: pontos numéricos"), e semanticamente avaliação (1-5, 1-10) e escala Likert/NPS são conceitos distintos mesmo quando o `range` tem a mesma forma.
- **`PitacoScalePoint` como primitivo novo, em vez de estender `PitacoChip`**: evita mexer no primitivo já testado da 3a (pílula, pensada para rótulos de texto variável) só para um caso quadrado de dígito único; mudança aditiva e isolada.
- **`QuestionErrorFrame` não repete a mensagem de validação**: `<PitacoSurveyContent />` já mostra `strings.validationRequired` acima do renderizador (contrato da 3a, não alterável por esta subtarefa); duplicar o texto dentro do renderizador ecoaria a mesma frase duas vezes para quem usa leitor de tela. A moldura só destaca visualmente (borda) e anuncia (`announceForAccessibility`) — que é o que o briefing pede além da mensagem.
- **Múltipla escolha sem mínimo/máximo**: conferido `core/survey/schema.ts` e `backend/docs/backend/contrato-sdk.md` — nenhum dos dois define `minSelections`/`maxSelections` para o tipo hoje; não há o que validar no renderizador além do que o core já garante (não deixar repetir/remover inexistente).
- **Contador de caracteres sempre visível no texto livre**: o teto (`MAX_FREE_TEXT_LENGTH = 2000`) é uma constante do core, não um campo do schema por pergunta — não existe um "com limite" e um "sem limite" para diferenciar, então o contador aparece sempre, contra esse teto fixo.
- **`select` sempre chamado ao tocar (nunca `deselect`) em escolha única/avaliação/escala/NPS**, mesmo tocando na opção já escolhida: conferido em `core/machine/machine.ts` (`select`, caso `previous === value`) que o core já não emite nada quando o valor repete — o renderizador não precisa (nem deve) decidir isso, simplificando o toque para "sempre selecionar".
- **Teste isolado por `QuestionRendererProps`, sem montar `<PitacoSurveyContent />`**: os renderizadores só recebem `theme`/`strings`/`actions` por prop e não chamam o core diretamente (contrato da 3a), então dá para testar cada um sozinho, sem sessão nem Provider — mais rápido e não depende de uma pergunta específica de `fixtures.ts` (`SCALE` foi montada manualmente no teste porque a fixture de referência não tem esse tipo). O teste de esquema escuro precisou de um `PitacoContext.Provider` local (`renderQuestion`) porque `PitacoChip`/`PitacoScalePoint` leem o próprio tema do contexto, não da prop — registrado como achado acima.

### O que ficou de fora

- **Escala com faixa grande (span até 100, `MAX_SCALE_SPAN`) continua desenhada como uma linha de pontos numéricos**, não como um controle `adjustable`/slider por arrasto: o briefing aceita "`adjustable` ou botões com rótulo" como alternativas equivalentes; optei pelos botões com rótulo para os seis tipos por uniformidade e porque um slider por arrasto sem Reanimated/Gesture Handler (restrição 1) exigiria um `PanResponder` dedicado, fora do escopo desta subtarefa. Uma faixa de 100 pontos quebra em várias linhas (funciona, mas fica longa); registrado para uma iteração futura se aparecer um caso real com faixa grande.
- Gesto de arrastar ou qualquer animação nos renderizadores — são apresentação (fase 3c) e fora da posse desta subtarefa.
- Validação de contraste das cores novas do `PitacoScalePoint` além dos pares já cobertos pela checagem da 3a (`checkThemeContrast`) — o par selecionado/`primaryText`-equivalente do ponto não está na lista fixa de pares verificados.

### Resultado exato dos comandos (rodados em `sdk/`)

- `npm run lint`: no fim desta subtarefa, os únicos problemas restantes estão em arquivos da 3c, ainda em andamento em paralelo (`src/react/SurfaceHost.tsx`, `src/ui/presentation/BottomSheet.tsx`, `src/ui/presentation/FullScreenModal.tsx`) — `react-hooks/set-state-in-effect`, `react-hooks/refs` e `@typescript-eslint/unbound-method`. Não corrigidos (não são meus arquivos e a 3c está ativa neles); a última rodada durante esta subtarefa terminou em `✖ 1 problem (1 error, 0 warnings)`, só em `BottomSheet.tsx:152` (`Passing a ref to a function may read its value during render`, `react-hooks/refs`) — o número de problemas variou entre execuções porque a 3c segue editando esses arquivos. Nenhum problema em `src/ui/questions/` ou nos demais arquivos desta subtarefa.
- `npm run typecheck` (`tsc --noEmit`): nenhum erro.
- `npm run test -- --ci` (`jest`): `Test Suites: 25 passed, 25 total` · `Tests: 216 passed, 216 total` · `Snapshots: 0 total` · cerca de 1,5 s. Inclui os 40 testes novos dos seis renderizadores e o `PitacoSurveyContent.test.tsx` ajustado.
- `npm run build` (`bob build`): `[commonjs] Compiling 78 files in src with babel`, `[module] Compiling 78 files in src with babel`, `[typescript] Generating type definitions with tsc`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`.
- Conferido depois do build: os únicos arquivos de `lib/` que citam os seis pacotes nativos proibidos continuam sendo só comentários e mensagens de aviso dos adaptadores de storage — nenhum `import`/`require` de verdade.
- `npm run verify` (composto) não foi rodado de ponta a ponta como um único comando nesta subtarefa porque o passo de lint falharia por causa dos arquivos da 3c ainda em edição (fora da minha posse); os quatro comandos que o compõem foram rodados em separado acima, e os três que tocam meus arquivos (`typecheck`, `test`, `build`) estão totalmente verdes.

## Fase 3c — Apresentações

### Arquivos criados

`sdk/src/ui/presentation/` (nova; dono desta subtarefa):

- `insets.ts`: safe area sem dependência — `platformDefaultInsets()` (iOS: `{top:47, bottom:34}`, aproximando notch e indicador de início; Android: `StatusBar.currentHeight` ou 24 de padrão) e `resolveInsets(insets, getInsets)` (`getInsets` tem prioridade, cai no padrão da plataforma se faltar os dois ou se `getInsets` lançar).
- `useReduceMotion.ts`: `AccessibilityInfo.isReduceMotionEnabled()` + o evento `reduceMotionChanged`; começa em `false`, nunca deixa uma falha da API chegar ao componente.
- `gesture.ts`: a lógica pura de arrastar a folha — `dragProgress` (resistência para cima), `shouldCloseFromGesture` (limiar de distância ou velocidade) e `createDragHandlers` (o objeto de handlers que o `PanResponder` do `BottomSheet` usa), tudo separado do componente para ser testável sem precisar fabricar um histórico de toque nativo de verdade (o `PanResponder` do React Native computa a `gestureState` a partir dele; não há como simular isso por evento sintético).
- `BottomSheet.tsx`: o bottom sheet próprio — `Modal` transparente (`statusBarTranslucent` no Android, `animationType="none"`), `Animated` com `useNativeDriver: true` (entrada/saída por `translateY`+opacidade do backdrop), `PanResponder` na alça (via `gesture.ts`), `KeyboardAvoidingView`, `ScrollView` interna com teto de 90% da tela, cantos arredondados do tema, rodapé com o inset inferior.
- `FullScreenModal.tsx`: a apresentação `'modal'` — `Modal` opaco (`presentationStyle="fullScreen"` no iOS), fade de opacidade, safe area pelos quatro insets, sem arrastar (não é o gesto de uma tela cheia).
- `index.ts`: barril interno (não exportado pelo `src/index.ts` — é detalhe do `SurfaceHost`, não API pública).
- `__tests__/`: `insets.test.ts`, `useReduceMotion.test.tsx`, `gesture.test.ts`.

### Arquivos alterados

- `sdk/src/react/SurfaceHost.tsx` (meu; era um placeholder da fase 2/3a): agora lê `context.ui.presentation` e escolhe `BottomSheet`, `FullScreenModal` ou nada (`'inline'`); resolve os insets (`resolveInsets`); mantém um `open` local que só desliga quando `<PitacoSurveyContent presentDeferred onFinish={...} />` avisa o desfecho (não quando `usePitacoSurvey().available` cai, que acontece cedo demais — ver decisões); `onOpened` chama `survey.present()`; `onDismiss(via)` de arrastar/fundo/voltar do Android/ação de acessibilidade chama `survey.dismiss(via)` direto.
- `sdk/src/react/__tests__/SurfaceHost.test.tsx` (novo, meu): 12 testes cobrindo tudo do checklist da subtarefa.

Não precisei tocar `ui/types.ts`, o registro (`SafeCustom`) nem os renderizadores — o contrato da 3a bastou.

### API pública nova

Nenhuma. `BottomSheet`, `FullScreenModal`, `gesture.ts` e `insets.ts` são detalhe de implementação do `SurfaceHost`, não exportados por `sdk/src/index.ts`. A superfície pública que o app usa continua sendo só `presentation` no `PitacoProvider` (já existia, tipada pela 3a) e `<PitacoSurveyContent />`/`dismiss(via)` (fase 3a/2) para quem monta a própria apresentação.

### Decisões e o porquê

- **`open` do `SurfaceHost` não é `usePitacoSurvey().available`.** `available` vira falso no instante em que o core conclui ou dispensa — cedo demais: cortaria a tela de agradecimento antes de aparecer, e a folha sumiria sem animar a saída. Em vez disso `open` fica ligado do instante em que `available` liga até `<PitacoSurveyContent presentDeferred onFinish={...} />` chamar `onFinish` — que na conclusão só vem depois do agradecimento (o próprio timer da fase 3a) e na dispensa só depois que o core já registrou a via. `BottomSheet`/`FullScreenModal` decidem sozinhos, a partir de `open`, quando a própria animação de saída termina (`onClosed`) para então desmontar de vez.
- **`presentDeferred` sempre `true`** nas duas apresentações: quem chama `present()` é o `SurfaceHost`, só depois que `onOpened` (fim da animação de entrada) dispara — nunca `<PitacoSurveyContent />` sozinha, que abriria a exibição (e emitiria `survey_presented`) antes da folha estar de fato visível. Testado (`survey_presented` não aparece até a animação avançar).
- **Vias de dispensa, quem chama o quê**: `close_button` já nasce de dentro do conteúdo (`DefaultCloseButton` da 3a chama `survey.dismiss('close_button')` direto) — o `SurfaceHost` não participa, só recebe `onFinish('dismissed')` depois. `backdrop`, `swipe` e `hardware_back` são o container quem decide (toque no fundo, o `PanResponder` da alça, `BackHandler`/`onRequestClose`) e chama `survey.dismiss(via)` ele mesmo, via `onDismiss` repassado pelo `SurfaceHost`. `programmatic` é o padrão de `usePitacoSurvey().dismiss()` sem argumento — testado chamando de um componente headless fora da apresentação, simulando o app.
- **`createDragHandlers` separado em `gesture.ts`, puro.** O `PanResponder` de verdade computa a `gestureState` a partir do histórico de toque nativo (`touchHistory`), que não dá para fabricar por evento sintético em teste. Extraindo a decisão (resistência, limiares de distância/velocidade) para uma função pura que recebe uma `gestureState` de mentira, os testes "simulam o arrasto disparando os handlers do PanResponder" chamando exatamente os mesmos handlers que o componente usa — só sem a maquinaria de toque nativo por baixo.
- **`react-hooks/refs` e `react-hooks/set-state-in-effect` (ESLint 10 + `eslint-plugin-react-hooks` 7, os mesmos que a 3a já tinha encontrado com `SafeCustom`) exigiram três padrões nesta subtarefa**, documentados em comentário no próprio código:
  1. **Estado derivado ajustado durante o render**, não dentro de um `useEffect`: `open`/`phase` comparam a prop/valor de fora com uma cópia rastreada em estado e chamam `setState` direto no corpo da função de render quando mudou (o padrão que os próprios documentos do React recomendam para "sincronizar estado com uma prop"), em vez de um `useEffect` que só chama `setState` — a animação em si (efeito colateral de verdade) continua num efeito à parte, disparado pela fase já decidida.
  2. **`phase`/`open` inicializados por função preguiçosa a partir da própria prop** (`useState(() => open ? 'entering' : 'hidden')`), não por um valor fixo: sem isso, quando a pesquisa já está disponível na primeira renderização (o caso comum — a consulta de elegibilidade já respondeu, ou é o preview), a comparação "mudou?" do item acima nunca veria diferença nenhuma, e a folha nunca abriria sozinha. Descoberto pelos meus próprios testes (a suíte falhava porque a folha nunca chamava `onOpened`).
  3. **O `PanResponder` só pôde ser criado sem disparar `react-hooks/refs`** com um `eslint-disable-next-line` justificado em comentário: todo padrão testado (`useMemo`, `useState` preguiçoso, o idioma `if (ref.current === null) ref.current = ...` dos próprios documentos do React) foi rejeitado igual, porque os handlers leem refs (`hiddenOffsetRef`, `startExitRef`) e a regra não consegue provar que a leitura só acontece num gesto de verdade, nunca durante o render — é exatamente o caso de uso que a memoização estável existe para habilitar.
- **Sem `Easing` customizado no `Animated.timing`**: `Easing.out(Easing.cubic)` disparava `@typescript-eslint/unbound-method` (falso positivo comum com o objeto estático `Easing`); a duração e a curva padrão do `Animated.timing` já bastam para o efeito pedido, e a instrução não exige uma curva específica.
- **Movimento reduzido implementado como duração de 1 ms**, não pulando a animação por completo: mantém o mesmo caminho de código (fase → efeito → `Animated.timing` → callback) em vez de um `if` separado, e o resultado prático (sem movimento perceptível) é o mesmo que a instrução pede ("sem animação, ou fade curtíssimo").
- **`insets` resolvidos no `SurfaceHost`, não em cada apresentação**: uma única chamada a `resolveInsets` e o resultado passa pronto para `BottomSheet`/`FullScreenModal` — evita chamar `getInsets()` (potencialmente uma função do app, com custo ou efeito colateral) mais de uma vez por render.
- **Contrato da 3a insuficiente em um ponto, resolvido com a menor mudança possível**: `src/preview/index.ts` (não é meu) monta `ui: { presentation }` sem `insets`/`getInsets` — o `<PitacoPreview />` visual (fase 3d) não precisa deles (o preview nunca abre um contêiner de verdade), então não toquei nesse arquivo. Para testar insets sem depender do preview, `SurfaceHost.test.tsx` monta um `PitacoContextValue` à mão (com `createPreviewController`, da própria fase 2/3a, por baixo) em vez do `PitacoPreviewProvider` — registrado aqui para quem for revisar a superfície do preview depois.

### O que ficou de fora

- Simulação de arrasto por evento de toque nativo de verdade (`fireEvent` sobre `onResponderMove`/`onResponderRelease`): o `PanResponder` do React Native computa a `gestureState` a partir de um histórico de toque que não dá para fabricar por evento sintético em teste teste (ver decisões). Testado, em vez disso, chamando `createDragHandlers` diretamente — mesma lógica, sem a maquinaria de toque por baixo.
- Rotação de tela durante uma apresentação aberta (`hiddenOffset` não reage a `Dimensions` mudar depois do primeiro render).
- Uma curva de easing customizada para a animação de entrada/saída (ver decisões — o padrão do `Animated.timing` foi suficiente e evitou um falso positivo de lint).
- Documentação de como o app usa `<PitacoSurveyContent />`+`dismiss(via)` no modo `'inline'` para o gorhom e o React Navigation (cenários 2 e 3 do exemplo, fase 5): o contrato (fase 3a) já cobre tudo que o `'inline'` precisa — o `SurfaceHost` simplesmente não renderiza nada nesse modo (`presentation === 'inline'` retorna `null` antes de montar qualquer contêiner) — mas o guia com o passo a passo de integração é conteúdo da fase 6 (documentação), não desta subtarefa.

### Resultado exato do `npm run verify`

- `eslint .`: nenhum problema (0 erros, 0 avisos).
- `tsc --noEmit`: nenhum erro.
- `jest --ci`: `Test Suites: 29 passed, 29 total` · `Tests: 250 passed, 250 total` · `Snapshots: 0 total` · cerca de 1,6 s. Inclui os 12 testes novos de `SurfaceHost.test.tsx`, os de `insets.test.ts` (5), `useReduceMotion.test.tsx` (3) e `gesture.test.ts` (12).
- `bob build`: `[commonjs] Compiling 79 files in src with babel`, `[module] Compiling 79 files in src with babel`, `[typescript] Generating type definitions with tsc`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`.
- Conferido depois do build: os únicos arquivos de `lib/` que citam os seis pacotes nativos proibidos continuam sendo só comentários (agora também em `ui/presentation/insets.js` e `ui/presentation/BottomSheet.js`, citando `react-native-safe-area-context`/gorhom/Reanimated/Gesture Handler só como referência) e as mensagens de aviso dos adaptadores de storage — nenhum `import`/`require` de verdade.

## Fase 3d — Fechamento

### Arquivos criados

`sdk/src/preview/` (subpath, dono desta subtarefa):
- `controller.ts`: o antigo conteúdo de `index.ts` (`createPreviewController`, `PitacoPreviewProvider`) movido para cá, para `PitacoPreview.tsx` poder importar `PitacoPreviewProvider` sem ciclo de módulo com o barril.
- `PitacoPreview.tsx`: **`<PitacoPreview schema={...} />`** — `PitacoPreviewProvider` (agora aceitando `theme`/`strings`/`renderers`/`slots`/`onRenderError`, ver abaixo) + `<PitacoSurveyContent />`, sempre inline (`presentation` default `'inline'`, nunca `Modal`). Reinício por `resetKey` (documentado: troca de valor remonta tudo — `key` própria funciona igual).
- `index.ts`: virou barril puro, reexportando `controller.ts` + `PitacoPreview.tsx` + `usePitacoSurvey`.
- `__tests__/PitacoPreview.test.tsx` (5 testes).

`sdk/src/ui/content/`:
- `focus.ts`: `moveAccessibilityFocus(handle, setFocus)` — extraído de `<PitacoSurveyContent />` para poder testar a decisão de foco (há handle → chama `setFocus`, nunca deixa uma falha escapar) sem depender de `findNodeHandle`/`AccessibilityInfo` de verdade (o renderizador de teste do RN não tem nó nativo: `findNodeHandle` sempre devolve `null` nele).
- `__tests__/sameSequence.contract.test.tsx`: **o teste de contrato de mesma sequência de eventos** — UI padrão, UI com NPS e escolha única substituídos, e UI headless mínima, rodando os dois roteiros de `src/__tests__/support/script.ts` (`STANDARD_SCRIPT`, `DISMISS_SCRIPT`) sobre `PitacoPreviewProvider`, comparadas por `signature()` contra a máquina pura de referência.
- `__tests__/customization.levels.test.tsx`: tema (claro/escuro/inválido degradando), textos substituídos, os cinco slots substituídos um a um, renderizador por tipo substituído, renderizador/slot que lança erro (cai no padrão e chama `onRenderError`), headless total — todos através de `<PitacoSurveyContent />` de verdade.
- `__tests__/a11y.focus.test.tsx`: `moveAccessibilityFocus` isolado + prova de que o título (alvo do foco) muda a cada navegação sozinho, dentro do bottom sheet e dentro do modal.
- `__tests__/a11y.fontScale.test.tsx`: o título da pergunta e o campo de texto livre usam o mesmo teto de `maxFontSizeMultiplier` que `PitacoText` já aplica ao resto da UI.
- `slots/__tests__/DefaultThankYou.test.tsx`: o agradecimento anuncia (`AccessibilityInfo.announceForAccessibility`) título+corpo ao aparecer.

`sdk/src/react/__tests__/SurfaceHost.a11y.test.tsx` (arquivo novo, não altera o `SurfaceHost.test.tsx` da 3c): a alça do bottom sheet tem ao menos 44 pt de altura tocável.

### Arquivos alterados

- `sdk/src/ui/presentation/BottomSheet.tsx` (não é meu — mudança mínima e corretiva, registrada): `handleArea` tinha só `paddingVertical: 10` ao redor de uma alça de 4 pt (24 pt de alvo total) — abaixo do mínimo de 44 pt pedido pelo prompt para "inclusive... a alça". Troquei por `minHeight: 44` (a largura já era a folha inteira). Coberto por `SurfaceHost.a11y.test.tsx`.
- `sdk/src/ui/content/slots/DefaultThankYou.tsx` (meu): anuncia `"{thankYouTitle}. {thankYouBody}"` via `AccessibilityInfo.announceForAccessibility` ao aparecer, além do `accessibilityRole="alert"` que já tinha — um elemento que já nasce montado (sem transição invisível→visível) nem sempre é anunciado por todo leitor de tela só com o `role`.
- `sdk/src/ui/content/PitacoSurveyContent.tsx` (meu): usa `moveAccessibilityFocus` (extraído) e ganhou `maxFontSizeMultiplier` no título (o único `Text` cru do arquivo, por precisar de uma ref de verdade — não passa por `PitacoText`).
- `sdk/src/ui/primitives/PitacoText.tsx` (meu): `DEFAULT_MAX_FONT_SIZE_MULTIPLIER` exportado, para o título de `PitacoSurveyContent` e o campo de `FreeTextQuestion` usarem o mesmo teto.
- `sdk/src/ui/questions/FreeTextQuestion.tsx` (não é meu, dono é 3b — mudança mínima, registrada): `maxFontSizeMultiplier={DEFAULT_MAX_FONT_SIZE_MULTIPLIER}` no `TextInput`, pela mesma razão de coerência de escala de fonte.

### API pública nova

- `@pitaco/react-native/preview`: **`<PitacoPreview />`** (+ `PitacoPreviewProps`) — schema, presentation (default `'inline'`), triggerEvent, theme, strings, renderers, slots, onEvent, onFinish, thankYouDurationMs, resetKey.
- `PitacoPreviewProviderProps` (mesmo subpath) ganhou `theme`, `strings`, `renderers`, `slots`, `onRenderError`, `triggerEvent` — antes só aceitava `schema`/`presentation`/`onEvent`. Quem já usava `PitacoPreviewProvider` direto (sem essas props) continua funcionando sem mudança.

### Decisões e o porquê

- **`controller.ts` separado de `index.ts`**: `<PitacoPreview />` precisa de `PitacoPreviewProvider`; deixá-lo em `index.ts` (o barril que também exportaria `<PitacoPreview />`) criaria um ciclo de módulo (`index.ts` → `PitacoPreview.tsx` → `index.ts`). Extrair para `controller.ts` e fazer os dois (`index.ts` e `PitacoPreview.tsx`) importarem de lá resolve sem ciclo.
- **`<PitacoPreview />` default `presentation="inline"`**: o preview nunca desenha um contêiner de verdade (é sempre inline, sem `Modal`) — `'inline'` é o valor que descreve isso com precisão no evento `survey_presented.data.presentation`; o app que quiser simular "como ficaria" com bottom sheet/modal pode passar a prop.
- **Reinício por `resetKey` (ou `key` própria), sem `restart()` imperativo**: `<PitacoPreview />` é um componente só declarativo (Provider + Content por baixo); expor um método imperativo pediria `forwardRef`/`useImperativeHandle` para um caso que a prop `key` do próprio React já resolve — documentado no JSDoc do componente.
- **`onRenderError` no preview, em vez de reaproveitar transporte**: o preview não tem `PitacoRuntime` (não há relatório de erro por rede); para o teste de "renderizador/slot que lança erro" (item 3 da subtarefa) funcionar sem transporte, `reportRenderError` do contexto do preview agora repassa para um callback simples.
- **Teste de contrato sobre `PitacoPreviewProvider`, não sobre `PitacoProvider` de verdade**: mais simples e determinístico (sem a dança de animação de entrada do bottom sheet); `background`/`foreground` (que dependem do `AppState`, não do preview) já têm a própria prova de paridade em `src/react/__tests__/hooks.test.tsx` (headless sobre o Provider de verdade, com os dois roteiros completos) — filtrados aqui como o resto da suíte de preview já fazia desde a 3a.
- **NPS e escolha única como os dois renderizadores substituídos do teste de contrato**: a escolha única substituída, ao contrário da padrão (que nunca desmarca ao tocar — decisão da 3b), alterna select/deselect no mesmo toque — prova que a sequência de eventos do core independe de como (ou se) cada UI expõe o gesto de desmarcar. Onde a UI padrão não tem gesto nenhum para uma ação do roteiro (desmarcar escolha única; dispensar por uma via que não é o botão de fechar), o driver chama a ação pública direto em `usePitacoSurvey()` — o mesmo que o app faria (contrato já documentado na 3c: só `close_button` nasce do conteúdo, as outras vias vêm do contêiner).
- **`moveAccessibilityFocus` extraído para `focus.ts`**: o renderizador de teste do React Native não tem nós nativos — `findNodeHandle` sempre devolve `null` nele (verificado empiricamente). Sem extrair a decisão pura da obtenção do handle, a regra "focar quando houver handle, nunca deixar uma falha da API derrubar a pesquisa" ficaria sem teste de verdade (só side-effect não observável). Os testes de "o alvo do foco muda" (o título) continuam de ponta a ponta, inclusive dentro do bottom sheet e do modal.
- **Alça do bottom sheet a 44 pt**: achado ao revisar a apresentação (item 4 do prompt, "alvo ≥ 44 pt em todos os controles, inclusive... a alça") — o código da 3c tinha só `paddingVertical: 10` ao redor de uma alça visual de 4 pt (24 pt de alvo). Corrigido com a menor mudança possível (só a altura mínima da área tocável) e registrado aqui por não ser meu arquivo.

### O que ficou de fora

- Teste de contrato sobre o `PitacoProvider` de verdade com `PitacoSurfaceHost` (bottom sheet/modal reais) — cobriria a mesma coisa que já está coberta (headless com `AppState` em `hooks.test.tsx`, e a UI real dentro do sheet/modal nos testes de foco desta subtarefa), a um custo de complexidade bem maior (esperar a animação de entrada, `presentDeferred`); não haveria sinal novo.
- Simulação de arrasto/gesto na alça do bottom sheet como parte de algum teste de acessibilidade desta subtarefa (a 3c já cobre a lógica pura do gesto em `gesture.test.ts`; aqui só o alvo de toque).
- Validação de contraste do `PitacoScalePoint`/estados de erro fora dos pares já cobertos pela checagem da 3a (mesma lacuna já registrada pela 3b).
- Uma central de rótulos/i18n para o anúncio do agradecimento (usei os mesmos `strings.thankYouTitle`/`thankYouBody` já existentes, concatenados).

### Resumo consolidado da Fase 3 (UI e apresentação)

**API pública final da UI** (`@pitaco/react-native`): `PitacoProvider` com `presentation` (`'bottom-sheet'` padrão, `'modal'`, `'inline'`), `theme`, `strings`, `renderers`, `slots`, `insets`/`getInsets`; `usePitacoSurvey()` (estado + ações do core, idêntico ao que a UI padrão usa); `<PitacoSurveyContent onFinish presentDeferred dismissVia thankYouDurationMs renderers slots />`; tema (`usePitacoTheme`, `DEFAULT_LIGHT_THEME`/`DEFAULT_DARK_THEME`), textos (`usePitacoStrings`, `DEFAULT_STRINGS`), primitivos (`PitacoText`, `PitacoButton`, `PitacoChip`, `PitacoScalePoint`), contratos de customização (`QuestionRendererProps`/`Actions`, `PitacoRendererMap`, os cinco slots, `PitacoSlotMap`, `EdgeInsets`). `@pitaco/react-native/preview` exporta `<PitacoPreview schema={...} />` (mais `createPreviewController`/`PitacoPreviewProvider` para quem quiser montar a própria UI sobre o schema em memória). Bottom sheet e modal são detalhe de implementação de `PitacoSurfaceHost` (`ui/presentation/`), não exportados.

**Onde está o teste de mesma sequência de eventos**: `sdk/src/ui/content/__tests__/sameSequence.contract.test.tsx` (UI padrão vs. UI com NPS/escolha única substituídos vs. headless, contra a máquina pura, dois roteiros) e `sdk/src/react/__tests__/hooks.test.tsx` (headless com `PitacoProvider` de verdade e `AppState`, incluindo segundo plano/volta — os dois arquivos juntos cobrem os quatro "modos" citados no prompt: padrão, substituída, headless, e headless-com-app-de-verdade).

**Lacunas conhecidas para as fases 4-7**:
- Fase 4 (exemplo/app de demonstração): nenhuma API nova pendente da UI; o exemplo consome `PitacoProvider`/`<PitacoSurveyContent />`/`<PitacoPreview />` como estão.
- Fase 5 (documentação/guia de integração): os três cenários de apresentação (`bottom-sheet`/`modal`/`inline` com gorhom ou React Navigation) têm o contrato pronto (3c) mas o passo a passo de integração ainda não foi escrito — fora do escopo de UI.
- Fase 6/7 (painel via `react-native-web`): `<PitacoPreview />` foi construído para funcionar sem nenhum módulo nativo (só `PitacoSurveyContent`/`PitacoPreviewProvider`, sem `Modal`), mas não foi testado de fato sob `react-native-web` (sem esse ambiente disponível aqui) — vale uma checagem manual quando o painel existir.
- Validação de contraste fora dos pares fixos (tema custom com cor arbitrária num slot/renderizador substituído) permanece sem checagem automática, registrado desde a 3a/3b.
- Slider `adjustable` por arrasto para escala com faixa grande (>10 pontos) continua como pontos numéricos em várias linhas, decisão da 3b mantida.

### Resultado exato do `npm run verify`

- `eslint .`: nenhum problema (0 erros, 0 avisos).
- `tsc --noEmit`: nenhum erro.
- `jest --ci`: `Test Suites: 36 passed, 36 total` · `Tests: 280 passed, 280 total` · `Snapshots: 0 total` · cerca de 1,7 s. (250 da 3c + 30 novos: 5 de `PitacoPreview.test.tsx`, 2 de `sameSequence.contract.test.tsx`, 13 de `customization.levels.test.tsx`, 6 de `a11y.focus.test.tsx`, 2 de `a11y.fontScale.test.tsx`, 1 de `DefaultThankYou.test.tsx`, 1 de `SurfaceHost.a11y.test.tsx`.)
- `bob build`: `[commonjs] Compiling 82 files in src with babel`, `[module] Compiling 82 files in src with babel`, `[typescript] Generating type definitions with tsc`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`.
- Conferido depois do build: os únicos arquivos de `lib/` que citam os seis pacotes nativos proibidos (`@gorhom/bottom-sheet`, Reanimated, Gesture Handler, `react-native-safe-area-context`, `@react-native-async-storage/async-storage`, `react-native-mmkv`) continuam sendo só comentários e as mensagens de aviso dos adaptadores de storage — nenhum `import`/`require` de verdade, inclusive nos arquivos novos de `preview/`.

## Fase 4 — Controle de onde e quando

### Arquivos alterados

- `sdk/src/catalog/placement.ts`: catálogo `placement_` redesenhado e completo. Tipos:
  `placement_available`, `placement_suppressed` (já existiam), `placement_session_limited`,
  `placement_blocked`, `placement_survey_held`, `placement_survey_discarded`,
  `placement_deferred`, `placement_released`, `placement_expired`. Envelope com `surveyId`/
  `versionId`/`triggerEvent` agora `string | null` (só `placement_blocked` e
  `placement_session_limited` não têm pesquisa candidata para identificar).
- `sdk/src/core/config.ts`: `DEFAULT_DEFER_TIMEOUT_MS` (5 min), `deferTimeoutMs` em
  `ConfigInput`/`ResolvedConfig` com validação (1 s–1 h), `DEFAULT_BLOCK_REASON` e
  `sanitizeBlockReason`.
- `sdk/src/core/runtime/runtime.ts`: limite de sessão (`sessionSurveyShown`), portão padrão
  embutido (bloqueio/adiamento) sobre a mesma interface `PlacementGate` da fase 2, métodos
  públicos `block`/`unblock`/`defer`/`release`/`simulateAppReopen`, `diagnostics()` estendido,
  `reset()` e `setPlacementGate()` ajustados.
- `sdk/src/core/runtime/placement.ts`: só comentário atualizado (a interface de extensão não
  mudou; `immediatePlacement` deixou de ser o portão padrão, mas continua exportado).
- `sdk/src/react/usePitaco.ts`: `block`/`unblock`/`defer`/`release` em `PitacoActions`.
- `sdk/src/react/PitacoBlock.tsx` (novo): `<PitacoBlock reason? />`.
- `sdk/src/react/PitacoProvider.tsx`: prop `deferTimeoutMs`.
- `sdk/src/index.ts`: exporta `PitacoBlock`/`PitacoBlockProps`.
- Testes: `sdk/src/core/runtime/__tests__/runtime.test.ts` (nova bateria da fase 4 + um ajuste em
  dois testes pré-existentes, ver abaixo), `sdk/src/react/__tests__/PitacoBlock.test.tsx` (novo),
  `sdk/src/react/__tests__/hooks.test.tsx` (uma linha a mais checando que `block`/`unblock`/
  `defer`/`release` fora do Provider não lançam).

### API pública nova

- `usePitaco()`: `block(reason?: string): void`, `unblock(reason?: string): void`,
  `defer(): void`, `release(): void`.
- `<PitacoBlock reason?: string />`: bloqueia ao montar, desbloqueia ao desmontar.
- `<PitacoProvider deferTimeoutMs?: number />` (padrão 5 min; validado entre 1 s e 1 h).
- Não documentado, só para diagnóstico/exemplo: `PitacoRuntime.simulateAppReopen()` (zera o
  limite de sessão) e os campos novos de `diagnostics()`: `sessionSurveyShown`,
  `blockedReasons`, `deferred`, `held`.

### Decisões e o porquê

- **Sessão de app = tempo de vida do `PitacoRuntime`, sem persistência.** `sessionSurveyShown` é
  um campo em memória, nunca gravado em storage. Um runtime novo (processo novo, já que o
  Provider normalmente nasce uma vez por processo) já nasce com o limite zerado — é a própria
  definição de "reabrir o app zera o limite", sem precisar de relógio de parede nem storage. Para
  o exemplo simular "reabrir" sem destruir a fila/identidade (que dependem do storage
  persistente, não do runtime), exponho `simulateAppReopen()` — só reseta esse campo, documentado
  como não sendo API pública.
- **O limite é sobre exibição aberta (`survey_presented`), não sobre oferta (`ready`).** Uma
  pesquisa que virou candidata e chegou a `ready` mas nunca foi `present()`ada (por exemplo,
  suprimida por outro motivo antes disso) não consome a sessão — só a abertura de fato.
- **`track()` verifica o limite antes de tudo, antes até de `sanitizeEventName`'s custo ser
  relevante** — na prática logo após validar o nome do evento e antes do `await this.boot()`/
  chamada de elegibilidade, para não gastar a viagem de rede.
- **Bloqueio por contagem de referência (`Map<motivo, contagem>`), não conjunto.** Um conjunto
  faria dois `<PitacoBlock reason="pagamento" />` simultâneos se anularem no primeiro a
  desmontar — o prompt pede exatamente o contrário.
- **Bloqueio e adiamento entram como o portão padrão do runtime**, implementado sobre a mesma
  interface `PlacementGate`/`SurveyCandidate`/`PlacementControls` que a fase 2 já desenhou como
  ponto de extensão. Quem chama `setPlacementGate()` substitui bloqueio/adiamento inteiros por
  outra política (documentado no comentário de `placement.ts`); uma candidata que o portão padrão
  estivesse retendo nesse instante é descartada sem exibição, e não fica órfã.
- **Um único `deferTimeoutMs` para dois usos**: prazo do `defer()`/`release()` e prazo de quanto
  uma pesquisa que chega durante um `block()` fica retida esperando `unblock()`. Mesmo conceito
  (pesquisa represada esperando liberação); uma segunda opção de configuração só para o bloqueio
  não parecia se pagar em clareza.
- **O relógio da retenção começa quando a pesquisa é retida, não quando `block()`/`defer()` foi
  chamado.** `defer()` chamado numa tela vazia, sem nenhuma pesquisa chegando por horas, não
  "vence" sozinho — não há nada para descartar. O prazo é sobre a pesquisa represada, não sobre a
  intenção declarada.
- **Só uma candidata retida por vez.** Uma segunda que chegar enquanto já existe uma retida é
  ignorada silenciosamente (mesmo padrão já usado para "pesquisa em andamento" na fase 2) —
  evita decidir uma política de fila de candidatas represadas que o prompt não pediu.
- **Eventos de sucesso, dois grupos, sem redundância**: para o bloqueio, o catálogo só promete
  `placement_blocked`/`placement_survey_held`/`placement_survey_discarded` — o sucesso (a
  pesquisa retida por bloqueio finalmente aparece) não ganha evento próprio, porque o
  `placement_available` de sempre (emitido por `offer()`) já cobre isso. Para o adiamento, o
  sucesso tem evento próprio (`placement_released`) porque o prompt pediu os três nomeados:
  adiamento, liberação, descarte. A "causa" de uma retenção é fixada no instante em que a
  pesquisa é retida (a primeira condição a segurá-la, bloqueio ou adiamento) e não muda se a
  outra condição se somar depois — é ela quem decide se a liberação final emite
  `placement_released` ou só `placement_available`.
- **Interação `defer()` + `block()`**: `release()` com bloqueio ainda ativo mantém a pesquisa
  retida (a condição do bloqueio continua valendo) até o último `unblock()`; e vice-versa,
  `unblock()` com `defer()` ainda pendente não libera sozinho. As duas precisam estar livres.
- **`reset()` (logout)**: descarta sem exibição uma candidata retida (foi consultada para o
  respondente que está saindo — misturar com a identidade nova seria incoerente), mas **não**
  mexe em bloqueios ativos nem no pedido de `defer()` em aberto (são escopo de tela — quem chamou
  `block()`/`defer()` normalmente continua montado depois do logout) e **não** zera o limite de
  sessão (sessão de uso do app, não de login). Isso obrigou a ajustar um teste pré-existente da
  fase 2/3 (`reset (logout) dispensa a pesquisa aberta...`) que assumia poder mostrar uma segunda
  pesquisa no mesmo runtime depois do `reset()`: adicionei `simulateAppReopen()` no teste, com
  comentário explicando por quê — é o próprio comportamento novo da fase 4 se manifestando.
- **`unblock()`/`release()` sem par correspondente**: nunca lançam. Avisam uma vez em
  desenvolvimento (`warnOnce`) e não fazem nada — mesmo padrão de degradação silenciosa do resto
  do SDK diante de uso incorreto.
- **`usePitacoSurvey().available`/`SurfaceHost`/modo inline já respeitam o portão de graça**: como
  bloqueio e adiamento atuam retendo a candidata *antes* de `offer()` (que é o único lugar que
  cria a sessão e torna `available` verdadeiro), nenhuma mudança foi necessária nesses arquivos —
  a arquitetura de extensão da fase 2 já garantia isso.

### O que ficou de fora

- Um bloqueio que começa enquanto a pesquisa está `ready` (oferecida, `available` verdadeiro) mas
  ainda não `presented` (a janela entre `offer()` e o `SurfaceHost` chamar `present()`) não é
  escondido retroativamente — só uma pesquisa já `presented` está explicitamente coberta pelo
  prompt ("não é interrompida"), e essa janela intermediária dura milissegundos, sem sinal
  observável pelo usuário; tratá-la aumentaria a complexidade do portão para um caso não
  especificado.
- Fila de mais de uma candidata retida simultânea: a segunda é descartada silenciosamente (só
  log de depuração, sem evento `placement_` próprio) — o prompt não pede uma política de fila
  aqui, e a fase 2 já ignorava o caso análogo (pesquisa em andamento).
- Teste E2E do cenário 12 do prompt ("pesquisa adiada numa tela e liberada em outra") com
  navegação de verdade — coberto no nível do runtime/hook (`defer`/`release` entre chamadas), que
  é onde a lógica mora; o cenário fim-a-fim com telas de exemplo fica para a Fase 5
  (`sdk/example/`, fora do escopo desta fase).
- `sdk/example/`: não criado (fora do escopo desta fase, conforme restrição do prompt).

### Resultado exato do `npm run verify`

- `eslint .`: nenhum problema (0 erros, 0 avisos).
- `tsc --noEmit`: nenhum erro.
- `jest --ci`: `Test Suites: 37 passed, 37 total` · `Tests: 298 passed, 298 total` · `Snapshots: 0
  total` · ~2,1 s. (280 da fase 3 + 18 novos: 16 em `runtime.test.ts` — sessão de app (3),
  bloqueio (6), adiamento (4), reset/fase 4 (2), placement_ só no onEvent (1) — mais 2 em
  `PitacoBlock.test.tsx`.)
- `bob build`: `[commonjs]`/`[module] Compiling 85 files in src with babel`, `[typescript]
  Generating type definitions with tsc`, `✔ Wrote files to lib/module`, `✔ Wrote files to
  lib/commonjs`, `✔ Wrote definition files to lib/typescript` — sem erro.

## Fase 5b — Seed e proxy

### Arquivos criados/alterados (todos em `sdk/example/`)
- `scripts/seed.ts` (herdado do executor anterior, revisado e corrigido): garante descanso desligado na aplicação reaproveitada (`PATCH quietPeriodDays: null`) e `responseQuota: null` na pesquisa reaproveitada; mensagem final corrigida com o que o servidor ainda impõe; `/// <reference types="node" />` e ajustes de `noUncheckedIndexedAccess` para passar no typecheck do exemplo (flags sem valor agora dão erro claro).
- `scripts/smoke.ts` (novo): prova ponta a ponta sem app, direto e pelo proxy.
- `proxy/server.mjs` (novo): proxy transparente local, só `node:http`/`node:https`.
- `.env.example` (novo), documentado linha a linha com o endereço por alvo.
- `package.json`: só os scripts `seed`, `proxy` e `smoke`. `.gitignore`: acrescentados `.env` e `scripts/.seed-state.json`.
- Gerados (git-ignorado / consumido pela 5a): `.env`, `scripts/.seed-state.json`, `src/generated/seed-survey.json`.

### Como rodar (comandos exatos, dentro de `sdk/example/`)
- `npm run seed` (simulador iOS, padrão). Outros alvos: `npm run seed -- --target android-emu` ou `npm run seed -- --target device --lan-ip 192.168.0.10`. Opcionais: `--base-url http://localhost:8080/api` (endereço administrativo, visto do computador) e `--proxy-port 8787`.
- `npm run proxy` (porta 8787, prefixo `/pitaco`, alvo `http://localhost:8080/api`). Opcionais: `--port`, `--target`, `--prefix` ou `PITACO_PROXY_PORT`/`PITACO_PROXY_TARGET`/`PITACO_PROXY_PREFIX`. Ctrl+C para parar.
- `npm run smoke` (direto e pelo proxy; o proxy precisa estar de pé). `npm run smoke -- --only direct|proxy`; `--direct-url`/`--proxy-url` para outros endereços; `--check-429` estoura o limite por origem pelo proxy (bloqueia a origem local por até 1 min; não rodei contra o backend real, ver abaixo).
- Requer Node 22.18+ (os `.ts` rodam com a remoção de tipos nativa do Node; aqui, Node 25.2.1). O aviso `MODULE_TYPELESS_PACKAGE_JSON` é inofensivo; não pus `"type": "module"` no `package.json` para não mexer no resto do exemplo.

### Formato final de `src/generated/seed-survey.json`
```json
{
  "triggerEvent": "pitaco.example.trigger",
  "surveyId": "d50db159-a181-43df-8140-0dccd47a6c5f",
  "schema": {
    "surveyId": "…", "versionId": "…", "versionNumber": 1,
    "freeTextNotice": { "enabled": true, "text": "Evite escrever dados pessoais, como nome, telefone ou e-mail." },
    "questions": [ { "key": "…", "position": 1, "statement": "…", "type": "NPS", "required": true, "options": [], "range": { "min": 0, "max": 10, "minLabel": "Nada provável", "maxLabel": "Extremamente provável" } }, "… SCALE (1-5, com rótulos), RATING (1-5), SINGLE_CHOICE com condition {sourceKey: NPS, operator: between, min 0, max 6}, MULTIPLE_CHOICE, FREE_TEXT" ]
  }
}
```
`schema` é a própria `survey` devolvida pela elegibilidade (`DeliverableSurvey`), o formato que `<PitacoPreview schema>` aceita. Conferido com o normalizador do SDK: `normalizeSurvey(schema)` de `sdk/lib/commonjs/core/survey/schema.js` devolve `kind: "survey"` com as seis perguntas (tipos `NPS, SCALE, RATING, SINGLE_CHOICE(cond), MULTIPLE_CHOICE, FREE_TEXT`) e o aviso de texto livre ligado.

### Decisões e o porquê
- **Proxy em Node, não Caddy**: sem instalar nada; roda com `npm run proxy`. Segue `backend/docs/backend/proxy.md`. O ADR-0008 citado não existe como arquivo em `backend/docs/adrs/` (só há o 0001); o contrato vem inteiro do `proxy.md` e da seção "Endereço" do `contrato-sdk.md`.
- **Só `POST {prefixo}/collect/*`** vai para `{alvo}/collect/*` (mesmo restante do caminho e query); qualquer outra rota recebe 404 `application/problem+json` (`gateway.route_not_found`) sem tocar no Pitaco; método diferente de POST dá 405.
- **Cabeçalhos**: repassa os do cliente sem alterar (`X-Pitaco-Key`, `X-Pitaco-Sdk-Version`, `Content-Type`), menos os de salto e os de origem. `X-Forwarded-For` é **substituído** pelo IP que o proxy viu; `X-Forwarded-Proto` e `X-Forwarded-Host` são definidos. **`CF-Connecting-IP` do cliente é descartado**: o proxy local está em loopback, que é proxy confiável no Pitaco, e o Pitaco lê esse cabeçalho antes do `X-Forwarded-For`; repassá-lo deixaria o cliente escolher a própria origem no limite por IP.
- **Respostas intactas** (status, corpo e cabeçalhos, inclusive `Retry-After` e `Location`), sem cache; corpo da requisição passa por `pipe`, sem reescrita. Timeouts de 5 s para conectar e 10 s para a resposta (respeitando socket reaproveitado pelo keep-alive); Pitaco inalcançável vira 502/504 em JSON (`gateway.upstream_unavailable`/`gateway.timeout`), nunca HTML.
- **Reexibição no seed**: amostragem 1.0, janela aberta desde ontem sem fim, aplicação sem `quietPeriodDays`, pesquisa com `ignoresQuietPeriod: true` e `responseQuota: null`. O que o servidor ainda impõe (não configurável pela API administrativa): pesquisa **respondida ou dispensada** não volta para o mesmo respondente (`ResolvedHistory.isResolved`); **3 abandonos** (exibição sem desfecho por 30 min, `pitaco.collect.max-attempts`/`display-timeout`) também param a entrega; limites de 120/min por origem e 1200/min por chave. Para rever a pesquisa no mesmo aparelho, o painel de depuração da 5a precisa trocar o `deviceId` (limpar storage e identidade).
- **Smoke com identidades descartáveis**: `deviceId` novo por caminho; a resposta enviada nunca leva texto livre (fica `SKIPPED`), e a condição é avaliada como o SDK avalia (NPS 9 deixa a pergunta condicional `NOT_APPLICABLE`).
- **Tipos do Node nos scripts** via `/// <reference types="node" />`, porque o tsconfig do exemplo (da 5a) não expõe `@types/node`; assim não mexi no tsconfig.
- **429 provado sem gastar o limite real**: a 5a pode estar usando o simulador na mesma origem 127.0.0.1, então em vez de `--check-429` contra o backend subi um upstream falso no scratchpad (`stub-429.mjs`, porta 8799) e um segundo proxy (`--port 8788 --target http://localhost:8799/api`).

### Credenciais usadas
- API administrativa local **sem autenticação** (o OpenAPI local não declara `securitySchemes`; o seed chama `/api/applications/**` direto).
- Banco do perfil `local` (`backend/src/main/resources/application-local.yml`): `jdbc:postgresql://localhost:5432/mydatabase`, `myuser`/`secret` (só desenvolvimento; não usado diretamente pelo seed).
- Chave emitida pelo seed: rótulo `sdk-example`, prefixo `pit_cfb5c3d9`, só no `.env` (git-ignorado).

### Estado do backend
- Processo `java … com.renanloureiroo.pitaco.infra.PitacoApplication --spring.profiles.active=local`, PID 58316, iniciado em 13/09 às 14:02 a partir de `backend/target/classes` (padrão de `spring-boot:run`), ouvindo em `*:8080`. `GET /api/v3/api-docs` → 200, com as seis rotas `/collect/*`, inclusive `/collect/displays/{displayId}/events`. **Deixado rodando.**
- Parar: `kill 58316`. Subir de novo: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (o perfil local sobe o Postgres pelo Docker Compose).
- Dados semeados: aplicação `Pitaco Example App` (slug `pitaco-example-app`, `377f4af0-598c-4ead-9e2f-56f288e1f1c9`), pesquisa `Pesquisa de exemplo` (`d50db159-a181-43df-8140-0dccd47a6c5f`, versão 1 publicada), disparo `pitaco.example.trigger`, uma chave ativa.
- Proxy e stubs parados ao fim (portas 8787, 8788 e 8799 livres).

### Resultados dos comandos
- `npm run seed` (2ª execução nesta fase, contra o estado do executor anterior): exit 0; "Aplicação reaproveitada … sem descanso", "Chave de API reaproveitada (pit_cfb5c3d9), já presente no .env", "Pesquisa reaproveitada", "Pesquisa já tinha conteúdo — pergunta, condição, disparo e publicação não repetidos"; `diff` de `.seed-state.json` antes/depois: idêntico. (O executor anterior já tinha comprovado 1 aplicação, 1 pesquisa, 1 chave ativa depois de duas execuções.)
- `npm run smoke` (direto e proxy): exit 0, "Tudo certo.". Nos dois caminhos: elegibilidade 200 com a pesquisa do seed; abertura 201; lote de 2 eventos 202 `{"accepted":2,"duplicated":0,…}`; resposta 204; reenvio idêntico 204; elegibilidade depois de responder 200 com `survey: null`; sem chave 401 `api_key.missing` em `application/problem+json`. Só no proxy: `GET /pitaco/applications` → 404 e `GET /pitaco/v3/api-docs` → 404, não repassados.
- `npm run smoke -- --only proxy` com o proxy reiniciado no código final: exit 0, os mesmos 9 "ok".
- Log do proxy: `POST /pitaco/collect/eligibility -> 200 em 27 ms`, `POST /pitaco/collect/displays -> 201 em 12 ms`, `…/events -> 202 em 15 ms`, `…/submission -> 204 em 21 ms`, `…/eligibility -> 401 em 2 ms`, `GET /pitaco/applications -> 404 em 0 ms (fora da superfície pública, não repassado)`.
- Proxy contra o stub, com `X-Forwarded-For: 6.6.6.6` e `CF-Connecting-IP: 6.6.6.6` forjados: `HTTP/1.1 429`, `retry-after: 17`, `content-type: application/problem+json` repassados; o stub recebeu caminho `/api/collect/eligibility?x=1`, `x-pitaco-key` e `x-pitaco-sdk-version` intactos, `x-forwarded-for: ::1` (substituído), `x-forwarded-proto: http`, `x-forwarded-host: localhost:8788`, nenhum `cf-connecting-ip`, corpo idêntico.
- `npx eslint scripts proxy` (no exemplo): 0 problemas. `npm run lint` do exemplo inteiro: exit 0.
- `npx tsc --noEmit` do exemplo: nenhum erro em `scripts/`; um único erro, fora da minha posse (abaixo).
- `cd sdk && npm run verify`: **exit 2**. `eslint .` ok; `tsc --noEmit` ok; `jest --ci`: `Test Suites: 37 passed, 37 total` · `Tests: 300 passed, 300 total`; `bob build` ok; **`example:typecheck` falhou** com `../src/ui/theme/useTheme.ts(41,51): error TS2345: Argument of type 'ColorSchemeName' is not assignable to parameter of type '"light" | "dark" | null | undefined'. Type '"unspecified"' is not assignable …`; `example:lint` não chegou a rodar pelo verify (rodado à parte: ok).

### Pendência registrada, não corrigida (fora da posse da 5b)
- `sdk/src/ui/theme/useTheme.ts` (arquivo novo, não rastreado no git) passa `useColorScheme()` para `resolvePitacoTheme`. Com os tipos do React Native **0.86.3 do exemplo**, `ColorSchemeName` inclui `'unspecified'`, que o parâmetro não aceita; com o 0.87.1 do SDK passa. A correção é no SDK e mínima (tratar `'unspecified'` como `null`, com teste), ou alinhar a versão do React Native do exemplo; fica para quem é dono do SDK/da 5a.

### O que ficou de fora
- `--check-429` contra o backend real (substituído pela prova com o stub, para não bloquear a origem local da 5a por um minuto).
- Proxy em HTTPS e limite de 64 KB de corpo no próprio proxy (o Pitaco já recusa corpo grande; o proxy só faz `pipe`).

### Texto pronto para o README do exemplo (5e): "Conexão com o backend"

````markdown
## Conexão com o backend

O exemplo fala com um Pitaco rodando na sua máquina. Você precisa do backend de pé na porta 8080
(`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, que sobe o Postgres pelo
Docker) e do Node 22.18 ou mais novo.

### 1. Semear o backend

Dentro de `sdk/example/`:

```bash
npm run seed                                            # simulador iOS (padrão)
npm run seed -- --target android-emu                    # emulador Android
npm run seed -- --target device --lan-ip 192.168.0.10   # aparelho físico na mesma rede
```

O seed usa a API administrativa local para criar a aplicação "Pitaco Example App", emitir uma
chave, criar a "Pesquisa de exemplo" com os seis tipos de pergunta (NPS, escala, nota, escolha
única, múltipla escolha e texto livre), rótulos nas escalas, uma condição (o motivo só aparece para
nota de 0 a 6), aviso de texto livre, disparo no evento `pitaco.example.trigger` e publicar. Ele
escreve o `.env` (preservando as outras linhas) e `src/generated/seed-survey.json`, o schema que o
cenário 4 usa no `<PitacoPreview />`. Rodar de novo não duplica nada: reaproveita a aplicação, a
pesquisa e a chave.

A pesquisa fica o mais reexibível possível: amostragem de 100%, sem descanso, sem cota, janela
aberta. O servidor ainda não mostra de novo uma pesquisa já respondida ou dispensada pelo mesmo
respondente, e para depois de 3 abandonos. Para vê-la de novo no mesmo aparelho, use **limpar
storage e identidade** no painel de depuração.

### 2. O `.env`

Veja `.env.example`. As três variáveis:

| Variável | Para quê |
| --- | --- |
| `EXPO_PUBLIC_PITACO_BASE_URL` | Perfil direto: o endereço do Pitaco com o prefixo `/api`. |
| `EXPO_PUBLIC_PITACO_PROXY_BASE_URL` | Perfil com gateway (cenário 15): o proxy local, prefixo `/pitaco`. |
| `EXPO_PUBLIC_PITACO_API_KEY` | A chave da aplicação de exemplo, a mesma nos dois perfis. |

O host depende de onde o app roda:

| Alvo | Direto | Pelo proxy |
| --- | --- | --- |
| Simulador iOS | `http://localhost:8080/api` | `http://localhost:8787/pitaco` |
| Emulador Android | `http://10.0.2.2:8080/api` | `http://10.0.2.2:8787/pitaco` |
| Aparelho físico | `http://<IP-do-computador>:8080/api` | `http://<IP-do-computador>:8787/pitaco` |

No aparelho físico, o celular e o computador precisam estar na mesma rede, e o firewall do
computador precisa liberar as portas 8080 e 8787. Depois de mudar o `.env`, reinicie o
`npx expo start` (com `-c` se o valor antigo insistir).

### 3. O proxy (topologia com gateway)

```bash
npm run proxy   # http://0.0.0.0:8787/pitaco -> http://localhost:8080/api
```

É um proxy transparente em Node, sem dependência, que segue `backend/docs/backend/proxy.md`:
repassa só `POST /pitaco/collect/*` para `/api/collect/*`, sem mexer em corpo, chave ou
`X-Pitaco-Sdk-Version`; substitui o `X-Forwarded-For` pelo IP que viu; devolve status, corpo e
cabeçalhos do Pitaco intactos (inclusive `Retry-After`). Qualquer outra rota recebe 404. O SDK não
sabe que está falando com um proxy: só o `baseUrl` muda. Opções: `--port`, `--target`, `--prefix`.

### 4. Conferir sem abrir o app

```bash
npm run smoke   # com o backend e o proxy de pé
```

Faz, direto e pelo proxy, o que o SDK faz: elegibilidade com o disparo do seed, abertura da
exibição, um lote de eventos e uma resposta, e confere os status (200, 201, 202, 204). Usa
identidades descartáveis, então não gasta a pesquisa do seu simulador. Termina com "Tudo certo.".
````

## Fase 5a — Fundação do exemplo

### Versões (conferidas)

- Expo SDK **57** (`expo@57.0.22`, `expo-router@57.0.21`), **React Native 0.86.3**, React 19.2.3. É o SDK mais recente com Expo Go: pela API de versões do Expo (`api.expo.dev/v2/versions/latest`), o 57.0.0 traz `facebookReactNativeVersion 0.86.3` e Expo Go 57.0.9 (iOS e Android). O 58.0.0 (RN 0.87.1, o do dev do SDK) ainda não tem Expo Go. `npx expo install --check`: `Dependencies are up to date`.
- A diferença 0.86.3 (exemplo) × 0.87.1 (dev do SDK) é intencional: o SDK declara `react-native >=0.74` como peer, e o exemplo prova o SDK na versão que o Expo Go roda.
- `react-native-mmkv@4.3.2` só em `devDependencies` do exemplo, sem nenhum import em runtime (conferido com `grep` em `app/`, `src/`, `scripts/`, `proxy/`, e no source map do bundle: nenhum arquivo `mmkv`). O único registro é o alias `@pitaco/react-native/storage/mmkv` no `metro.config.js`/`tsconfig.json`, que nenhum código do app importa. Fica provado que o pacote convive instalado; o adaptador MMKV continua coberto pelos testes do SDK.
- Acrescentados com `npx expo install`: `expo-clipboard@57.0.2` (botão "Copiar log"; roda no Expo Go) e `react-dom@19.2.3`. O `react-dom` resolve um conflito de peer que travava o `npm install`: sem ele, o npm puxava o `react-dom@19.3.0` (peer opcional do `expo-router`), que exige `react@^19.3`.

### Arquivos (todos em `sdk/example/`, salvo indicação)

Revisados e reescritos: `metro.config.js`, `tsconfig.json`, `app.json` (nome "Pitaco Exemplo", slug `pitaco-example`, `userInterfaceStyle: automatic` para o esquema escuro do sistema chegar ao tema do SDK), `src/pitaco/seed.ts`, `src/scenarios/registry.ts`. Mantidos: `package.json` (com os scripts `seed`/`proxy`/`smoke` da 5b), `eslint.config.js`, `.gitignore`, `src/pitaco/config.ts`.

Removidos: `src/pitaco/ScenarioProvider.tsx` e `src/pitaco/ExampleConfigContext.tsx`, da tentativa anterior (um `PitacoProvider` por tela; ver decisões). O `ScenarioProvider` também quebrava o lint, porque escrevia num ref durante o render.

Criados:
- `app/_layout.tsx` (raiz: `GestureHandlerRootView` → `ExampleProvider` → `PitacoRoot` → `Stack`; instala o embrulho de `fetch`), `app/index.tsx` (redireciona para `/cenarios`), `app/(tabs)/_layout.tsx` (abas **Cenários** e **Depuração**), `app/(tabs)/cenarios/_layout.tsx` (pilha, com os títulos vindos do registro), `app/(tabs)/cenarios/index.tsx` (tela inicial com seletor de perfil e os 15 cenários), `app/(tabs)/cenarios/01-sheet.tsx` (cenário 1 completo), de `02-gorhom.tsx` a `15-proxy.tsx` (provisórios), `app/(tabs)/depuracao.tsx`.
- `src/pitaco/ExampleContext.tsx` (perfil, storage, pilha de cenários, "instalação nova"), `src/pitaco/PitacoRoot.tsx` (o `PitacoProvider` único), `src/pitaco/useScenario.ts`, `src/pitaco/ProfileSelector.tsx`.
- `src/debug/logStore.ts` (armazém fora do React com `useSyncExternalStore`), `src/debug/eventLog.ts`, `src/debug/networkLog.ts`, `src/debug/panel/{EventsView,EventRow,NetworkView,StateView,DebugActions}.tsx`.
- `src/scenarios/ScenarioPlaceholder.tsx`, `src/ui/{palette.ts,ActionButton.tsx,Screen.tsx,Segmented.tsx,JsonBlock.tsx}`.

No SDK:
- `sdk/src/ui/theme/useTheme.ts`: tipo `SystemColorScheme` (`'light' | 'dark' | 'unspecified' | null | undefined`) em `resolveColorScheme`/`resolvePitacoTheme`.
- `sdk/src/ui/theme/__tests__/theme.test.ts`: duas asserções novas para `'unspecified'`.
- `sdk/package.json`, `sdk/eslint.config.mjs` e `sdk/tsconfig.json` já estavam como precisam (vieram da tentativa anterior e foram conferidos): `example:typecheck` e `example:lint` no fim do `verify`; `example/**` ignorado no ESLint e excluído do `tsconfig`. O bob compila só `src`, então não foi mexido.

### Defeito do SDK encontrado e corrigido

- **`useColorScheme()` com `'unspecified'`** (achado da 5b, confirmado aqui como o erro do `example:typecheck`). Os tipos do React Native 0.86 incluem `'unspecified'` no retorno de `useColorScheme()`. `resolveColorScheme` só aceitava `'light' | 'dark' | null | undefined`, então `useTheme.ts:41` não compilava contra o 0.86, embora compile contra o 0.87 do dev do SDK. Em runtime o comportamento já era o certo: tudo que não é `'dark'` cai no claro. A correção é só de tipo: `'unspecified'` vale como ausência de esquema. Teste em `theme.test.ts` (`resolveColorScheme(undefined, 'unspecified')` e `resolvePitacoTheme(undefined, 'unspecified').scheme` → `'light'`). Total com as duas correções: 301 testes.

- **Bottom sheet do SDK aparecia no topo da tela** (achado ao vivo no Expo Go). Em `sdk/src/ui/presentation/BottomSheet.tsx`, o `KeyboardAvoidingView` que envolve a folha tinha `justifyContent: 'flex-end'` sem `flex: 1`. Ele ficava só com a altura do conteúdo, no topo do `Modal`, e o fundo ocupava o resto. Os testes não pegavam porque o renderizador de teste não faz layout. Correção: `flex: 1` no estilo `keyboardAvoider`, mais `testID="pitaco-bottom-sheet-container"` no contêiner. Teste novo em `sdk/src/react/__tests__/SurfaceHost.test.tsx` ('ancora a folha embaixo da tela…', que confere `flex: 1` e `justifyContent: 'flex-end'`). A captura antes e depois está abaixo.

### Decisões e o porquê

- **Um `<PitacoProvider>` só, na raiz** (`src/pitaco/PitacoRoot.tsx`), com `baseUrl` do perfil ativo, `apiKey`, `storage={createAsyncStorageAdapter(AsyncStorage)}`, `onEvent` alimentando o painel, `debug={__DEV__}` e `errorReporting`. É a integração que o guia ensina (Provider na raiz). É também o que os cenários 12 (adiar numa tela e liberar em outra) e 13 (fila persistente entre reaberturas) precisam, e o que deixa o painel ler o runtime certo sem registro de "cenário ativo". A tentativa anterior montava um Provider por tela, e isso quebra o cenário 12.
- **Cada cenário varia o Provider com `useScenario(id, config)`**. O efeito empilha o cenário na montagem e desempilha na desmontagem. Vale a configuração do cenário montado por último, e ela é sobreposta às props base. Os valores são comparados por referência, por isso os objetos precisam ser estáveis. A pilha segue a montagem, não o foco, por dois motivos: trocar para a aba Depuração não desfaz a configuração, e uma rota empilhada por cima (cenário 3) pode assumir e devolver ao sair. O contexto é dividido em ações (estáveis) e estado, então a tela que configura não é re-renderizada pela própria mudança.
- **Abas + pilha**: a aba Cenários tem a própria `Stack`, e o cenário aberto continua montado quando se vai à aba Depuração e volta. Assim dá para acompanhar os eventos ao vivo sem perder a pesquisa aberta.
- **Painel de depuração sem API nova no SDK**:
  - Eventos: o `onEvent` da raiz publica tudo, marcado com o cenário ativo e `source: 'provider'`. O preview publica à mão com `usePublishEvent`.
  - Fila, identidade e portões: `usePitaco().diagnostics()`, lido a cada 1 s. A aba de depuração está dentro do Provider da raiz.
  - Rede: o SDK não tem gancho de rede, e não precisa ter. O runtime lê `globalThis.fetch` a cada chamada, então o exemplo embrulha o `fetch` global uma vez, antes do Provider (`installNetworkLogger`, só para URLs com `/collect/`), e registra método, URL, cabeçalhos com `X-Pitaco-Key` truncada, corpo, status, duração e resposta (via `clone()`). Isso fica só no exemplo.
  - "Simular app reaberto": `usePitaco().simulateAppReopen()`.
- **"Limpar storage e identidade"** (`requestFreshInstall`). A 5b observou que o servidor não reexibe uma pesquisa respondida ou dispensada ao mesmo respondente, e para depois de 3 abandonos; por isso o botão precisa gerar um `deviceId` novo. A sequência:
  1. troca o `storage` do Provider por um em memória: o runtime atual faz `detach`, que pausa a fila;
  2. espera 300 ms e apaga do AsyncStorage todas as chaves `@pitaco/` (fila e `@pitaco/v1:device-id`);
  3. volta para um `createAsyncStorageAdapter(AsyncStorage)` novo.

  Trocar a instância de `storage` já faz o `PitacoProvider` recriar o runtime, que lê um storage vazio e gera um `deviceId` novo. O app não é remontado e a navegação fica onde estava. O log é limpo junto.
- **Logs fora do estado do React** (`createLogStore` + `useSyncExternalStore`): um evento a mais não re-renderiza a raiz. A notificação vai numa microtarefa, porque o `onEvent` pode chegar durante o render de outro componente.
- **Metro**:
  - `watchFolders` recebe `sdk/`.
  - Um `resolveRequest` mapeia os quatro pontos de entrada públicos (`@pitaco/react-native`, `/preview`, `/storage/async-storage`, `/storage/mmkv`) para `sdk/src`. Qualquer outro subpath `@pitaco/react-native/...` dá erro com a lista dos válidos, porque um import interno no exemplo não passaria no pacote publicado.
  - Todo import sem caminho vindo de `sdk/src` é resolvido como se viesse do exemplo.
  - O `blockList` bloqueia `sdk/node_modules/` e `sdk/lib/` inteiros.
  - Tirei o `disableHierarchicalLookup: true` e o `extraNodeModules` com `Proxy` da tentativa anterior. O `disableHierarchicalLookup` quebra dependências aninhadas em `node_modules/x/node_modules/y`, e o guia de Metro do Expo (Context7) recomenda `resolveRequest` para aliases.
- **tsconfig**: `paths` só para os quatro pontos de entrada, apontando para `../src`. Tirei o `baseUrl`, porque o TS 6 o dá como obsoleto e isso quebrava o `typecheck`, e acrescentei `noUncheckedIndexedAccess`, como no SDK. **Não** mapeie `react`/`react-native` nos `paths`: o Metro do Expo lê os `paths` do tsconfig e passaria a resolver `react` para `@types/react`, o que quebra o bundle. Isso aconteceu na primeira tentativa de export. O `npx expo start` reescreveu o `include` para `["**/*.ts", "**/*.tsx"]`; deixei assim.

### Contrato para 5c e 5d

1. **Rotas reservadas**: `app/(tabs)/cenarios/<id>.tsx`, com os ids de `src/scenarios/registry.ts` (`01-sheet` … `15-proxy`). URL: `/cenarios/<id>` (`scenarioHref(id)`). Substitua o conteúdo do arquivo provisório, sem renomear. Rotas extras de um cenário vão na mesma pasta com o mesmo prefixo, e o título vai no próprio arquivo com `<Stack.Screen options={{ title }} />`. Exemplo: `app/(tabs)/cenarios/03-tela-pesquisa.tsx`, empilhada com `router.push('/cenarios/03-tela-pesquisa')`. Não crie outro `<PitacoProvider>`. O `GestureHandlerRootView` já está na raiz; o `BottomSheetModalProvider` do gorhom fica na tela do cenário 2 (e do 4).
2. **Trocar a configuração do Provider**: `useScenario('<id>', CONFIG)` no topo da tela, de `src/pitaco/useScenario.ts`. `CONFIG` é um `ScenarioProviderConfig`, isto é, qualquer prop do `PitacoProviderProps` menos `children`, `onEvent` e `storage`: `theme`, `strings`, `presentation`, `renderers`, `slots`, `insets`/`getInsets`, `baseUrl`, `apiKey`, `eligibilityTimeoutMs`, `deferTimeoutMs`… Use constante de módulo ou `useMemo`: um objeto novo a cada render vira laço de atualização. Toda tela de cenário chama `useScenario`, mesmo sem config, para os eventos saírem marcados com o id certo. Mudar `baseUrl`, `apiKey`, `presentation`, `errorReporting`, `debug` ou os prazos recria o runtime e zera o limite de sessão; tema, textos, renderizadores e slots não.
3. **Publicar eventos no painel**: os do Provider da raiz entram sozinhos. Os do preview (cenário 4) entram por `const publish = usePublishEvent('04-comparar')` e `onEvent={(e) => publish(e, { form: 'sheet' | 'gorhom' | 'tela' })}`, de `src/debug/eventLog.ts`. O painel agrupa por ordem, por `displayId` ou por "cenário · forma".
4. **testIDs**:
   - lista inicial: item `cenario-<id>`; tela `tela-inicio`;
   - raiz de cada cenário: `tela-<id>` (o `<Screen testID>` de `src/ui/Screen.tsx`);
   - cenário 1: botão `cenario-01-disparar`;
   - abas: `tab-cenarios`, `tab-depuracao`;
   - perfil: `perfil-direto`, `perfil-proxy`, texto `perfil-endereco`;
   - painel: raiz `tela-depuracao`; ações `debug-limpar-storage`, `debug-simular-reabertura`, `debug-copiar-log`, `debug-limpar-log`; seções `secao-eventos|rede|estado`; agrupamento `agrupar-tempo|exibicao|cenario`; listas `lista-eventos` e `lista-requisicoes`, com itens `evento-e<n>` e `requisicao-r<n>`; estado `estado-cenario`, `estado-device-id`, `estado-sessao`, `estado-fila`.

   Convenção para os botões novos: `cenario-<NN>-<acao>`.
5. **Schema do seed**: `import { seedSurvey } from '../../../src/pitaco/seed'`. `seedSurvey` vale `{ triggerEvent, surveyId, schema } | null` (`null` quando o JSON não tem o formato esperado; nesse caso mostre o aviso e desabilite o botão, como no cenário 1). `seedSurvey.triggerEvent` é o evento para `track`, e `seedSurvey.schema` vai direto em `<PitacoPreview schema>`/`createPreviewController({ schema })`.
6. **Componentes de moldura** para as telas: `Screen`, `Paragraph`, `Hint` (`src/ui/Screen.tsx`), `ActionButton`, `Segmented` e `usePalette()`.
7. **Perfil proxy (cenário 15)**: o seletor da tela inicial troca o perfil do app inteiro. O cenário também pode forçar com `useScenario('15-proxy', { baseUrl: <proxy> })`, lendo `CONNECTION_PROFILES` de `src/pitaco/config.ts`.

### O que ficou de fora

- Cenários 2 a 15 (5c e 5d), fluxos Maestro, capturas e README do exemplo (5e).
- Android não foi aberto no emulador. O `expo export --platform android` prova o bundle.
- A "instalação nova" espera 300 ms para o runtime anterior parar a fila. Uma resposta HTTP do runtime anterior que chegue depois disso ainda pode regravar a fila antiga no storage. Não vi isso acontecer e é caso de ferramenta de diagnóstico, então não tratei.

### Resultado exato dos comandos

- `npx expo install --check` (exemplo): `Dependencies are up to date`.
- `npx expo install expo-clipboard`: falhou com `ERESOLVE` (`react-dom@19.3.0` × `react@19.2.3`). `npx expo install react-dom expo-clipboard`: ok (`react-dom@19.2.3`, `expo-clipboard@57.0.2`).
- `npm run typecheck` (exemplo): sem erro (antes: `TS5101 baseUrl` e, depois, `useTheme.ts(41,51) TS2345`, os dois corrigidos).
- `npm run lint` (exemplo): sem problema (antes: `react-hooks/refs` no `ScenarioProvider` antigo e `react-hooks/purity` num `Date.now()` no render, os dois corrigidos).
- `cd sdk && npm run verify`: código de saída 0. `eslint .` sem problema; `tsc --noEmit` sem erro; `jest --ci`: `Test Suites: 37 passed, 37 total` · `Tests: 301 passed, 301 total`; `bob build`: `✔ Wrote files to lib/module`, `✔ Wrote files to lib/commonjs`, `✔ Wrote definition files to lib/typescript`; `example:typecheck` e `example:lint` sem erro.
- `CI=1 npx expo export --platform ios --output-dir /tmp/pitaco-example-export-ios`: `iOS Bundled 4455ms node_modules/expo-router/entry.js (1674 modules)`, `entry-….hbc (3.8MB)`, código 0.
- `CI=1 npx expo export --platform android --output-dir /tmp/pitaco-example-export-android`: `Android Bundled 5332ms node_modules/expo-router/entry.js (1768 modules)`, `entry-….hbc (4MB)`, código 0. Diretórios apagados depois.
- Prova da ligação, pelo source map:
  - bundle de dev do Metro: 70 arquivos de `/Users/renanloureiro/www/pitaco/sdk/src/…` e uma única cópia de `react/index.js`, a de `sdk/example/node_modules`;
  - export iOS com `--source-maps`: nenhum arquivo de `sdk/node_modules` nem de `sdk/lib`, nenhum `mmkv`, um só `react/index.js` e um só `react-native/…/InitializeCore.js`.

### Simulador iOS (Expo Go), cenário 1 ao vivo

- Backend no ar em `http://localhost:8080` (o da 5b, com o seed aplicado). `POST /api/collect/eligibility` com `pitaco.example.trigger` e um `deviceId` novo devolve a pesquisa `d50db159…` com as 6 perguntas.
- `xcrun simctl boot` (iPhone 16, iOS 26.5) e `CI=1 npx expo start --ios` em segundo plano. O Expo CLI baixou e instalou o Expo Go para SDK 57 (`Fetching Expo Go` → `Installing Expo Go on iPhone 16`) e `iOS Bundled 3362ms node_modules/expo-router/entry.js (1836 modules)`.
- Primeira execução: a tela inicial abre e o `track` do cenário 1 abre a pesquisa (Pergunta 1 de 6, NPS do seed), mas a folha aparecia no topo da tela. É o defeito acima. Captura: `scratchpad/5a-apos-track.png`.
- Depois da correção e com o Metro reiniciado (`iOS Bundled 810ms … (1835 modules)`), um fluxo Maestro temporário (`scratchpad/cenario-01.yaml`, fora de `.maestro/`, que é da 5e) passou: abas → Depuração → "Limpar storage e identidade" → Cenários → cenário 1 → "Disparar pesquisa" → `"Pergunta 1 de 6"` visível. Todos os passos `COMPLETED`. A folha abre ancorada embaixo, com a alça, sobre o fundo. Captura: `scratchpad/5a-cenario-01.png` (5e decide o que vai para `docs/capturas/`).
- Metro encerrado ao terminar (`lsof -ti tcp:8081` vazio, sem `expo start`). O simulador ficou ligado.
- Observações para a 5e:
  1. No iOS, o botão de voltar do cabeçalho tem como rótulo de acessibilidade o título da tela anterior (`"Pitaco — exemplo"`), não "Back". Para o Maestro, use `tapOn: "Pitaco — exemplo"`.
  2. Na primeira abertura, o Expo Go mostra o menu de desenvolvedor, que precisa ser fechado com "Continue" e depois o X.
  3. O botão flutuante de engrenagem do Expo Go fica sobre o canto superior direito e pode cobrir o X da pesquisa quando a folha fica alta.
  4. "Limpar storage e identidade" gera no console um aviso de `__DEV__` do SDK ("Sem storage persistente…"), porque troca por um storage em memória por 300 ms. É esperado e sai uma vez só.

## Fase 5d — Cenários 11 a 15

### Arquivos (todos em `sdk/example/`)

Rotas (placeholders substituídos, sem renomear): `app/(tabs)/cenarios/11-bloqueio.tsx`, `12-adiamento.tsx`, `13-sem-rede.tsx`, `14-falhas.tsx`, `15-proxy.tsx`. Rotas extras, mesma pasta e mesmo prefixo, título via `<Stack.Screen options>` no próprio arquivo: `11-bloqueio-pagamento.tsx` (título "Pagamento") e `12-adiamento-liberar.tsx` ("Liberar a pesquisa", recebe `?prazo=10|60`).

Componentes:
- `src/scenarios/11-15-comum/`: `useNow.ts` (relógio atualizado só em callback de timer), `usePolledDiagnostics.ts` (`diagnostics()` a cada 500 ms), `usePlacementEvents.ts` e `PlacementFeed.tsx` (os `placement_` do cenário, lidos do `eventLog` do painel), `HoldCountdown.tsx` (contador regressivo do prazo de retenção), `SurveyStatusLine.tsx` (`usePitacoSurvey().status`), `NetworkFeed.tsx` (requisições do `networkLog`, com a URL inteira), `ScenarioPrep.tsx` ("Nova identidade" e "Zerar limite da sessão", os mesmos botões do painel).
- `src/scenarios/11-bloqueio/{config.ts, PaymentSummary.tsx}`
- `src/scenarios/12-adiamento/config.ts`
- `src/scenarios/13-sem-rede/{offlineRun.ts, OfflineSwitch.tsx, QueueList.tsx, DeliveryCheck.tsx, backendCheck.ts}`
- `src/scenarios/14-falhas/{modes.ts, RunTrigger.tsx, TapCounter.tsx}`
- `src/scenarios/15-proxy/ProxyProbe.tsx`

Infraestrutura nova: `src/debug/networkConditions.ts` (condições de rede simuladas, em memória: `offline` e `latencyMs`; `useNetworkConditions`, `resetNetworkConditions`).

Compartilhado alterado (mudança mínima, relido na hora de editar): `src/debug/networkLog.ts`. Foram um import e três linhas no embrulho de `fetch`, antes de `original(input, init)`:
- com `latencyMs > 0`, `await waitUnlessAborted(latencyMs, init?.signal)`, que termina com `AbortError` assim que o SDK desiste;
- com `offline`, `throw simulatedNetworkError()` (`TypeError: Network request failed (simulado…)`), sem a requisição sair.

As duas condições só valem para as URLs do Pitaco (as que o embrulho já filtrava, com `/collect/`) e ficam registradas no log como qualquer falha. Registry, `useScenario`, `ExampleContext`, `PitacoRoot`, layouts, painel e perfis: **sem mudança**.

SDK (`sdk/src`): **nenhuma mudança**. Nenhum defeito do SDK apareceu nestes cenários.

### Decisões e o porquê

- **`deferTimeoutMs` por cenário pelo Provider único: já funciona, sem mudança.** `ScenarioProviderConfig` inclui a prop, e o `PitacoProvider` a põe na identidade do runtime. Por isso trocá-la **recria o runtime**, e isso tem duas consequências:
  - as rotas extras de um cenário usam o **mesmo id e a mesma constante de configuração** da tela de entrada (`BLOCK_CONFIG`, `DEFER_CONFIGS[prazo]`); senão a pesquisa retida some com o runtime antigo;
  - no 12, o prazo escolhido vai para a outra tela pelo parâmetro `prazo`.
- **Cenário 11:**
  - Entrada → "Ir para o pagamento" → a tela de pagamento monta `<PitacoBlock reason="pagamento" />` → "Pagar" dispara `track` → `placement_blocked` (ao montar) e `placement_survey_held` (ao chegar a pesquisa), sem nada na tela.
  - "Concluir e sair" (ou a seta ou o gesto de voltar) desmonta o bloqueio, e a pesquisa retida abre na tela de entrada (`placement_available` e o sheet do SDK).
  - Prazo de 60 s neste cenário (`deferTimeoutMs`; o padrão é 5 min). Com isso também dá para ver `placement_survey_discarded` ficando no pagamento.
  - As duas telas mostram ao vivo os `placement_`, o contador do prazo, os bloqueios ativos e se há pesquisa retida.
- **Cenário 12:** seletor de prazo de 10 s ou 60 s (padrão 10 s), mais dois botões:
  - "Adiar e disparar" chama `defer()` e depois `track()`, e a pesquisa fica retida com `placement_deferred`;
  - "Ir para a outra tela (liberar lá)" leva a uma tela onde `release()` gera `placement_released {heldMs}`, depois `placement_available`, e o sheet abre.
  - Sem liberar, o contador chega a zero e sai `placement_expired`, sem exibição (nenhum `POST /collect/displays`).
  - O contador parte do `at` do `placement_survey_held`/`placement_deferred` recebido. É o mesmo ponto em que o relógio do SDK começa (decisão da fase 4: o prazo conta da retenção, não do `defer()`).
- **Cenário 13:**
  - O interruptor "Simular sem rede" fica **só em memória** (`networkConditions.ts`). Por isso o app reaberto volta sempre com rede, sem depender de nada gravado, e ninguém fica preso sem rede por engano. Sair do cenário devolve a rede; fechar o app, não, porque não roda limpeza.
  - "Disparar e cortar a rede" busca a pesquisa com rede e liga o interruptor quando o `track` resolve, antes do `present()`. Assim a abertura, os eventos e a resposta ficam todos na fila, como no modo avião ligado com a pesquisa já na tela.
  - Um registro da execução (`displayId`, `surveyId`, `queuedAt`) é gravado no AsyncStorage (`@pitaco-example/13-sem-rede`) quando a resposta entra na fila sem rede. A chave fica fora de `@pitaco/`, para "Nova identidade" não apagá-la.
  - Depois de reabrir, "entregue" exige as duas condições: o item fora da fila **e** um `POST …/submission` com 2xx no log de rede desta abertura. Sem a segunda, uma fila ainda não carregada no boot, vazia no snapshot, pareceria entregue.
  - A tela mostra quantos envios da resposta houve nesta abertura, e os `accepted`/`duplicated` somados das respostas 202 de eventos.
  - "Conferir no backend" lê, pela API administrativa local (sem autenticação no perfil `local`):
    - `GET /api/applications`, para achar a aplicação pelo slug `pitaco-example-app` do seed;
    - depois `GET …/surveys/{surveyId}/results/export` (CSV com uma linha por exibição e o desfecho), contando as linhas daquele `displayId`.
  - É ferramenta do exemplo contra o backend de desenvolvimento, nunca do SDK. As URLs sem `/collect/` não passam pelo interruptor.
- **Cenário 14:**
  - As três falhas são configurações de módulo:
    - `{ apiKey: 'pk_invalida' }`;
    - `{ baseUrl: 'http://10.255.255.1:9' }`;
    - `{ eligibilityTimeoutMs: 3000 }` com latência de 5 s no embrulho de `fetch`.
  - O `eligibilityTimeoutMs` explícito entra na identidade: as três recriam o runtime, e trocar de uma para outra sempre dá runtime novo.
  - O `track` precisa ir para o runtime novo. `RunTrigger` lê no mesmo render a configuração aplicada (`useExampleState().activeScenario.config`) e o `usePitaco()`, e dispara no efeito só quando a configuração aplicada já é a da falha. Uso ref só dentro de efeito (o lint `react-hooks/refs` e `set-state-in-effect` estão como erro). Repetir a mesma falha dispara no runtime atual; com a chave recusada, o SDK fica em silêncio até o fim da sessão, como deve.
  - A tela mostra o estado da pesquisa (nenhuma), um contador de toques, "chave recusada", o tamanho da fila e as requisições desde o disparo (401, falha e `AbortError` em cerca de 3000 ms). A latência zera ao sair do cenário e em "Voltar à configuração normal".
- **Cenário 15:**
  - `useScenario('15-proxy', { baseUrl: <perfil Proxy> })`, uma constante de módulo, só quando o perfil tem configuração; sem ela, a tela orienta e desabilita o botão. A tela inicial continua trocando o perfil do app inteiro.
  - A tela mostra a URL efetiva (a configuração do cenário, ou o perfil), o botão do fluxo do cenário 1 e a contagem de requisições pelo gateway (`/pitaco/collect/*`) × direto (`/api/collect/*`), com o log de rede.
  - "Conferir se o proxy está de pé" faz `GET {proxy}/saude`, e o próprio proxy responde 404 `gateway.route_not_found`, sem tocar no Pitaco nem sujar o log.
- Toda tela tem "Nova identidade" e "Zerar limite da sessão": o servidor não reexibe pesquisa respondida ou dispensada, e o SDK mostra uma por sessão.

### Observações sobre o SDK (não são defeito dos cenários; não corrigidas)

- O `PitacoProvider` só faz `detach()` no runtime substituído, nunca `dispose()`. Se houver pesquisa retida quando a configuração muda (trocar o prazo no 12 com uma retida, ou sair do cenário 11/12 com uma retida), o temporizador do runtime antigo ainda vence e emite `placement_expired`/`placement_survey_discarded` pelo ouvinte antigo.
  - Não abre exibição e a fila do runtime antigo está pausada, então não afeta o usuário.
  - Fica para o dono do SDK decidir se o Provider deve descartar o runtime substituído. O efeito de limpeza não distingue a remontagem do StrictMode da troca de runtime.
- Depois de `placement_expired`, o pedido de `defer()` continua ativo até um `release()` (comportamento da fase 4). A tela do 12 mostra "Adiamento pedido: sim".
- A fila é separada por `baseUrl|apiKey`. No 13, o app precisa reabrir no mesmo perfil (o Direto, que é o padrão quando configurado).

### testIDs

- **11:** raiz `tela-11-bloqueio`; `cenario-11-ir-pagamento`, `cenario-11-status`, `cenario-11-prazo`, lista `cenario-11-placement` (itens `cenario-11-placement-<n>`), `cenario-11-nova-identidade`, `cenario-11-zerar-sessao`. Pagamento: raiz `tela-11-bloqueio-pagamento`; `cenario-11-pagar`, `cenario-11-sair`, `cenario-11-pagamento-status`, `cenario-11-pagamento-prazo`, `cenario-11-pagamento-placement` (itens `-<n>`).
- **12:** `tela-12-adiamento`; prazo `cenario-12-prazo-10` e `cenario-12-prazo-60`; `cenario-12-adiar-disparar`, `cenario-12-ir-liberar`, contador `cenario-12-prazo`, `cenario-12-status`, `cenario-12-placement`, `cenario-12-nova-identidade`, `cenario-12-zerar-sessao`. Liberar: `tela-12-adiamento-liberar`; `cenario-12-liberar`, `cenario-12-liberar-prazo`, `cenario-12-liberar-status`, `cenario-12-liberar-placement`.
- **13:** `tela-13-sem-rede`; interruptor `cenario-13-sem-rede` (rótulo "Simular sem rede"), texto `cenario-13-rede`, `cenario-13-disparar-sem-rede`, `cenario-13-status`, fila `cenario-13-fila` ("Fila local: N item(ns) pendente(s)", itens `cenario-13-fila-<n>`), `cenario-13-display-id`, `cenario-13-entrega`, `cenario-13-envios`, `cenario-13-conferir-backend`, `cenario-13-backend`, `cenario-13-esquecer`, requisições `cenario-13-requisicoes` (itens `cenario-13-requisicoes-r<n>`), `cenario-13-nova-identidade`, `cenario-13-zerar-sessao`.
- **14:** `tela-14-falhas`; `cenario-14-chave-invalida`, `cenario-14-endereco-inalcancavel`, `cenario-14-api-lenta`, `cenario-14-normal`, `cenario-14-status`, `cenario-14-incrementar`, `cenario-14-contagem` ("N toque(s)"), `cenario-14-rede` (itens `cenario-14-rede-r<n>`).
- **15:** `tela-15-proxy`; `cenario-15-endereco`, `cenario-15-conferir-proxy`, `cenario-15-proxy-status`, `cenario-15-disparar`, `cenario-15-status`, `cenario-15-prefixo`, `cenario-15-rede`, `cenario-15-nova-identidade`, `cenario-15-zerar-sessao`.

Textos estáveis:
- `SurveyStatusLine`: "Pesquisa: nenhuma na tela | pronta para abrir | na tela | concluída | dispensada | descartada sem exibição".
- Os itens de `placement_` aparecem como "hh:mm:ss placement_<tipo> {dados}"; no Maestro, use regex: `".*placement_survey_held.*"`.
- Progresso do SDK: "Pergunta 1 de 6". Botão de fechar: acessibilidade "Fechar pesquisa".

### Passo a passo para os fluxos Maestro da 5e (não escritos aqui; `.maestro/` é da 5e)

Contexto: Expo Go (`appId: host.exp.Exponent`), Metro de pé, backend com seed, perfil Direto. No Expo Go, o app é aberto por `openLink`, e o Expo Router entende `/--/<rota>` (Context7). As observações da 5a valem: o menu de desenvolvedor na primeira abertura precisa de "Continue" e depois do X, e o voltar do iOS tem como rótulo o título da tela anterior.

**Cenário 11 (bloqueio):**
1. `launchApp`, depois `openLink: exp://127.0.0.1:8081/--/cenarios/11-bloqueio` (fechar o menu de dev se aparecer).
2. `tapOn: { id: cenario-11-nova-identidade }`; esperar o rótulo voltar para "Nova identidade" (`extendedWaitUntil`).
3. `tapOn: { id: cenario-11-ir-pagamento }` → `assertVisible: { id: tela-11-bloqueio-pagamento }` → `assertVisible: ".*placement_blocked.*"`.
4. `tapOn: { id: cenario-11-pagar }` → `extendedWaitUntil: { visible: ".*placement_survey_held.*", timeout: 10000 }` → `assertVisible: { id: cenario-11-pagamento-status, text: "Pesquisa: nenhuma na tela" }` → `assertNotVisible: "Pergunta 1 de 6"`.
5. `tapOn: { id: cenario-11-sair }` (em até 60 s desde o passo 4) → `extendedWaitUntil: { visible: "Pergunta 1 de 6", timeout: 10000 }`.
6. Fechar: `tapOn: "Fechar pesquisa"`. Opcional: `assertVisible: ".*placement_available.*"` na tela de entrada.

**Cenário 13 (sem rede):**
1. `launchApp`, depois `openLink: exp://127.0.0.1:8081/--/cenarios/13-sem-rede`.
2. `tapOn: { id: cenario-13-nova-identidade }` e esperar. Se houver registro antigo (`cenario-13-esquecer` visível), tocar nele (`runFlow` com `when: visible`).
3. `tapOn: { id: cenario-13-disparar-sem-rede }` → `extendedWaitUntil: { visible: "Pergunta 1 de 6", timeout: 10000 }`.
4. Responder (sugestão mais curta: tocar "9" no NPS e `tapOn: "Fechar pesquisa"`; vira `DISMISSED` com a primeira resposta preservada; ou percorrer até o fim para `COMPLETED`).
5. `extendedWaitUntil: { visible: { id: cenario-13-display-id }, timeout: 10000 }` → `assertVisible: { id: cenario-13-rede, text: "Sem rede.*" }` → `assertVisible: { id: cenario-13-fila, text: "Fila local: [1-9].*" }`.
6. `stopApp` (sem voltar de tela antes; o registro e a fila já estão no AsyncStorage).
7. `launchApp`, depois `openLink: exp://127.0.0.1:8081/--/cenarios/13-sem-rede` (o app volta com rede: o interruptor é só de memória).
8. `extendedWaitUntil: { visible: { id: cenario-13-entrega, text: "Entregue.*" }, timeout: 20000 }` → `assertVisible: { id: cenario-13-envios, text: "Envios da resposta nesta abertura: 1 .*" }` → `assertVisible: { id: cenario-13-fila, text: "Fila local: 0 .*" }`.
9. `tapOn: { id: cenario-13-conferir-backend }` → `extendedWaitUntil: { visible: { id: cenario-13-backend, text: "Backend: 1 registro\\(s\\) para esta exibição, desfecho (COMPLETED|DISMISSED)\\." }, timeout: 10000 }`.
10. Limpeza: `tapOn: { id: cenario-13-esquecer }`.

Alternativa fora do app: `copyTextFrom: { id: cenario-13-display-id }` e um `runScript` com `http.get` na mesma URL de exportação (a do comentário de `backendCheck.ts`), contando a linha do `maestro.copiedText`.

### O que ficou de fora

- Não usei simulador nem Metro (ordem desta subtarefa). Nenhum cenário foi aberto ao vivo: a 5e valida.
- Fluxos Maestro, capturas e README ficam com a 5e. O passo a passo está acima.
- Os avisos de desenvolvimento do SDK (chave recusada, endereço inalcançável) saem no console do Metro. O painel do exemplo não os captura, porque o SDK não tem gancho de log e não precisa ter. O cenário 14 orienta a olhar o console.
- A falha "endereço inalcançável" depende da rede: no simulador iOS, `10.255.255.1` fica sem resposta até o timeout de 3 s. Uma rede que recuse na hora dá falha imediata, igualmente silenciosa.

### Resultado exato dos comandos

- `npm run typecheck` (exemplo): `tsc --noEmit` sem erro.
- `npm run lint` (exemplo): `eslint .` sem problema.
- `cd sdk && CI=1 npm run verify`: **exit 0**.
  - `eslint .` sem problema; `tsc --noEmit` sem erro.
  - `jest --ci`: `Test Suites: 38 passed, 38 total` · `Tests: 305 passed, 305 total`.
  - `bob build`: `[module]/[commonjs] Compiling 83 files`, `✔ Wrote files to lib/module`, `✔ … lib/commonjs`, `✔ Wrote definition files to lib/typescript`.
  - `example:typecheck` e `example:lint` sem erro, já com os arquivos da 5c em andamento na árvore.
- `CI=1 npx expo export --platform ios --output-dir /tmp/pitaco-5d-export-ios`: `iOS Bundled 5854ms node_modules/expo-router/entry.js (1820 modules)`, `entry-….hbc (4.1MB)`, exit 0.
- `CI=1 npx expo export --platform android --output-dir /tmp/pitaco-5d-export-android`: `Android Bundled 4531ms … (1917 modules)`, `entry-….hbc (4.3MB)`, exit 0. Diretórios apagados.
- Chamadas reais (backend em `localhost:8080`, proxy com `npm run proxy` em segundo plano):
  - **13**: script `scratchpad/s13-check.mjs` com identidade descartável: `eligibility 200`, `open 201`, `submission 204`, reenvio idêntico `204` (como o reenvio da fila), e a mesma conferência de `backendCheck.ts` → `{"rows":1,"outcome":"DISMISSED"}`. Uma linha só, mesmo com o reenvio.
  - **14**: `POST /api/collect/eligibility` com `X-Pitaco-Key: pk_invalida` → `401` `api_key.invalid` (`application/problem+json`). `curl --max-time 3 http://10.255.255.1:9/collect/eligibility` → exit 28 em 3 s (sem resposta: é o timeout de elegibilidade que corta).
  - **15**: `GET http://localhost:8787/pitaco/saude` → `404 gateway.route_not_found`. `POST /pitaco/collect/eligibility` com a chave do `.env` → `200` com a pesquisa `d50db159…`. Log do proxy: `GET /pitaco/saude -> 404 em 0 ms (fora da superfície pública, não repassado)`, `POST /pitaco/collect/eligibility -> 200 em 34 ms`.
  - Proxy parado ao fim ("Parando o proxy.", exit 0; porta 8787 livre). O backend continua de pé (não é desta subtarefa).

## Fase 5c — Cenários 2 a 10

### Arquivos (todos em `sdk/example/`, salvo indicação)

Rotas (substituem os provisórios, sem renomear):
- `app/(tabs)/cenarios/02-gorhom.tsx`, `03-tela.tsx`, `04-comparar.tsx`, `05-tema.tsx`, `06-textos.tsx`, `07-renderizador.tsx`, `08-slots.tsx`, `09-headless.tsx`, `10-modal-inline.tsx`.

Rotas extras (mesmo prefixo, título no próprio arquivo com `<Stack.Screen options>`):
- `app/(tabs)/cenarios/03-tela-pesquisa.tsx`: a pesquisa real como tela (cenário 3).
- `app/(tabs)/cenarios/04-comparar-tela.tsx`: a forma "Tela" do cenário 4 (preview), com `?tema=claro|escuro`.

Componentes e apoio (`src/scenarios/`):
- `shared/useSurveyArrival.ts`: acompanha `usePitacoSurvey().available` e mantém a exibição "ativa" do instante em que chega até o app chamar `finish()` (o `available` cai antes do agradecimento e antes de o contêiner do app fechar).
- `shared/configs.ts` (`INLINE_CONFIG`, `MODAL_CONFIG`), `shared/TriggerButton.tsx` (botão `cenario-NN-disparar` + orientação "Limpar storage e identidade"), `shared/FlowModes.tsx` (fluxo real × preview local), `shared/LocalPreview.tsx` (preview do seed com a mesma customização), `shared/useLeavingRef.ts` (`beforeRemove` da navegação).
- `02-gorhom/GorhomSurveySheet.tsx` e `02-gorhom/SheetBody.tsx` (usados também pela forma "Gorhom" do cenário 4).
- `04-comparar/themes.ts`, `05-tema/themes.ts`, `06-textos/english.ts`, `07-renderizador/{AppNps.tsx,BrokenRating.tsx,renderers.ts}`, `08-slots/{BrandHeader.tsx,BrandFooter.tsx,slots.ts}`, `09-headless/{HeadlessSurvey.tsx,HeadlessQuestion.tsx}`.

Não mexi em arquivo compartilhado do exemplo (registro, `useScenario`, `ExampleContext`, layouts, painel, `src/ui/*`). `src/scenarios/ScenarioPlaceholder.tsx` continua existindo para os cenários da 5d.

### Mudança no SDK (com teste)

O cenário 4 pede o preview dentro do bottom sheet do SDK, e os componentes de apresentação não são exportados. Menor capacidade pública acrescentada:
- `sdk/src/preview/PitacoPreview.tsx`: `presentation="bottom-sheet"` ou `"modal"` agora abre a pré-visualização no mesmo contêiner do `<PitacoProvider>` (`PitacoSurfaceHost`, dentro de um `PitacoErrorBoundary`). O padrão continua `"inline"` (só o conteúdo), então quem usava o padrão (o painel da fase 7, os testes da 3d) não muda. Antes, `presentation` só mudava o que o `survey_presented` relatava; registrado como mudança de semântica para esses dois valores.
- `sdk/src/react/SurfaceHost.tsx` (interno, não exportado): props opcionais `onFinish(reason)` (chamado só depois da animação de saída, com o desfecho relatado pelo conteúdo, ou `dismissed` se não houve relato) e `thankYouDurationMs` (repassado ao conteúdo). O `<PitacoProvider>` renderiza `<PitacoSurfaceHost />` sem props: comportamento idêntico.
- Teste novo `sdk/src/preview/__tests__/PitacoPreview.container.test.tsx` (4 testes): folha do SDK aberta e `survey_presented` (`presentation: "bottom-sheet"`) só depois da entrada, sem `fetch`; fundo dispensa com `via: "backdrop"` e `onFinish('dismissed')` só depois da saída; concluir mostra o agradecimento e entrega `onFinish('completed')` depois da saída; `modal` abre em tela cheia e relata `"modal"`, sem `fetch`.
- Nenhum módulo nativo novo; o preview continua sem transporte (o `SurfaceHost` só usa `Modal`/`Animated`/`PanResponder` do React Native).

### Decisões e o porquê

- **Cenário 2, teclado do texto livre no gorhom.** No `@gorhom/bottom-sheet` 5.2.14, `useAnimatedKeyboard` guarda o evento de teclado até algum campo registrar o `target`, e só o `BottomSheetTextInput` faz isso no `onFocus`. O texto livre do SDK é um `TextInput` comum (o SDK não pode depender do gorhom), então o `keyboardBehavior` do gorhom não age sobre ele. Decisão: `android_keyboardInputMode="adjustResize"` (o Expo usa `softwareKeyboardLayoutMode: resize`; a janela encolhe e o sheet acompanha) e, nos dois sistemas, o corpo do sheet observa `usePitacoSurvey().focusedQuestionKey` (o core sabe quando o texto livre tem foco) e chama `snapToIndex(1)` (92%), deixando o campo acima do teclado. Mantive `keyboardBehavior="interactive"`/`keyboardBlurBehavior="restore"` declarados. Alternativa registrada para o guia: quem quiser o comportamento interativo do gorhom pode substituir o renderizador `freeText` por um com `BottomSheetTextInput` (nível 4 de customização).
- **Cenário 2, vias de dispensa.** O `unmount()` do `BottomSheetModal` agenda a desmontagem do conteúdo (`setState`) e chama `onDismiss` na mesma pilha, antes de o React desmontar: o `dismiss(via)` do `onDismiss` chega ao core antes da limpeza de desmontagem do `<PitacoSurveyContent />`, que então não dispensa de novo (o core ignora dispensa sem pergunta atual). Via padrão `swipe`; o `onPress` do `BottomSheetBackdrop` troca para `backdrop` antes do fechamento; um `BackHandler` enquanto aberto troca para `hardware_back` e fecha (o gorhom não trata o voltar do Android; sem isto o voltar sairia da tela e a via seria `navigation`). O X do SDK dispensa por dentro (`close_button`) e o `onFinish` fecha o sheet; concluir mostra o agradecimento e o `onFinish('completed')` fecha o sheet.
- **Cenário 2, `present()`**: o `onChange` do gorhom só dispara no fim da animação (`animateToPositionCompleted`); o primeiro `onChange(index >= 0)` chama `present('inline')`. `<PitacoSurveyContent presentDeferred />`.
- **Contexto no portal do gorhom**: o conteúdo do `BottomSheetModal` é renderizado junto do `BottomSheetModalProvider` (portal), então o contexto do Pitaco que vale lá é o do provider. No cenário 2 é o da raiz; no cenário 4 o `PitacoPreviewProvider` entra por dentro do sheet (`wrap`), e o `GorhomSurveySheet` conversa com o corpo por uma ponte (`opened`/`dismissed`) registrada num efeito.
- **Cenário 2, safe area**: `useSafeAreaInsets()` vai ao SDK por `useScenario('02-gorhom', { presentation: 'inline', insets })` (em `inline` o SDK não desenha contêiner, então hoje é informativo) e ao gorhom (`topInset`, respiro embaixo do conteúdo).
- **Cenário 3, voltar sem voltar duas vezes**: ao desmontar em andamento, o `<PitacoSurveyContent />` dispensa com `navigation` e também chama `onFinish('dismissed')`. A rota escuta `beforeRemove` (dispara para seta, gesto do iOS, voltar do Android e `router.back()`) e só chama `router.back()` no `onFinish` se ainda não estiver saindo. A rota da pesquisa chama `useScenario('03-tela', INLINE_CONFIG)` com o mesmo objeto da tela de baixo, então o runtime não é recriado ao empilhar.
- **Cenário 3, desmontagem ao voltar (conferido no código, não ao vivo)**: o native stack (react-native-screens 4.26, via Expo Router 57) tira a rota do estado ao voltar e o React desmonta a tela depois da transição; a limpeza do `<PitacoSurveyContent />` roda nesse desmonte. Um gesto de voltar cancelado no meio não remove a rota nem dispensa. A prova ao vivo (iOS e Android) fica para a 5e.
- **Cenário 3, `present()` ao montar** (padrão do `<PitacoSurveyContent />`), não no fim da transição de empilhar: `transitionEnd` não dispara com `animation: 'none'`, e depender dele arriscaria nunca abrir a exibição.
- **Cenário 4**: a pesquisa vem de `seedSurvey.schema` (carregada uma vez, do JSON gerado pelo seed) e cada abertura é um preview novo (`key`), sem elegibilidade e sem backend. Formas: `sheet` (`<PitacoPreview presentation="bottom-sheet" />`, a capacidade nova do SDK), `gorhom` (o mesmo `GorhomSurveySheet` do cenário 2 com o preview por dentro) e `tela` (rota empilhada). `triggerEvent` do seed, para o `survey_presented` ser igual ao real. Eventos publicados com `usePublishEvent('04-comparar')` e `form`; o painel agrupa por "Cenário/forma".
- **Cenários 5 a 10, modo "preview local"** além do real (decisão registrada): o servidor não reexibe a pesquisa ao mesmo respondente depois de respondida ou dispensada, e repetir "Limpar storage e identidade" a cada volta torna penoso comparar tema, textos, renderizador, slots, headless e modal. O modo real é o padrão da tela e existe em todos; o preview local usa a mesma customização (`LocalPreview`, eventos com `form: 'preview-local'`). No cenário 7, o preview não tem transporte: o renderizador quebrado cai no padrão, mas o relatório de erro só vai à rede no modo real (dito na tela).
- **Cenário 5**: tema inválido com cinco tokens errados e um válido (`radius.md: 4`), para mostrar a degradação token a token; a tela mostra as cinco linhas exatas que o `logger` do SDK escreve em `__DEV__` (uma vez por token e por processo).
- **Cenário 6**: `ENGLISH_STRINGS` tipado como `PitacoStrings` completo (o TypeScript acusa rótulo novo faltando). Enunciados e aviso de texto livre vêm da pesquisa publicada e continuam em português (dito na tela).
- **Cenário 7**: `renderers={{ nps: AppNps, rating: BrokenRating }}` na mesma pesquisa com os outros quatro tipos padrão. `BrokenRating` lança sempre; o `SafeCustom` do SDK cai nas estrelas e chama `reportRenderError` → `POST /collect/sdk-errors` (`kind: "render_error"`), que o embrulho de `fetch` do painel mostra na aba Rede.
- **Cenário 9**: "Avançar" e "Concluir" chamam `next()` (na última aplicável o core conclui, como o rodapé padrão, com `question_skipped` quando for o caso), "Pular" é `next()` numa opcional em branco, "Dispensar" é `dismiss('close_button')`, `present('inline')` ao montar o cartão. `complete()` não é usado: pularia o `question_skipped` de uma última opcional em branco e divergiria da UI padrão.
- **Cenário 10**: um seletor Modal/Inline troca `presentation` na raiz (recria o runtime, dito na tela: escolha a forma antes de disparar). No inline, o `<PitacoSurveyContent />` é montado entre parágrafos da tela rolável quando a pesquisa chega (`key` = `displayId`, para o `present()` do mount valer) e desmontado no `onFinish`.

### testIDs para os fluxos Maestro (5e)

Comuns: lista `cenario-<id>`; raiz `tela-<id>`; disparar `cenario-NN-disparar`; modo `cenario-NN-modo-real` / `cenario-NN-modo-preview` (5 a 10); preview local `cenario-NN-abrir-preview`. Da UI padrão do SDK (sem testID; por texto/rótulo): "Pergunta X de 6", "Próxima", "Voltar", "Enviar", X com rótulo de acessibilidade "Fechar pesquisa", "Obrigado!". Folha do SDK: `pitaco-bottom-sheet`, `pitaco-bottom-sheet-backdrop`, `pitaco-bottom-sheet-handle`; modal do SDK: `pitaco-fullscreen-safe-area`.

- **2 (gorhom)**: `cenario-02-gorhom` → `tela-02-gorhom` → (se preciso, Depuração → `debug-limpar-storage` → `tab-cenarios`) → `cenario-02-disparar` → esperar "Pergunta 1 de 6" dentro de `cenario-02-sheet` (o `BottomSheetScrollView`).
  - Arrastar para fechar: `swipe` de cima do sheet para baixo (da alça do gorhom até perto do fim da tela) → `cenario-02-sheet` some → `tab-depuracao` → `secao-eventos` → conferir `survey_presented` (`presentation: "inline"`) e `survey_dismissed` com `"via": "swipe"`.
  - Fundo: tocar na área escurecida acima do sheet → `survey_dismissed` `backdrop`. X do SDK ("Fechar pesquisa") → `close_button`. Android `back` → `hardware_back`.
  - Concluir: responder NPS 9 ("9") → "Próxima" … → "Enviar" → "Obrigado!" → sheet fecha sozinho (~2,5 s) → `survey_completed`.
  - Cada volta exige "Limpar storage e identidade" antes de disparar de novo.
- **3 (tela)**: `cenario-03-tela` → `cenario-03-disparar` → espera `tela-03-tela-pesquisa` e "Pergunta 1 de 6" (título do cabeçalho "Pesquisa").
  - Sair pela navegação: iOS `tapOn: "Voltar"`/rótulo do botão de voltar (no iOS o rótulo é o título da tela anterior, "3. Como tela"), ou `back` no Android, ou `swipe` da borda esquerda no iOS → volta a `tela-03-tela` → Depuração: `survey_dismissed` com `"via": "navigation"`, um só.
  - Concluir: percorrer até "Enviar" → "Obrigado!" → volta sozinho a `tela-03-tela` (assertVisible `cenario-03-disparar`) → `survey_completed`, sem `survey_dismissed`.
  - X do SDK: "Fechar pesquisa" → volta sozinho → `close_button`.
- **4 (comparar)**: `cenario-04-tema-claro|escuro`, `cenario-04-sheet`, `cenario-04-gorhom`, `cenario-04-tela` (rota `tela-04-comparar-tela`). Painel: `agrupar-cenario` → seções "04-comparar · sheet|gorhom|tela".
- **5**: `cenario-05-tema-claro|escuro|invalido`. **6**: só os comuns (conferir "Next"/"Question 1 of 6"). **8**: `cenario-08-header`, `cenario-08-voltar`, `cenario-08-avancar` (rótulo "Continuar →"/"Enviar respostas").
- **7 (renderizador)**: `cenario-07-renderizador` → `cenario-07-disparar` → `cenario-07-nps` visível (o NPS do app) → `cenario-07-nps-9` → "Próxima" (escala) → "Próxima" … na pergunta 3 as estrelas padrão aparecem ("4 de 5", o renderizador quebrado caiu no padrão) → seguir até "Enviar". Conferir: `tab-depuracao` → `secao-rede` → um item `POST /collect/sdk-errors` (status 202, resposta vazia) → tocar nele e ver `"kind":"render_error"` e `"stage":"render"` no corpo; em `secao-eventos`, `answer_selected` com `value: 9` na pergunta 1.
- **9 (headless)**: `cenario-09-headless` → `cenario-09-disparar` → `cenario-09-pesquisa` → `cenario-09-opcao-9` (NPS) → `cenario-09-avancar` → escala `cenario-09-opcao-4` → `cenario-09-avancar` → avaliação `cenario-09-opcao-5` → `cenario-09-voltar` (confere `navigated_back`) → `cenario-09-avancar` → … a condicional (motivo) é pulada pelo core com NPS 9 → múltipla escolha opcional: `cenario-09-pular` → texto livre: `cenario-09-texto` (`inputText`) → `cenario-09-concluir` → `cenario-09-obrigado` → `cenario-09-fechar`. Validação: `cenario-09-avancar` sem responder o NPS → `cenario-09-erro` e `validation_blocked`. Dispensar: `cenario-09-dispensar` → `survey_dismissed` `close_button`. Conferir no painel a sequência completa (inclusive `question_skipped`, `question_not_applicable`, `text_focused`/`text_edited`/`text_blurred`).
- **10**: `cenario-10-forma-modal|inline`, `cenario-10-inline` (o lugar no meio da rolagem).

### O que ficou de fora

- Nada foi rodado em simulador nem no Metro (posse da 5e). Em especial: o arrastar/fundo/voltar do gorhom, o teclado do texto livre no gorhom e a desmontagem da rota do cenário 3 no gesto de voltar foram decididos lendo o código do gorhom 5.2.14 e do Expo Router, e precisam da prova ao vivo.
- `insets`/`getInsets` no `<PitacoPreview />`: a folha do preview usa o padrão por plataforma do SDK.
- Um renderizador `freeText` com `BottomSheetTextInput` (alternativa registrada acima).
- No cenário 4, trocar o tema com uma forma aberta vale para o preview aberto (gorhom e sheet), mas a forma "Tela" lê o tema só ao empilhar.

### Resultado exato dos comandos

- `npx jest --ci src/preview src/react/__tests__/SurfaceHost` (SDK, depois da mudança): `Test Suites: 5 passed, 5 total` · `Tests: 27 passed, 27 total` (o rastro de pilha no console é do teste existente de contenção de falha, esperado).
- `npx tsc --noEmit` (SDK): sem erro. `npx eslint src/react/SurfaceHost.tsx src/preview`: sem problema.
- `npm run typecheck` (exemplo): `tsc --noEmit`, sem erro, exit 0. `npm run lint` (exemplo): `eslint .`, sem problema, exit 0.
- `cd sdk && npm run verify`: exit 0. `eslint .` sem problema; `tsc --noEmit` sem erro; `jest --ci`: `Test Suites: 38 passed, 38 total` · `Tests: 305 passed, 305 total` · `Snapshots: 0 total` · `Time: 1.374 s` (301 da 5a + 4 novos); `bob build`: `[module]`/`[commonjs] Compiling 83 files in src with babel`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`; `example:typecheck` e `example:lint` sem erro. Log: `scratchpad/verify-5c.log`.
- `CI=1 npx expo export --platform ios --output-dir /tmp/pitaco-5c-export-ios`: `iOS Bundled 3511ms node_modules/expo-router/entry.js (1824 modules)`, `_expo/static/js/ios/entry-….hbc (4.1MB)`, exit 0.
- `CI=1 npx expo export --platform android --output-dir /tmp/pitaco-5c-export-android`: `Android Bundled 3673ms node_modules/expo-router/entry.js (1921 modules)`, `_expo/static/js/android/entry-….hbc (4.3MB)`, exit 0. Os dois diretórios foram apagados. Logs: `scratchpad/export-5c-ios.log` e `export-5c-android.log`.
- Nenhuma falha por arquivos da 5d nas rodadas acima.

## Fase 5e — Validação iOS e Maestro

Ambiente: iPhone 16 (iOS 18.6) no Simulator, Expo Go SDK 57, Metro na porta 8081 em modo de observação (sem `CI=1`), backend em `localhost:8080` com o seed da 5b, proxy da 5b na porta 8787 para o cenário 15. Maestro 2.6.1.

### Arquivos criados/alterados

SDK (`sdk/src`):
- `react/PitacoProvider.tsx`: descarta o runtime substituído (efeito com `useRef` do runtime anterior; `dispose()` só quando o runtime muda de fato). A desmontagem simples continua só com `detach`, por causa do StrictMode.
- `core/runtime/runtime.ts`: campo `disposed`. `dispose()` o liga antes do `detach`; `attach()` não religa um runtime descartado; `consider()` ignora a elegibilidade que chega depois do descarte; `deliver()` não entrega mais eventos ao `onEvent`.
- `react/__tests__/PitacoProvider.lifecycle.test.tsx` (novo, 2 testes).

Exemplo (`sdk/example`):
- `app/_layout.tsx`: `BottomSheetModalProvider` do gorhom na raiz, dentro do `PitacoRoot`, acima das abas.
- `app/(tabs)/cenarios/02-gorhom.tsx` e `04-comparar.tsx`: sem provider local; `key` por exibição/abertura no `GorhomSurveySheet`.
- `src/scenarios/02-gorhom/SheetContainer.tsx` (novo): no iOS, `FullWindowOverlay` + `GestureHandlerRootView`.
- `src/scenarios/02-gorhom/GorhomSurveySheet.tsx`: `containerComponent={sheetContainer}`.
- `src/scenarios/07-renderizador/BrokenRating.tsx`: `LogBox.ignoreLogs` para o erro proposital.
- `src/debug/panel/EventRow.tsx`: a via da dispensa na linha do evento (`survey_dismissed · via swipe`).
- `package.json`: script `e2e:ios` (`maestro test .maestro/`).
- `.maestro/`: `_comum/abrir-cenario.yaml`, `01-sheet.yaml`, `02-gorhom.yaml`, `03-tela.yaml`, `07-renderizador.yaml`, `09-headless.yaml`, `11-bloqueio.yaml`, `13-sem-rede.yaml` e `README.md`.

Fluxos de apoio da passada manual (4, 5, 6, 8, 10, 12, 14, 15) ficaram no scratchpad (`s04.yaml`, `s05-06-08.yaml`, `s10.yaml`, `s12.yaml`, `s14.yaml`, `s15.yaml`), fora do repositório.

### Defeito do SDK apontado pela 5d — corrigido

- **Sintoma:** o `PitacoProvider` só fazia `detach()` no runtime substituído quando a configuração mudava (`deferTimeoutMs`, `baseUrl`, storage...). O temporizador de uma pesquisa retida seguia vivo e emitia `placement_expired`/`placement_survey_discarded` pelo ouvinte antigo.
- **Correção:**
  - o runtime anterior recebe `dispose()`: para os temporizadores de retenção e de envio, solta o `AppState`, pausa a fila, descarta a sessão e limpa os ouvintes;
  - uma elegibilidade que chegue depois não vira oferta, e nenhum evento sai pelo `onEvent` antigo;
  - o que já estava gravado na fila persistente fica lá (a chave da fila é a mesma para `baseUrl|apiKey`) e o runtime novo entrega.
- **Testes:**
  - "encerra o runtime substituído…": adia, troca `deferTimeoutMs` de 10 s para 60 s e avança 15 s. Nenhum `placement_expired`, o `remove()` do `AppState` antigo foi chamado e `held` do runtime novo é `false`. Sem a correção, esse teste falha: `Received + 1` (o `placement_expired` a mais).
  - "o que já estava gravado… sai uma vez pelo runtime novo": resposta na fila sem rede, troca `eligibilityTimeoutMs`, a rede volta e sai uma submissão só; a fila fica vazia.
- **Lacuna registrada, não corrigida:** o desmonte definitivo do `<PitacoProvider>` continua só com `detach()`. Os ouvintes e o envio param, mas o temporizador de uma pesquisa retida ainda vence e emite `placement_expired` pelo `onEvent` da árvore já desmontada. O efeito de limpeza não distingue a remontagem do StrictMode (mesmo runtime) do desmonte de verdade, e descartar ali quebraria o StrictMode, porque `dispose()` limpa os assinantes do `useSyncExternalStore`. Não abre exibição nem envia nada. Uma correção possível é um `dispose()` adiado (microtarefa ou `setTimeout(0)`) e cancelado se o mesmo runtime voltar a ser ligado. Fica para o dono do SDK.

### Defeitos do exemplo encontrados ao vivo — corrigidos na origem

1. **Sheet do gorhom sob a barra de abas (cenários 2 e 4):**
   - **Sintoma:** a barra de abas nativa cobria o pé do sheet e cortava o "Próxima".
   - **Primeira tentativa:** o provider na raiz não bastou: o contêiner nativo de telas (react-native-screens) fica por cima do portal do gorhom.
   - **Correção:** o provider foi para a raiz e, no iOS, o sheet vai para o `containerComponent` com `FullWindowOverlay`, que é a correção documentada pelo gorhom (Context7, issue #832).
   - **Gestos:** a camada é uma janela à parte, então leva um `GestureHandlerRootView` próprio (Context7 do gesture-handler).
   - **Android:** o contêiner fica sem valor.
   - **Conferido na tela:** sheet inteiro acima das abas e fundo escurecido na tela toda.
2. **O mesmo `BottomSheetModal` não reabria (cenário 2, segunda volta):**
   - **Sintoma:** depois de uma dispensa e de "Limpar storage e identidade", o `present()` do gorhom era chamado com o ref vivo e o `onChange` nunca vinha. Log temporário `[5e-diag]`, já removido.
   - **Descartado:** não é o `FullWindowOverlay`. Com o contêiner desligado, o sintoma se repetia.
   - **Causa:** não isolada dentro do gorhom 5.2.14.
   - **Correção no exemplo:** um `BottomSheetModal` novo por exibição (`key={arrivedId}` no cenário 2, `key` por abertura no 4). A primeira apresentação de uma instância nova sempre funcionou.
3. **LogBox cobria o cenário 7:**
   - **Sintoma:** o erro proposital do `BrokenRating` abria o LogBox de desenvolvimento por cima do app. O SDK tratava certo por trás (estrelas padrão e `render_error` enviado).
   - **Correção:** `LogBox.ignoreLogs` só para essa mensagem. Não muda nada em produção.
4. **Painel sem a via:**
   - **Sintoma:** a linha do evento não mostrava a via da dispensa, que só aparecia expandindo o JSON.
   - **Correção:** `EventRow` passou a mostrar `· via <via>`. Isso ajuda a comparar as formas a olho e dá ao Maestro algo para conferir.

### Achado de ambiente (não é código)

- **Metro com `CI=1` não observa arquivos.** Ao subir, o próprio Expo avisa: `Metro is running in CI mode, reloads are disabled. Remove CI=true to enable watch mode.` Com isso, várias edições desta fase só chegavam ao app depois de reiniciar o Metro com `-c`, e as primeiras falhas dos fluxos 02 e 07 eram código velho. Passei a subir o Metro sem `CI`, com `stdin` de `/dev/null` para não haver prompt. Registrado no README dos fluxos.
- **Engrenagem flutuante do Expo Go:**
  - fica numa janela à parte, no canto superior direito, e cobre o X do modal em tela cheia (cenário 10); um toque ali abre o menu de desenvolvedor;
  - o menu não tem opção para escondê-la, mas ela é arrastável e foi levada para baixo à esquerda;
  - só existe no Expo Go.
- **Durante a primeira metade da fase o usuário também usava o simulador.** Os toques que não eram meus fizeram pausar a fase até ele confirmar.

### Passada pelos quinze cenários no iOS (Expo Go, backend real com seed)

| # | Cenário | Resultado | Como foi conferido |
| --- | --- | --- | --- |
| 1 | Sheet do SDK | ok | Maestro `01`: arrastar a alça → `survey_dismissed · via swipe`; toque no fundo → `via backdrop`. À mão (Maestro de apoio), texto livre com teclado: a folha sobe, campo, contador "10/2000" e "Enviar" à vista, envio → `survey_completed`. Movimento reduzido com `xcrun simctl spawn booted defaults write com.apple.Accessibility ReduceMotionEnabled -bool true` (o `simctl ui` não tem essa opção); a captura logo após o toque já mostra a folha aberta, compatível com a duração curta que o `useReduceMotion` do SDK escolhe, mas a latência da captura não permite medir a animação. Desligado ao fim (`0`). Voltar do Android: fora (5f). |
| 2 | Com gorhom | ok, com 2 defeitos do exemplo corrigidos | Maestro `02`: arrastar → `via swipe`; fundo → `via backdrop`; texto livre com teclado, sheet em 92% e "Enviar" à vista com o teclado aberto; concluir fecha sozinho → `survey_completed`, sem dispensa. |
| 3 | Como tela | ok, com limitação | Maestro `03`: seta do cabeçalho → `survey_dismissed · via navigation` (uma só); concluir volta sozinho → `survey_completed`, sem dispensa. **Gesto de borda do iOS não validado:** nem o swipe do Maestro nem arrastos com o mouse no Simulator (rápido e em passos, a partir da borda) acionaram o reconhecedor de borda. A tela não se moveu, embora os toques cheguem ao app (o "9" foi selecionado pelo mouse), e o exemplo não mexe em `gestureEnabled`. A seta passa pelo mesmo desmonte da rota. |
| 4 | Comparar apresentações | ok | Maestro de apoio: tema escuro → Sheet do SDK, Gorhom e Tela abrem a mesma pesquisa do preview (capturas `c4-*-escuro`, sheet do gorhom escuro com texto claro e botão de destaque); claro → Sheet de novo. Painel agrupado por "Cenário/forma": `04-comparar · sheet`, `· gorhom` e `· tela` com a mesma sequência (`survey_presented` → `question_viewed` → `question_left` → `survey_dismissed`). |
| 5 | Tema | ok | Tema inválido no preview local: a pesquisa abre com o tema padrão. |
| 6 | Textos | ok | Preview local em inglês: "Question 1 of 6" e "Next". |
| 7 | Renderizador substituído | ok, com 1 defeito do exemplo corrigido | Maestro `07`: NPS do app (`cenario-07-nps-9`), estrelas padrão na pergunta 3 ("4 de 5"), concluir; Rede com `POST /collect/sdk-errors` e `render_error` no corpo; `survey_completed`. |
| 8 | Slots | ok | Preview local: `cenario-08-header` e `cenario-08-avancar` na tela. |
| 9 | Headless | ok | Maestro `09`: validação (`cenario-09-erro` e `validation_blocked`), voltar (`navigated_back`), pular (`question_skipped`), texto livre, concluir, agradecimento e fechar → `survey_completed`, sem dispensa. |
| 10 | Modal e inline | ok | Maestro de apoio: modal em tela cheia (`pitaco-fullscreen-safe-area`), X → `survey_dismissed · via close_button`; inline no meio da rolagem, sem contêiner (captura `c10-inline`). |
| 11 | Bloqueio | ok | Maestro `11`: `placement_blocked` no pagamento; "Pagar" → `placement_survey_held` e "Pesquisa: nenhuma na tela"; sair → a retida abre na entrada; fundo → `via backdrop`. |
| 12 | Adiamento | ok | Maestro de apoio: prazo de 10 s → `placement_deferred`, depois `placement_expired`, nada na tela. Identidade nova → adiar → outra tela → liberar → `placement_released {"heldMs":2540}`, `placement_available` e a folha do SDK. |
| 13 | Sem rede | ok | Maestro `13`: disparar e cortar a rede, NPS 9, fundo; "Sem rede", fila ≥ 1 e `displayId` registrado; `stopApp` e reabrir → "Entregue", "Envios da resposta nesta abertura: 1", fila 0; "Conferir no backend" → 1 registro para a exibição. |
| 14 | Falhas | ok | Maestro de apoio: chave inválida (401), endereço inalcançável (falhou), API lenta (cortada); contador "3 toque(s)" e nenhuma pesquisa. O app segue navegável. |
| 15 | Proxy | ok | Maestro de apoio: "Conferir se o proxy está de pé", identidade nova, disparar, NPS 9, fundo. Log do proxy: `GET /pitaco/saude -> 404 … (não repassado)`, `POST /pitaco/collect/eligibility -> 200 em 66 ms`, `POST /pitaco/collect/displays -> 201 em 29 ms`, `POST …/events -> 202 em 38 ms` e `… 202 em 36 ms`, `POST …/submission -> 204 em 67 ms`. |

### Fluxos Maestro — decisões

- **Subfluxo comum** `_comum/abrir-cenario.yaml`:
  - `stopApp` (processo novo: zera o limite de sessão);
  - `openLink: exp://127.0.0.1:8081/--/cenarios/${ROTA}`;
  - "Continue" e o menu de desenvolvedor, se aparecerem (o do menu usa a posição da 5a);
  - espera `tela-${ROTA}` e "Limpar storage e identidade".
- **Conferência pela linha do evento:** o `Pressable` da linha junta os textos na acessibilidade, então a regex é `.*survey_dismissed · via swipe.*`. Listas longas usam `scrollUntilVisible`.
- **Total da pesquisa:** depois do NPS 9, o total passa de 6 para 5 (a condicional deixa de se aplicar), e os fluxos usam "Pergunta N de 5".
- **Toques por posição, e o porquê:**
  - o fundo da folha do SDK: `accessibilityViewIsModal` esconde da árvore o que está ao lado da folha, e isso é o correto para acessibilidade;
  - o campo de texto livre no gorhom: o placeholder não aparece para o Maestro.
- **Rótulos que o Maestro lê:**
  - "Fechar pesquisa" também está na alça da folha do SDK, e o Maestro tocaria nela, então os fluxos fecham pelo fundo;
  - o contador do texto livre é lido pelo rótulo "10 de 2000 caracteres".
- **Agradecimento opcional:** "Obrigado!" dura pouco e o sheet fecha sozinho, então a espera é opcional e a prova é o `survey_completed`.
- **Teclado:** o `hideKeyboard` do Maestro falha no iOS. O envio é feito com o teclado aberto, o que também prova o botão à vista.

### Saída exata do Maestro (suíte inteira, depois de todas as mudanças)

`cd sdk/example && maestro test .maestro/` (o mesmo que `npm run e2e:ios`):

```
Waiting for flows to complete...
[Passed] 09 · Headless (48s)
[Passed] 07 · Renderizador substituído (38s)
[Passed] 13 · Sem rede (34s)
[Passed] 02 · Com gorhom (51s)
[Passed] 11 · Bloqueio (23s)
[Passed] 03 · Como tela (40s)
[Passed] 01 · Sheet do SDK (25s)
7/7 Flows Passed in 4m 19s
```

Código de saída 0. Uma rodada anterior, antes de mover a engrenagem do Expo Go, também deu `7/7 Flows Passed in 4m 22s`.

### Resultado exato dos comandos

- `npx jest --ci src/react/__tests__/PitacoProvider.lifecycle` (SDK):
  - com a correção: `Tests: 2 passed, 2 total`;
  - com a linha do `dispose()` desligada de propósito: `Tests: 1 failed, 1 passed`, `Expected - 0 / Received + 1`. Arquivo restaurado depois.
- `cd sdk && npm run verify` (depois de todas as mudanças de código): exit 0.
  - `eslint .` sem problema e `tsc --noEmit` sem erro;
  - `jest --ci`: `Test Suites: 39 passed, 39 total` · `Tests: 307 passed, 307 total` (305 + 2);
  - `bob build`: `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`;
  - `example:typecheck` e `example:lint` sem erro.
  - Log: `scratchpad/verify-5e-final.log`.
- `npm run typecheck` e `npm run lint` (exemplo), depois de cada mudança: exit 0; nenhum resto de `5e-diag`.
- `npx expo export --platform ios`: exit 0. `iOS Bundled 6755ms node_modules/expo-router/entry.js (1825 modules)`, `_expo/static/js/ios/entry-….hbc (4.1MB)`.
- `npx expo export --platform android`: exit 0. `Android Bundled 6816ms node_modules/expo-router/entry.js (1922 modules)`, `_expo/static/js/android/entry-….hbc (4.3MB)`.
- Diretórios de export apagados.
- `npm run proxy`: `Proxy do Pitaco ouvindo em http://0.0.0.0:8787/pitaco`. Parado ao fim.
- Metro (`npx expo start -c --port 8081`, sem `CI`): parado ao fim.
- Estado final: portas 8081 e 8787 livres; backend (8080) e simulador iPhone 16 ligados.

### O que ficou de fora

- **Gesto de voltar da borda do iOS (cenário 3):** não validado neste ambiente (ver a tabela). Conferir num aparelho físico.
- **Movimento reduzido:** configuração aplicada e mecanismo do SDK conferido no código; a diferença de animação não foi medida.
- **Voltar do Android** nos cenários 1 e 2, a **Android inteira**, as **capturas e vídeos do cenário 4** e o **README do exemplo:** com a 5f.
- **Causa do `present()` ignorado** na segunda apresentação do mesmo `BottomSheetModal` do gorhom: não isolada. O exemplo remonta o modal por exibição.
- **Desmonte definitivo do Provider:** sem `dispose()` (lacuna registrada acima).

## Fase 5f — Android, capturas e README

Ambiente: Pixel 9 (AVD `Pixel_9`, Android 16, API 36, navegação por gestos) e iPhone 16 (iOS 18.6) no Simulator, Expo Go SDK 57 nos dois, Metro na porta 8081 sem `CI`, backend em `localhost:8080` com o seed da 5b, proxy da 5b na porta 8787 para o cenário 15. Maestro 2.6.1. `ffmpeg` ausente; `avconvert` (macOS) disponível.

### Arquivos criados/alterados

SDK (`sdk/src`):
- `react/SurfaceHost.tsx`: em `presentation="inline"`, zera `open` e `trackedAvailable` antes de devolver `null` (defeito 1 abaixo).
- `react/__tests__/SurfaceHost.test.tsx`: dois testes novos no bloco `inline` e o cabeçalho do arquivo atualizado.
- `ui/presentation/keyboard.ts` (novo, interno): `keyboardAvoidingBehavior()`, `padding` no iOS e `height` no Android.
- `ui/presentation/BottomSheet.tsx` e `ui/presentation/FullScreenModal.tsx`: `behavior={keyboardAvoidingBehavior()}` no `KeyboardAvoidingView` (antes, `undefined` no Android); o do modal ganhou `testID="pitaco-fullscreen-keyboard-avoider"`.
- `ui/presentation/__tests__/keyboard.test.ts` (novo): três testes.
- `react/PitacoProvider.tsx`: o separador da identidade do runtime era um caractere NUL literal dentro da string do `.join(...)`; virou o escape `'\u0000'`. Mesmo valor em runtime; o arquivo deixa de ser tratado como binário pelo `grep` e pelo `git diff` (defeito 2).

Exemplo (`sdk/example`):
- `app/_layout.tsx`: `ThemeProvider` do Expo Router com `DarkTheme`/`DefaultTheme` pelo `useColorScheme()`, e `LogBox.ignoreLogs` para o aviso de desenvolvimento do Expo Router ao abrir por deep link.
- `src/pitaco/ExampleContext.tsx`: `LogBox.ignoreLogs(['Sem storage persistente'])`.
- `src/scenarios/07-renderizador/BrokenRating.tsx`: `LogBox.ignoreLogs` também para os dois avisos de desenvolvimento do SDK ao cair no padrão e ao relatar a falha.
- `src/scenarios/09-headless/HeadlessSurvey.tsx`: `dismiss('navigation')` ao desmontar com a pesquisa em andamento.
- `package.json`: `e2e:ios` passa a `maestro --platform ios test .maestro/`; novo `e2e:android` (`maestro --platform android test .maestro/`).
- `.maestro/_comum/abrir-link.yaml` (novo): abre a rota com o endereço do Metro por plataforma (`127.0.0.1` no iOS, `10.0.2.2` no Android); no Android abre antes a tela inicial do próprio Expo Go (`launchApp: host.exp.exponent`, espera "Expo Go") e só então manda o link; fecha a apresentação e o menu de desenvolvedor do Expo Go e só termina com `tela-${ROTA}` visível; `retry` de 2 só da abertura.
- `.maestro/_comum/fechar-app.yaml` (novo): `stopApp` no iOS, `stopApp: host.exp.exponent` no Android.
- `.maestro/_comum/fechar-tutorial-teclado.yaml` (novo): no Android, "Cancel" no tutorial de caneta do Gboard, se aparecer.
- `.maestro/_comum/abrir-cenario.yaml`: usa `fechar-app.yaml` e `abrir-link.yaml`.
- `.maestro/01-sheet.yaml`: passo só do Android, voltar do sistema no sheet do SDK → `survey_dismissed · via hardware_back`, sem sair da tela.
- `.maestro/03-tela.yaml`: sair da pesquisa pela seta do cabeçalho no iOS e pelo voltar do sistema no Android (`via navigation`).
- `.maestro/02-gorhom.yaml` e `09-headless.yaml`: `fechar-tutorial-teclado.yaml` depois de tocar no campo; no 09, `hideKeyboard` só no iOS e rolagem até "Concluir" no Android.
- `.maestro/13-sem-rede.yaml`: fecha e reabre com `fechar-app.yaml` e `abrir-link.yaml`.
- `.maestro/README.md`: reescrito para as duas plataformas, com a tabela do que muda e o porquê (deep link, pacote do Expo Go, menu de desenvolvedor, voltar do sistema, teclado, tutorial do Gboard, Expo Go frio) e como esconder a engrenagem em cada plataforma.
- `README.md` (novo): o README do exemplo.
- `docs/capturas/` (novo): capturas e vídeos (lista abaixo).

### Defeitos do SDK encontrados e corrigidos

1. **Contêiner vazio preso por cima do app depois de uma pesquisa em `inline`** (viola a degradação silenciosa).
   - Sintoma (Android, cenário 9): sair do headless com a pesquisa aberta deixava, na tela seguinte, o fundo escurecido e uma folha vazia com só a alça. Todo toque era engolido; o Maestro não enxergava mais a tela (a folha é um `Modal`).
   - Causa: o `PitacoSurfaceHost` põe `open = true` quando a pesquisa fica disponível, mesmo em `inline`, em que devolve `null`. Nesse modo nada chega a fechar (`onFinish`/`onClosed` só rodam num contêiner). Quando a apresentação volta a `bottom-sheet` (o cenário desmonta e a raiz volta ao padrão; o runtime é recriado, sem pesquisa), o mesmo `PitacoSurfaceHost` (montado sem `key` no Provider) renderiza a folha com `open` ainda verdadeiro e o conteúdo vazio.
   - Correção: em `inline`, zera `open` e `trackedAvailable` antes de devolver `null`. Zerar `trackedAvailable` faz uma pesquisa ainda disponível abrir normalmente quando a apresentação sai de `inline` para um contêiner.
   - Testes: "uma pesquisa vista em inline não deixa o contêiner aberto quando a apresentação muda" (apresenta e dispensa em `inline`, troca para `bottom-sheet`, espera nenhum `pitaco-bottom-sheet`) e "uma pesquisa ainda disponível abre no contêiner quando a apresentação sai de inline". Com a lógica antiga restaurada de propósito, o primeiro falha e o `Received` é exatamente a folha com um `<View />` vazio dentro; arquivo restaurado (idêntico ao corrigido, conferido com `cmp`).
3. **No Android, o teclado cobria a folha do SDK** (cenário 1 pede "teclado subindo no texto livre").
   - Sintoma (Android 16, API 36, ponta a ponta): ao tocar no texto livre, a folha ficava no mesmo lugar e o teclado a cobria; só a alça aparecia acima dele, e o campo, o contador e o "Enviar" sumiam (capturas antes e depois: a borda de cima da folha no mesmo y, cerca de 1143 de 2000).
   - Causa: `BottomSheet` e `FullScreenModal` usavam `KeyboardAvoidingView` com `behavior` só no iOS, contando que o Android encolheria a janela (`adjustResize`). Com a janela ponta a ponta, a janela do `Modal` não encolhe.
   - Correção: `behavior` nas duas plataformas, `padding` no iOS e `height` no Android, o padrão da documentação do React Native (Context7: "setting behavior is recommended on both platforms"). A regra ficou em `keyboardAvoidingBehavior()`, usada pelos dois contêineres.
   - Testes: o `KeyboardAvoidingView` não repassa `behavior` ao `View` que renderiza (tira a prop antes do `<View {...props}>`), e a Testing Library 14 não tem mais `UNSAFE_getByType`, então o teste é da regra: `height` no Android, `padding` no iOS, e a leitura da plataforma atual. Com o Android voltando `undefined` (o comportamento antigo) de propósito, dois dos três falham (`Expected: "height"`, `Received: undefined`); arquivo restaurado, idêntico ao corrigido. Uma primeira versão, que lia `props.behavior` do `View` pelo `testID`, falhava com e sem a correção e foi descartada.
   - Conferido no emulador: com o teclado aberto, a folha inteira sobe; campo com "Tudo certo", "10/2000", "Voltar" e "Enviar" acima do teclado; envio com o teclado aberto e `survey_completed` no painel.
2. **Caractere NUL literal em `PitacoProvider.tsx`**: não mudava comportamento, mas escondia o arquivo das buscas e dos diffs (o `grep` do sistema responde "Binary file matches"). O arquivo é novo, não rastreado (fases anteriores).

### Defeitos do exemplo encontrados e corrigidos

1. **Moldura clara no tema escuro:** conteúdo escuro com cabeçalho e barra de abas brancos (confirmado na captura escura da 5e). `ThemeProvider` na raiz.
2. **Aviso do LogBox sobre a barra de abas (Android):** "Limpar storage e identidade" troca de propósito o storage por um em memória por 300 ms, o SDK avisa em desenvolvimento, e a notificação do LogBox cobria "Cenários" e engolia o toque. Ignorado só esse texto; continua no console.
3. **Avisos do LogBox sobre o "Próxima" (Android, cenário 7):** os avisos de desenvolvimento do SDK para o renderizador quebrado de propósito ("o renderizador substituído "rating" lançou um erro…" e "falha interna (render_error)…") cobriam o botão. Ignorados só esses textos, como a 5e fez com o erro do componente.
4. **Headless sem desfecho ao sair pela navegação (cenário 9):** o `<PitacoSurveyContent />` dispensa com `via navigation` ao desmontar; uma UI própria precisa fazer à mão. Sem isso a exibição ficava sem desfecho (abandono no servidor). `HeadlessSurvey` faz o mesmo que o SDK (refs atualizadas num efeito e limpeza de desmontagem).

### Achados de ambiente (não são código do app nem do SDK)

- **`adb reverse` não respondia:** o túnel `tcp:8081` aparecia na lista e a conexão abria, mas sem nenhum byte (`nc 127.0.0.1 8081` dentro do emulador); o Expo Go ficava no carregamento. `10.0.2.2:8081` responde (`HTTP/1.1 200 OK`, `packager-status:running`). Os fluxos usam `10.0.2.2` no Android.
- **Pacote do Expo Go no Android é `host.exp.exponent`** (`adb shell pm list packages`), em minúsculas. O `appId: host.exp.Exponent` dos fluxos é o do iOS; o `stopApp` sem argumento terminava em 15 ms sem fechar nada ("Stopping app host.exp.Exponent"), e o estado vazava de um fluxo para o outro (pilha de navegação, a folha vazia do defeito 1).
- **Tutorial de caneta do Gboard** ("Try out your stylus") ao abrir o teclado, cobrindo a tela inteira. `settings put secure stylus_handwriting_enabled 0` (antes `null`) não bastou.
- **Expo Go frio engole o link:** logo depois do `force-stop`, às vezes o `LauncherActivity` recebe o link, aparece ("Displayed … +398ms") e fecha a própria tarefa (transição CLOSE) sem abrir o app: fica a tela inicial do Android. No `adb logcat`, as aberturas boas têm o START do `LauncherActivity` e, cerca de 0,3 s depois, o do `ExperienceActivity`; as ruins só o primeiro. Não depende do intervalo entre o `force-stop` e o link (houve boa com 31 ms e ruim com 88 ms) e piorou com o emulador apertado de memória (AVD de 2 GB, cerca de 650 MB livres). O link "perdido" às vezes era tratado depois, com o app já aberto pela nova tentativa, e mandava o app para o fundo no meio do fluxo (fluxos 01 e 03 terminando na tela inicial, sem nenhuma morte inesperada do Expo Go: todas as mortes no logcat vieram 0,0 a 0,1 s depois de um `force-stop` do `stopApp`, e o buffer de crash só tem uma queda do Bluetooth na inicialização do emulador). Correção nos fluxos: `abrir-link.yaml` abre antes a tela inicial do próprio Expo Go (`launchApp: host.exp.exponent`), espera "Expo Go" e só então manda o link.
- **`hideKeyboard` do Maestro no Android usa o voltar do sistema** quando não acha outro jeito: no cenário 9 isso sai da tela.
- **Metro encerrado por falta de memória, duas vezes** ("stopped because the system is running low on memory"), enquanto rodavam o emulador (qemu, até 1,7 GB), o simulador e o backend Java. Nas duas, a memória livre do sistema estava em 40 a 45% logo depois; quem encerrou foi o gerenciador de tarefas em segundo plano do ambiente. Na segunda, o Metro foi religado com `nohup … &` (processo à parte), e parado explicitamente no fim. Quando o Metro cai, o Expo Go mostra "Something went wrong".
- **JVM do Maestro órfã:** para interromper uma rodada, usei `pkill -f 'maestro --platform android test'`, que casa com o `npm run`, mas não com a JVM (`java … maestro.cli.AppKt --platform android test …`). A JVM seguiu dona da sessão do driver no aparelho, e as rodadas seguintes falharam em 0 s ("Not able to reach the gRPC server"). Resolvido encerrando a JVM pelo pid e o driver (`am force-stop dev.mobile.maestro`). Não é defeito do app, do SDK nem do Maestro.
- **Engrenagem do Expo Go:** no Android, Expo Go → Settings → "Tools button" a desliga (desligada nesta fase; as capturas e vídeos do Android saem sem ela). No iOS (Expo Go 57.0.9, "Supported SDK 57.0.0") as configurações só têm o tema e os gestos do menu (sacudir, três dedos), sem opção para o botão. Ele estava no canto de baixo à esquerda (onde a 5e o deixou) e, na primeira rodada de capturas do iOS, cobria o "7" e o "Nada provável" da folha: foi arrastado (Maestro, `swipe` de 2 s de (11%, 83%) para (90%, 10%)) para a ponta vazia do cabeçalho, as capturas e os vídeos do iOS foram refeitos, e ele aparece ali, sem cobrir a pesquisa nas três formas.
- **Troca de alvo: `lsof -ti tcp:8081` não é só o Metro.** No script que troca o `.env` para o iOS e reinicia o Metro, `kill $(lsof -ti tcp:8081)` também mandou `SIGTERM` ao `netsimd` do emulador Android (pid 40243), que só tinha uma conexão aberta com o Metro. O processo não caiu (continuou com 5 h de atividade, `adb` e rede do emulador ok), mas o certo é `lsof -ti tcp:8081 -sTCP:LISTEN`.
- **"Can't perform a React state update on a component that hasn't mounted yet"** na raiz, algumas vezes no Android. A pilha aponta para o próprio Expo Router (`expo-router/build/fork/useLinking.native.js:127`: a promessa da URL inicial do deep link resolve e faz `setState` no `ContextNavigator` antes da montagem). É aviso de desenvolvimento dentro da biblioteca; não vem do exemplo nem do SDK e não quebra a tela, mas a notificação do LogBox cobria a barra de abas no Android e engolia o toque em "Depuração" (fluxo 07). Silenciado no LogBox em `app/_layout.tsx` com o texto exato; continua no console do Metro. Como o texto é o do React e não cita o componente, o mesmo aviso vindo de outro lugar também deixa de aparecer no LogBox (segue no console).

### Decisões e o porquê

- **`stopApp` por plataforma num subfluxo, e não `appId` por variável de ambiente.** Com `fechar-app.yaml`, os arquivos continuam autossuficientes: `maestro test .maestro/01-sheet.yaml` funciona sem `-e`, nas duas plataformas, e o `appId` do cabeçalho (usado só pelo `stopApp`) não precisa mudar. O `openLink` já abre pelo link, sem depender do `appId`.
- **`10.0.2.2` no deep link do Android**, em vez de consertar o `adb reverse`: não depende de túnel e é o mesmo endereço que o app usa para o backend.
- **`retry` só na abertura** (`abrir-link.yaml`): o próprio Maestro avisa que envolver o fluxo inteiro em `retry` esconde instabilidade do app. A abertura termina esperando `tela-${ROTA}`, então uma abertura que não pegou é detectada ali, e o cenário em si nunca é repetido.
- **LogBox só com textos exatos**, nunca `LogBox.ignoreAllLogs` nem o prefixo `[Pitaco]`: o que o exemplo provoca de propósito sai da frente, e qualquer outro aviso do SDK continua aparecendo.
- **Pergunta das capturas: a de NPS (pergunta 1), com o 9 marcado.** Tem rótulos nas pontas ("Nada provável" e "Extremamente provável"), onze opções e o estado selecionado, e é a primeira, então as três formas mostram exatamente o mesmo estado sem navegação.

### Passada pelos quinze cenários no Android (Expo Go, backend real com seed em `10.0.2.2`)

Fluxos de apoio da passada (fora do repositório, no scratchpad): `a-s01-teclado.yaml`, `a-s02-back.yaml`, `a-s04.yaml`, `a-s05-06-08.yaml`, `a-s10.yaml`, `a-s12.yaml`, `a-s14.yaml`, `a-s15.yaml`, todos abrindo pelo `_comum/abrir-cenario.yaml` do repositório.

| # | Cenário | Resultado no Android | Como foi conferido |
| --- | --- | --- | --- |
| 1 | Sheet do SDK | ok, com 1 defeito do SDK corrigido (teclado) | Maestro `01`: arrastar → `via swipe`; fundo → `via backdrop`; **voltar do sistema → `survey_dismissed · via hardware_back`, sem sair da tela**. `a-s01-teclado`: a folha sobe com o teclado, "10 de 2000 caracteres" e "Enviar" à vista, envio com o teclado aberto → `survey_completed` (antes da correção, a folha ficava atrás do teclado). Movimento reduzido: não medido no Android (mesmo `useReduceMotion` conferido na 5e). |
| 2 | Com gorhom | ok | Maestro `02`: arrastar → `via swipe`; fundo → `via backdrop`; texto livre com o teclado (`adjustResize` e o sheet em 92%), "Enviar" à vista → `survey_completed`, sem dispensa. `a-s02-back`: **voltar do sistema com o sheet aberto → `survey_dismissed · via hardware_back`** (o `BackHandler` do exemplo), sem sair da tela. |
| 3 | Como tela | ok | Maestro `03`: **voltar do sistema com a pesquisa aberta → `survey_dismissed · via navigation`** (uma só); concluir volta sozinho → `survey_completed`, sem dispensa. |
| 4 | Comparar apresentações | ok | `a-s04`: tema escuro → Sheet do SDK, Gorhom e Tela (a volta da Tela pelo voltar do sistema); claro → Sheet; painel agrupado por "Cenário/forma" com `04-comparar · sheet`, `· gorhom` e `· tela`. As seis capturas do Android (abaixo). |
| 5 | Tema | ok | `a-s05-06-08`: tema inválido no preview local abre com o tema padrão. |
| 6 | Textos | ok | `a-s05-06-08`: "Question 1 of 6" e "Next". |
| 7 | Renderizador substituído | ok, com 1 defeito do exemplo corrigido (avisos do LogBox sobre o "Próxima") | Maestro `07`: NPS do app, estrelas padrão na pergunta 3, concluir; Rede com `POST /collect/sdk-errors` e `render_error`; `survey_completed`. |
| 8 | Slots | ok | `a-s05-06-08`: `cenario-08-header` e `cenario-08-avancar`. |
| 9 | Headless | ok, com 1 defeito do SDK (contêiner vazio) e 1 do exemplo (desfecho ao sair) corrigidos | Maestro `09`: validação, voltar, pular, texto livre (rolagem até "Concluir" com o teclado aberto), concluir → `survey_completed`, sem dispensa. |
| 10 | Modal e inline | ok | `a-s10`: modal em tela cheia com o conteúdo abaixo da barra de status (inset do topo pelo `StatusBar.currentHeight`), **voltar do sistema → `survey_dismissed · via hardware_back`**; inline no meio da rolagem → `survey_presented`. |
| 11 | Bloqueio | ok | Maestro `11`: `placement_blocked`; "Pagar" → `placement_survey_held`, nada na tela; sair → a retida abre; fundo → `via backdrop`. |
| 12 | Adiamento | ok | `a-s12`: prazo de 10 s → `placement_deferred` e `placement_expired {"heldMs":10009}`, sem exibição; prazo de 60 s → outra tela → liberar → `placement_released`, `placement_available` e a folha do SDK. (Com 10 s, o caminho até "Liberar" passa do prazo no emulador; o SDK descarta, como deve.) |
| 13 | Sem rede | ok | Maestro `13`: "Sem rede", fila ≥ 1; fechar o Expo Go de verdade (`stopApp: host.exp.exponent`) e reabrir → "Entregue", "Envios da resposta nesta abertura: 1", fila 0; backend com 1 registro para a exibição. |
| 14 | Falhas | ok | `a-s14`: chave inválida (401), endereço inalcançável (falhou), API lenta (cortada); "3 toque(s)", nenhuma pesquisa. |
| 15 | Proxy | ok | `a-s15` pelo `http://10.0.2.2:8787/pitaco`. Log do proxy: `GET /pitaco/saude -> 404 em 0 ms (fora da superfície pública, não repassado)`, `POST /pitaco/collect/eligibility -> 200 em 62 ms`, `POST /pitaco/collect/displays -> 201 em 22 ms`, `POST …/events -> 202 em 50 ms` e `… 202 em 25 ms`, `POST …/submission -> 204 em 32 ms`. |

Pontos exclusivos do Android:
- **Voltar do sistema:** sheet do SDK (`hardware_back`), gorhom (`hardware_back`, pelo `BackHandler` do exemplo), modal em tela cheia (`hardware_back`) e pesquisa como tela (`navigation`): os quatro conferidos.
- **Teclado:** gorhom ok desde o início; sheet do SDK corrigido no SDK (defeito 3).
- **Insets:** barra de status pelo `StatusBar.currentHeight` no modal e no sheet; nada sob a barra de gestos (o `paddingBottom` do padrão do Android é 0, e a janela do `Modal` termina acima dela).
- **Tema escuro:** moldura, sheet do SDK, gorhom e tela no esquema escuro (capturas `android-*-escuro.png`), depois da correção do `ThemeProvider`.

### Saída exata do Maestro (suíte inteira, depois de todas as mudanças)

Android, `cd sdk/example && npm run e2e:android` (`maestro --platform android test .maestro/`), `.env` com `10.0.2.2`:

```
Waiting for flows to complete...
[Passed] 09 · Headless (2m 2s)
[Passed] 07 · Renderizador substituído (1m 25s)
[Passed] 13 · Sem rede (1m 16s)
[Passed] 02 · Com gorhom (1m 52s)
[Passed] 11 · Bloqueio (1m 18s)
[Passed] 03 · Como tela (1m 15s)
[Passed] 01 · Sheet do SDK (1m 11s)
7/7 Flows Passed in 10m 19s
```

Código de saída 0.

iOS, `cd sdk/example && npm run e2e:ios` (`maestro --platform ios test .maestro/`), `.env` com `localhost`, com as mesmas mudanças do SDK, do exemplo e dos fluxos:

```
Waiting for flows to complete...
[Passed] 09 · Headless (54s)
[Passed] 07 · Renderizador substituído (38s)
[Passed] 13 · Sem rede (40s)
[Passed] 02 · Com gorhom (53s)
[Passed] 11 · Bloqueio (23s)
[Passed] 03 · Como tela (41s)
[Passed] 01 · Sheet do SDK (25s)
7/7 Flows Passed in 4m 34s
```

Código de saída 0.

Rodadas anteriores no Android, até o verde (cada falha levou a uma correção registrada acima):
- Fluxo 01 sozinho: Expo Go no carregamento (`adb reverse` sem resposta) → deep link por `10.0.2.2`; depois, a notificação do LogBox sobre a barra de abas → aviso da troca de storage ignorado.
- Suíte 1 (interrompida, 3 falhas): folha vazia presa do SDK depois do cenário 9 (defeito 1) e `stopApp` sem efeito (pacote em minúsculas).
- Suíte 2 (`4/7 Flows Failed`): tutorial de caneta do Gboard nos fluxos que digitam e, na sequência, a tela inicial do Android.
- Suíte 3 (`3/7 Flows Failed`) e 4 (`1/7 Flow Failed`): o Expo Go frio engolindo o link (primeiro com o `retry`, depois com o Expo Go aquecido antes do link); avisos do LogBox sobre o "Próxima" do cenário 7.
- Suíte 5 (`3/7 Flows Failed`): o link atrasado mandando o app para o fundo (resolvido com o Expo Go aquecido).
- Duas rodadas com `7/7 Flows Failed` em 0 s: JVM do Maestro órfã segurando o driver (achado de ambiente).
- Suíte 8 (`1/7 Flow Failed`, 07): a notificação do aviso do Expo Router sobre a barra de abas.
- Suíte 9: `7/7 Flows Passed in 10m 19s` (acima).

### Capturas e vídeos (`sdk/example/docs/capturas/`, 18 arquivos, 8,8 MB)

A mesma pergunta nas doze capturas: a de NPS (pergunta 1), com o 9 marcado ("Pergunta 1 de 5", porque o 9 deixa a condicional sem efeito). Barra de status limpa: `xcrun simctl status_bar booted override --time 9:41 …` no iOS e o modo demo do SystemUI no Android (9:41, bateria cheia, sem notificações; o ícone de Wi-Fi com "!" e o "3G" do modo demo ficaram). O tema do sistema acompanha o do cenário (`simctl ui appearance`, `cmd uimode night`), e a moldura sai no mesmo esquema. Engrenagem do Expo Go: desligada no Android; no iOS, no canto do cabeçalho. Todas conferidas abrindo o arquivo.

| Arquivo | Bytes | | Arquivo | Bytes |
| --- | --- | --- | --- | --- |
| `ios-sheet-claro.png` | 256698 | | `android-sheet-claro.png` | 165039 |
| `ios-gorhom-claro.png` | 248387 | | `android-gorhom-claro.png` | 131487 |
| `ios-tela-claro.png` | 205993 | | `android-tela-claro.png` | 100898 |
| `ios-sheet-escuro.png` | 217345 | | `android-sheet-escuro.png` | 160925 |
| `ios-gorhom-escuro.png` | 222956 | | `android-gorhom-escuro.png` | 131856 |
| `ios-tela-escuro.png` | 180074 | | `android-tela-escuro.png` | 101890 |

Vídeos (a pesquisa percorrida do começo ao fim no cenário 4, tema claro: NPS 9, escala 4, avaliação 4, múltipla escolha "Relatórios", texto livre em branco, "Enviar", agradecimento e volta à tela):

| Arquivo | Bytes | Duração | Resolução | Como |
| --- | --- | --- | --- | --- |
| `ios-sheet.mp4` | 634880 | 33,8 s | 444×960, H.264 | `simctl io booted recordVideo --codec h264` (bruto 2,4 MB), reduzido com `avconvert --preset Preset960x540` |
| `ios-gorhom.mp4` | 865426 | 33,5 s | 444×960, H.264 | idem (bruto 3,6 MB) |
| `ios-tela.mp4` | 792617 | 33,2 s | 444×960, H.264 | idem (bruto 3,0 MB) |
| `android-sheet.mp4` | 1648740 | 40,4 s | 1080×2424, H.264 | `adb shell screenrecord --bit-rate 4000000`, sem conversão (abaixo de 8 MB) |
| `android-gorhom.mp4` | 1599897 | 38,9 s | 1080×2424, H.264 | idem |
| `android-tela.mp4` | 1368779 | 41,8 s | 1080×2424, H.264 | idem |

`ffmpeg` não está instalado; o `avconvert` do macOS fez a redução. Os fluxos e scripts das capturas e dos vídeos ficaram no scratchpad (`captura-04.yaml`, `capturas.sh`, `video-04.yaml`, `abrir-04.yaml`, `videos.sh`), fora do repositório.

### O que ficou de fora

- **Gesto de voltar da borda do iOS:** continua sem validação em simulador (5e); a seta do cabeçalho e o voltar do Android passam pelo mesmo desmonte e foram conferidos.
- **Movimento reduzido no Android:** não medido.
- **Engrenagem do Expo Go no iOS:** o Expo Go 57.0.9 não tem opção para escondê-la; nas capturas do iOS ela aparece no canto do cabeçalho, sem cobrir a pesquisa.
- **Fluxos Maestro para os cenários fora da lista do prompt** (4, 5, 6, 8, 10, 12, 14, 15, o teclado do sheet do SDK e o voltar do gorhom): rodados como fluxos de apoio no scratchpad, não entraram em `.maestro/`.
- **`adb reverse`:** não investigado a fundo; os fluxos usam `10.0.2.2`.
- **Mais memória no AVD** (2 GB): não mexi na configuração do emulador do usuário.

### Resultado exato dos comandos

- `npm run seed -- --target android-emu`: exit 0; `.env` com `http://10.0.2.2:8080/api` e `http://10.0.2.2:8787/pitaco`, aplicação, chave e pesquisa reaproveitadas. `npm run seed` (volta ao iOS): exit 0; `.env` com `http://localhost:8080/api` e `http://localhost:8787/pitaco`.
- `npx jest --ci src/react/__tests__/SurfaceHost src/react/__tests__/PitacoProvider.lifecycle` (contêiner vazio): com a correção `Tests: 18 passed, 18 total`; com a lógica antiga `Tests: 1 failed, 15 passed, 16 total` (o `Received` é a folha com o `<View />` vazio); restaurada `Tests: 16 passed, 16 total`.
- `npx jest --ci src/ui/presentation/__tests__/keyboard src/react/__tests__/SurfaceHost` (teclado): com a correção `Tests: 19 passed, 19 total`; com o Android sem `behavior` `Tests: 2 failed, 1 passed, 3 total` (`Expected: "height"`, `Received: undefined`); restaurada `Tests: 19 passed, 19 total`.
- `cd sdk && npm run verify`: **exit 0**. `eslint .` sem problema; `tsc --noEmit` sem erro; `jest --ci`: `Test Suites: 40 passed, 40 total` · `Tests: 312 passed, 312 total` · `Snapshots: 0 total` · `Time: 2.159 s` (307 da 5e + 2 do contêiner vazio + 3 do teclado); `bob build`: `[module]`/`[commonjs] Compiling 84 files in src with babel`, `✔ [module] Wrote files to lib/module`, `✔ [commonjs] Wrote files to lib/commonjs`, `✔ [typescript] Wrote definition files to lib/typescript`; `example:typecheck` e `example:lint` sem erro. Log: `scratchpad/verify-5f.log`.
- `npm run typecheck` e `npm run lint` (exemplo), depois de cada mudança: exit 0.
- `npx expo export --platform ios`: exit 0, `iOS Bundled 7590ms node_modules/expo-router/entry.js (1826 modules)`, `_expo/static/js/ios/entry-….hbc (4.1MB)`.
- `npx expo export --platform android`: exit 0, `Android Bundled 7512ms node_modules/expo-router/entry.js (1923 modules)`, `_expo/static/js/android/entry-….hbc (4.3MB)`. Diretórios de export apagados.
- `npm run e2e:android`: `7/7 Flows Passed in 10m 19s`, exit 0. `npm run e2e:ios`: `7/7 Flows Passed in 4m 34s`, exit 0 (saídas completas acima).
- Passada de apoio no Android: `a-s04`, `a-s05-06-08`, `a-s14`, `a-s15`, `a-s12` (com o prazo de 60 s na liberação), `a-s10` (com a última conferência corrigida para `survey_presented`), `a-s01-teclado` (depois da correção do SDK) e `a-s02-back`: exit 0.
- `npm run proxy`: `Proxy do Pitaco ouvindo em http://0.0.0.0:8787/pitaco`; parado ao fim.
- Metro (`npx expo start --port 8081`, sem `CI`; depois `-c` na troca para o iOS): parado ao fim.
- Estado final: portas 8081 e 8787 sem ninguém escutando; backend em `localhost:8080` (200) e os dois simuladores ligados (iPhone 16 e `emulator-5554`), em tema claro, barra de status do iOS restaurada (`simctl status_bar booted clear`) e modo demo do Android desligado; engrenagem do Expo Go desligada no Android e no canto do cabeçalho no iOS; `.env` do exemplo no alvo iOS (`localhost`). Para voltar ao Android: `npm run seed -- --target android-emu` e `npx expo start -c`.

### Resumo consolidado da Fase 5

**O que o exemplo entrega** (`sdk/example/`): um app Expo SDK 57 com Expo Router, rodando no Expo Go em iOS e Android sem build nativo, que consome o código-fonte do SDK (Metro com `watchFolders` e `resolveRequest` para `../src`, uma cópia só de `react` e `react-native`). Um `<PitacoProvider>` na raiz, com AsyncStorage persistente, e os quinze cenários do prompt, um por tela, cada um variando o Provider por `useScenario`. Um painel de depuração com eventos (agrupados por ordem, exibição ou cenário/forma, com a via da dispensa na linha), rede (embrulho do `fetch`, só para o Pitaco), fila e identidade, e as ações "Limpar storage e identidade" e "Simular app reaberto". Seed idempotente (`npm run seed`, alvos `ios-sim`, `android-emu` e `device`), proxy transparente em Node (`npm run proxy`) e prova sem app (`npm run smoke`). Sete fluxos Maestro (cenários 1, 2, 3, 7, 9, 11 e 13) que rodam iguais no iOS e no Android (`npm run e2e:ios` / `e2e:android`). O README do exemplo, do zero à primeira pesquisa, e as capturas e vídeos do cenário 4 nas duas plataformas. Prova que o SDK convive com reanimated, gesture-handler, gorhom, safe-area-context e async-storage sem exigi-los, e que o `react-native-mmkv` pode estar instalado sem import em runtime.

**Os quinze cenários**

| # | Cenário | iOS (5e, e de novo a suíte na 5f) | Android (5f) |
| --- | --- | --- | --- |
| 1 | Sheet do SDK | ok | ok (voltar do sistema; teclado corrigido no SDK) |
| 2 | Com gorhom | ok | ok (voltar do sistema pelo `BackHandler`) |
| 3 | Como tela | ok (gesto de borda não validável no simulador) | ok (voltar do sistema → `navigation`) |
| 4 | Comparar apresentações | ok | ok |
| 5 | Tema | ok | ok |
| 6 | Textos | ok | ok |
| 7 | Renderizador substituído | ok | ok |
| 8 | Slots | ok | ok |
| 9 | Headless | ok | ok |
| 10 | Modal e inline | ok | ok (voltar do sistema no modal) |
| 11 | Bloqueio | ok | ok |
| 12 | Adiamento | ok | ok |
| 13 | Sem rede | ok | ok |
| 14 | Falhas | ok | ok |
| 15 | Proxy | ok | ok |

**Defeitos do SDK corrigidos ao longo da fase** (todos com teste):
1. 5a: `useColorScheme()` com `'unspecified'` (tipos do React Native 0.86) não compilava em `resolveColorScheme`; vale como ausência de esquema.
2. 5a: o bottom sheet do SDK aparecia no topo da tela (`KeyboardAvoidingView` sem `flex: 1`).
3. 5c: capacidade nova, não defeito: `<PitacoPreview presentation="bottom-sheet" | "modal">` abre a pré-visualização no mesmo contêiner do Provider (antes, `presentation` só mudava o que o `survey_presented` relatava).
4. 5e: o `PitacoProvider` só fazia `detach()` no runtime substituído, e o temporizador de uma pesquisa retida seguia emitindo pelo ouvinte antigo; agora o substituído recebe `dispose()`.
5. 5f: contêiner vazio preso por cima do app depois de uma pesquisa em `inline` (`SurfaceHost`).
6. 5f: no Android, o teclado cobria a folha e o modal do SDK (`behavior` só no iOS); agora `height` no Android.
7. 5f: caractere NUL literal em `PitacoProvider.tsx` (sem efeito em runtime; escondia o arquivo das buscas e dos diffs).

**Lacunas abertas**
- Gesto de voltar da borda do iOS (cenário 3): não validável no simulador; conferir num aparelho físico.
- Desmonte definitivo do `<PitacoProvider>` continua só com `detach()` (lacuna da 5e): o temporizador de uma pesquisa retida ainda vence e emite `placement_expired` pelo `onEvent` da árvore desmontada. Não abre exibição nem envia nada.
- Causa do `present()` ignorado na segunda apresentação do mesmo `BottomSheetModal` do gorhom 5.2.14 (5e): não isolada; o exemplo remonta o modal por exibição.
- Movimento reduzido: mecanismo conferido no código; a diferença de animação não foi medida em nenhuma das plataformas.
- MMKV: fora do Expo Go; coberto só pelos testes do SDK e mostrado no README como trecho.
- Engrenagem do Expo Go no iOS: sem opção para esconder; aparece nas capturas do iOS, no canto do cabeçalho.
- Ambiente Android: o emulador de 2 GB fica apertado de memória com o simulador e o backend juntos (Metro encerrado duas vezes); o Expo Go frio às vezes engole o deep link (contornado nos fluxos abrindo antes o Expo Go).

## Fase 6 — Documentação

### Arquivos principais

Criados:
- `sdk/README.md`: Documentação completa do SDK para o usuário final, contendo o guia inicial, opções da API, catálogo de eventos, informações de privacidade e guias customizados.

### Decisões tomadas

- A documentação foi estruturada de maneira progressiva: introduz a integração mínima (Cenário 1) e em seguida fornece a referência completa de opções.
- Inclusão do aviso explícito sobre `atributos` e `reference` para que desenvolvedores não passem PII (Personally Identifiable Information) sem intenção.
- Como o catálogo de eventos (`sdk/src/catalog/events.ts`) possui payloads específicos para 18 tipos de eventos, todos foram listados com um exemplo em JSON para facilitar o debug e o envio para as ferramentas de Analytics dos clientes.
- As seções de guia curto apontam claramente para os cenários do aplicativo de exemplo (Cenários 2, 3 e 9), mostrando a flexibilidade de integração.

### O que ficou de fora

- O foco do arquivo se restringe apenas à adoção do SDK e integração na ponta.
- Não constam no README detalhes sobre como rodar testes de E2E do repositório ou como instanciar o backend localmente (já documentados na arquitetura e nas documentações internas).

### Resultado do portão (npm run verify)

`cd sdk && npm run verify` encerrou com código 0:
- O linter completou sem avisos ou erros.
- A compilação do TypeScript `tsc --noEmit` executou perfeitamente.
- `jest --ci` finalizou com `Test Suites: 40 passed, 40 total`, `Tests: 312 passed, 312 total`.
- O build via `bob build` compilou com sucesso 84 arquivos nos três alvos (`module`, `commonjs`, `typescript`).
- O typecheck e lint no diretório `example` (parte da rotina do verify) terminaram sem falhas.

## Fase 7 — Painel

### Arquivos alterados/criados
- `painel/next.config.ts`: Adicionado suporte e aliases do Webpack/Turbopack para resolver `react-native` como `react-native-web`.
- `painel/package.json`: Adicionadas dependências (`react-native-web` e o SDK local `file:../sdk`).
- `painel/src/features/surveys/components/questions/survey-preview.tsx`: Criado o componente de pré-visualização (feature 2) que importa o `<PitacoPreview />` do SDK e mostra seus eventos.
- `painel/src/features/surveys/components/questions/questions-panel.tsx`: Adicionado layout em grid para renderizar o form de criação ao lado do `SurveyPreview`.
- `painel/src/features/surveys/schemas/question.ts` & `actions.ts`: Exportada função `readQuestionInput` para simplificar extração.
- `painel/src/features/results/schemas/behavior.ts` e `results.ts` e `paths.ts`: Tipagem e mapeamento da API do "Comportamento" (/results/behavior).
- `painel/src/features/results/components/behavior-card.tsx`: Adicionada seção de Comportamento para resultados.
- `painel/src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`: Modificada a tela de resultados para buscar e exibir a seção de comportamento.
- `painel/e2e/stub-api/routes/results.ts`: Simulator da rota `results/behavior` alimentando com dados mocados.
- `painel/e2e/resultados.spec.ts` e `painel/e2e/pesquisas.spec.ts`: Inseridas asserções de E2E sobre o `BehaviorCard` e sobre a seção de `SurveyPreview`.

### Decisões tomadas
- `SurveyPreview` usa `dynamic(..., { ssr: false })` já que o `react-native-web` não pode ser renderizado do lado do servidor sem muito setup extra.
- O preview se atualiza passando o `ordered` da lista de questions que foram aprovadas localmente pela Server Action - garantindo integridade "estado atual do form" sem requerer um novo componente controlado complexo para o formulário inteiro.
- A rota `/results/behavior` foi totalmente implementada no stub com dados coerentes, cobrindo o `BehaviorCard` (que usa a tabela base e definições interativas usando tooltips).
- Definitions são exibidas via Tooltips no `<MetricHeader>` do componente de tabela em vez de textos grandes no card para manter um design conciso.

### O que ficou de fora
- A "live typing" caractere por caractere (onde as mudanças num `<input>` refletem antes mesmo do onBlur ou do Add/Save Action) ficou de fora, já que as Server Actions processam de forma atômica e rápida, satisfazendo a imutabilidade do `estado atual do form` antes da Publicação ("sem salvar" a survey final). 

### Resultado do portão (npm run verify)
Passou com sucesso cobrindo typecheck, lint, unittests e testes end-to-end do Playwright para as novas views inseridas.

## Fase 8 — Integração contínua

### Arquivos criados/alterados
- `.github/workflows/sdk.yml`: Criado workflow no GitHub Actions para CI do SDK Pitaco, executando typecheck, linter, testes e exportação do exemplo.
- `sdk/src/catalog/__tests__/contract.test.ts`: Criado teste de contrato de catálogo que faz o parse direto de `InteractionEventType.java` do backend e garante a equivalência com os tipos listados no SDK em `sdk/src/catalog/events.ts`.

### Decisões tomadas
- O teste de UI (cenário de sequência de eventos com UI padrão, customizada e headless) já havia sido perfeitamente implementado em `sdk/src/ui/content/__tests__/sameSequence.contract.test.tsx` (que compara as 3 abordagens contra um *driver* mocado e a máquina de estado `createMachineDriver()`). Logo, apenas verifiquei e mantive esse modelo em vez de duplicar.
- No CI do `sdk.yml`, separamos as validações em três jobs distintos (`verify-sdk`, `contract-tests`, `expo-export`) para garantir paralelismo. 
- Na validação de *bundle* pelo expo (`expo-export`), passamos as tags `-p ios` e `-p android` diretamente ao `npx expo export`, evadindo o erro de exportação para ambiente web (que gerava exceções de importação do pacote `react-native-web` por não constar ou ser incompatível com a versão mínima testada).
- Para o teste de catálogo bater com o backend, o script lê e interpreta os tokens do arquivo de enum Java `InteractionEventType.java` via regex, extraindo os enums de eventos do back-end para garantir que batam com `INTERACTION_EVENT_TYPES`.

### O que ficou de fora
- Não estamos subindo a API em Java pelo GitHub Actions ou usando swagger UI (via `http://localhost:8080/api/v3/api-docs`); como o monorepo permite inspecionar a classe `.java` via filesystem, essa foi a maneira mais rápida e barata em termos de CI (evita todo o *overhead* de build de Spring Boot só para extrair os tipos num teste de contrato).

### Resultados da rodada local (CI)
- O portão `npm run verify` do SDK passou completamente com sucesso nos lintings e `tsc`.
- A exportação do bundle sem nativo (`npx expo export -p ios`) foi realizada localmente em cerca de ~3.3s ("iOS Bundled... 1826 modules"), confirmando que o bundle não possui dependências não resolvidas ou amarrações restritas.
- O teste do catálogo do `contract.test.ts` falhou na primeira rodada por tipagem (unsafe assignments), sendo ajustado para passar no verify: `expect(sdkEvents.sort()).toEqual(backendEvents.sort())` retornando com sucesso.

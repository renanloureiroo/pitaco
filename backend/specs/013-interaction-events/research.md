# Research: Eventos de interação — catálogo, ingestão e leitura de comportamento

**Feature**: `013-interaction-events` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Catálogo no `core`, fechado e versionado (FR-001 a FR-006)

**Decision**: `core/catalog/InteractionEventType` com os 18 tipos, cada um com `questionScoped` e a
lista de `InteractionField` (`InteractionFieldKind`: contagem ≥ 0, positivo ≥ 1, duração, escolha
fechada, chave de pergunta UUID, valor de resposta 1..120 ou inteiro, nome de evento, booleano).
`fromWire` compara exato; `sanitize` mantém só campos declarados, na ordem do catálogo, e só os que
cabem na forma. `CATALOG_VERSION = 1`. Inteiro aceita o double inteiro do JavaScript, nunca fração.

**Rationale**: `collect` (ingestão) e `results` (vias de dispensa) usam o catálogo; fica ao lado de
`SdkCapabilities`. Fechado porque o app hospedeiro não cria tipo nem altera payload.

**Alternatives considered**: catálogo aberto com `data` livre — o texto digitado chegaria sem
ninguém pedir; modelar "versão em que o tipo nasceu" — só existe a v1.

---

## D-02 — Descarte evento a evento, nunca `400` por evento (FR-007 a FR-010, FR-014 a FR-016)

**Decision**: `InteractionIntake.read(rascunho, exibição, tiposDasPerguntas, agora)` avalia nesta
ordem: tipo (desconhecido → `UNKNOWN_TYPE`, antes do envelope), `displayId` divergente ou envelope
incompleto (`INVALID_ENVELOPE`), chave de pergunta ausente ou malformada (`INVALID_ENVELOPE`),
pergunta fora da versão exibida (`UNKNOWN_QUESTION`), escolha em texto livre
(`INVALID_ENVELOPE`), construção do `InteractionEvent` (falha → `INVALID_ENVELOPE`). Antes disso, o
caso de uso descarta tudo como `DISPLAY_UNAVAILABLE` (aplicação inativa, exibição inexistente, de
outra aplicação ou id malformado) ou `OUTSIDE_WINDOW` (agora > abertura + janela). O DTO só recusa
lote vazio/ausente/acima de 100 e tipo JSON errado no envelope.

**Rationale**: um `400` faria o SDK jogar fora o lote inteiro, inclusive o que estava certo. O tipo
vem antes do envelope porque um SDK mais novo pode mandar tipo e envelope que o servidor não
conhece, e o motivo contado precisa ser o tipo. Exibição alheia com a mesma resposta não confirma a
existência do identificador.

**Alternatives considered**: validar o lote inteiro com Bean Validation — um campo ruim derrubaria
todos.

---

## D-03 — Idempotência por (exibição, seq), teto exato sob trava (FR-011, FR-012)

**Decision**: `unique (display_id, seq)`. Com eventos legíveis, `lockDisplay` (trava consultiva
`pg_advisory_xact_lock(hashtextextended(...))`), `storedSeqs`, `countByDisplay`, depois
`InteractionIntake.admit`: o primeiro de cada `seq` vence, no lote e contra o gravado; o teto corta
pela ordem de `seq` (o que sobra é sempre o fim, nunca um buraco); `saveAll` num único `insert …
select from jsonb_to_recordset(…) on conflict (display_id, seq) do nothing`. Sem legíveis, nada é
travado.

**Rationale**: dois lotes simultâneos passam um de cada vez pela contagem; o reenvio da fila local
nunca duplica.

**Alternatives considered**: um `insert` por evento — 100 idas ao banco por lote.

---

## D-04 — OpenAPI gerado do enum (FR-005)

**Decision**: `InteractionCatalogOpenApiConfiguration` registra um `OpenApiCustomizer` que chama
`InteractionCatalogSchemas` para publicar `InteractionEventType` (enum + `x-pitaco-catalog-version`),
`InteractionEvent` (`oneOf` com discriminador `type`) e `<Tipo>Event`/`<Tipo>Data` com
`additionalProperties: false`. Os eventos do lote apontam para `#/components/schemas/InteractionEvent`.

**Rationale**: fonte única. O teste de contrato do SDK compara a própria lista com
`components.schemas.InteractionEventType.enum` e falha se um tipo existir só de um lado.

**Alternatives considered**: DTO anotado por tipo — 18 classes que podem divergir do enum.

---

## D-05 — A base do comportamento é a exibição instrumentada (FR-021 a FR-026)

**Decision**: `SurveyBehaviorReadModel` com `totalsOf` (exibidas e instrumentadas), `funnelsOf`,
`abandonmentsOf(filtro, abandonedBefore)`, `activeTimesOf`, `dismissalsOf`, todos sobre o mesmo
`ResultsFilter` dos resultados. Definições exatas (em `BehaviorMetric`): respondida e pulada pela
última `question_left` (maior `seq`); abandonada pela última `question_viewed` em exibição
dispensada ou vencida; tempo ativo = soma do `activeMs` das visitas, mediana e p90 por
`percentile_cont`, arredondados; volta = `question_viewed` com `visit ≥ 2` ÷ vistas; troca =
(`answer_changed` ou `answer_deselected`) ÷ `answer_selected`; dispensa pela via da última
`survey_dismissed`.

**Rationale**: SDKs sem eventos não podem diluir as taxas; a definição precisa ser única entre
painel, contrato e código.

**Alternatives considered**: base em todas as exibidas — uma versão antiga do SDK derrubaria todas
as taxas sem nada ter mudado.

---

## D-06 — Definições voltam com os números (FR-025)

**Decision**: `BehaviorMetric` (enum) com `key` e `definition`; `GetSurveyBehaviorUseCase` devolve
`definitions` sempre. `rate(num, den)` ausente sem denominador. As seis vias saem de
`InteractionEventType.SURVEY_DISMISSED.field("via")`.

**Rationale**: o painel mostra a mesma frase que o contrato documenta.

**Alternatives considered**: textos no painel — divergiriam na primeira mudança de regra.

---

## D-07 — Retenção apaga eventos, sem agregado congelado (FR-020)

**Decision**: `ApplyRetentionUseCase.discardEvents` chama `RetentionStore.deleteInteractionEvents(app,
corteDeRespostas, batchSize)` em lotes, cada um na sua transação, depois de respostas e textos.
`retention_runs` continua contando só respostas e textos; os eventos vão para o log.

**Rationale**: a retenção apaga respostas e não exibições, então a cascata não alcança os eventos,
que carregam valor escolhido.

**Alternatives considered**: congelar agregado de comportamento — complexidade sem pedido; depois do
descarte, a leitura conta só o que ficou.

---

## D-08 — `INTERACTION_EVENTS` é capacidade, não exigência (FR-019)

**Decision**: `SdkFeature.INTERACTION_EVENTS` com mínimo 1.0.0 em `SdkCapabilities`; `RequiredSdk` e
`SurveyVersion.requiredSdkVersion` nunca o acrescentam.

**Rationale**: nenhuma versão publicada depende de eventos para ser desenhada; exigir o recurso
suprimiria pesquisas em SDKs que só não enviam eventos.

**Alternatives considered**: exigir na versão mínima — supressão sem motivo.

# Research: Saúde do SDK e proteção da superfície pública

**Feature**: `010-sdk-health` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Uso de versão acumulado em memória e descido em lote (FR-001 a FR-004)

**Decision**: `FindEligibleSurveyUseCase.recordSdkUsage` faz `SdkVersion.parse` do cabeçalho
`X-Pitaco-Sdk-Version` e chama `SdkUsageRecorder.record`. `BufferedSdkUsageRecorder` acumula num
`ConcurrentHashMap` por `(aplicação, versão, dia UTC)`, com teto de 10.000 chaves pendentes, e
`flush()` (`@Scheduled`, `pitaco.health.sdk-usage.flush-interval`, 1 min) desce um delta por chave
em transação própria (`repository.increment`), com até 3 tentativas. Descarga também no
`@PreDestroy`.

**Rationale**: contar cada consulta seria uma escrita por evento do app hospedeiro, no caminho que
mais devolve nada. O número é aproximado por natureza.

**Alternatives considered**: escrita síncrona — custo no caminho quente; contar só aberturas de
exibição — esconderia versões que nunca recebem pesquisa, justamente as antigas.

---

## D-02 — Duas tabelas: rollup e diário (FR-002, FR-003)

**Decision**: `sdk_version_usage` (acumulado, primeira/última ocorrência, `unique (application_id,
version)`) e `sdk_version_daily_usage` (PK `(application_id, version, day)`). A proporção usa o
diário desde `recentFrom = hoje - 14 dias`; `stale` compara `lastSeenAt` com `agora - 14 dias`. O
diário é expurgado depois de 90 dias; o acumulado fica.

**Rationale**: sem o total por dia, uma versão que foi maioria no ano passado continuaria pesando
pelo acumulado.

**Alternatives considered**: guardar cada consulta e agregar na leitura — tabela que cresce com o
tráfego.

---

## D-03 — Sanitização é descarte, não recusa (FR-005 a FR-010)

**Decision**: `SdkErrorKind.fromWire` (fora da lista → `UNKNOWN`). `SdkErrorReport.create` troca
controles por espaço, aplica `PersonalDataMask.mask` (e-mail → `[email]`, sequência de 8+ dígitos
com separadores → `[número]`) e corta em 500. `SdkErrorContext.sanitize` mantém só chaves no padrão
`^[A-Za-z][A-Za-z0-9_.-]{0,39}$` cujas palavras (camelCase e separadores) não estão na lista de dado
pessoal, valores escalares ou um nível de objeto, textos sem cara de pessoal cortados em 200, até 20
chaves (10 no aninhado) e 2.000 caracteres no total; conta o que descartou. Instante além de 5
minutos no futuro vira o recebimento. Aplicação inativa descarta.

**Rationale**: um registro com metade do contexto ainda serve para investigar; recusar o registro
inteiro não protegeria mais ninguém. A palavra é avaliada inteira: `context` contém `text` e não pode
cair junto de `freeText`.

**Alternatives considered**: recusar com 400 — o SDK desistiria calado e o erro se perderia;
confiar no SDK — o contrato pede, a rede de segurança garante.

---

## D-04 — Supressão não é exibição (FR-013, FR-014, FR-017)

**Decision**: tabela `suppression_events`, separada de `survey_displays`, com `reason`,
`min_required_version` (de `RequiredSdk.of(conteúdo entregue)`), `sdk_version` opcional e
`respondent_id` só quando o respondente já existe (`findByIdentity`, sem criar).

**Rationale**: a pesquisa suprimida nunca foi mostrada; contá-la como exibição estragaria a taxa de
resposta que justifica `survey_displays`.

**Alternatives considered**: desfecho `SUPPRESSED` na exibição — mistura alcance com resposta.

---

## D-05 — Deduplicação por resumo irreversível, sob trava (FR-015, FR-016)

**Decision**: `SuppressionDedupKey.of(aplicação, identidade)` = SHA-256 de
`aplicação|tipo|valor`. `RecordSuppressionUseCase` (`@Transactional`) faz `lockDedup(versão,
chave)` e `existsSince(versão, chave, agora - 24h)` antes de gravar. Tudo que não pode virar
supressão (outra aplicação, versão não publicada, versão de outra pesquisa, id malformado,
aplicação inativa) é descartado com log de motivo e a mesma resposta `202`.

**Rationale**: a supressão não cria respondente; guardar a referência crua seria um segundo
cadastro de quem nunca viu pesquisa. Recusar com 404 confirmaria a existência do identificador a
quem tem uma chave extraída.

**Alternatives considered**: deduplicar sem trava — envios simultâneos do mesmo dispositivo
contariam duas vezes.

---

## D-06 — Saúde separa as duas causas de "zero respostas" (FR-018, FR-019)

**Decision**: `GetSurveyHealthUseCase` resolve o período (padrão 30 dias), conta supressões
(`countFor`: total, por motivo, por versão do SDK) e exibições abertas no período
(`countOpenedBetween`), calcula `SuppressionShare` (supressões ÷ exibidas + suprimidas; relevante
com proporção ≥ 0,05 e total ≥ 5), a versão mínima da publicação atual e a última ocorrência do
evento de disparo (`ObservedEventRepository.lastSeenAt`). Os dois motivos aparecem sempre, com zero.

**Rationale**: "está sendo suprimida" e "o evento nunca chegou" têm correções opostas. O mínimo
absoluto existe porque uma supressão em duas consultas é 50% e não diz nada.

**Alternatives considered**: só a contagem — sem proporção, 50 supressões em 10.000 parecem tanto
quanto 50 em 60.

---

## D-07 — Capacidades no `core/catalog`, com o compilador como guarda (FR-020, FR-021)

**Decision**: `SdkCapabilities.minimumFor(QuestionType)` e `minimumFor(SdkFeature)` com `switch`
exaustivo sem `default`; `minimumFor(tipos, recursos)` devolve a maior. Tudo nasce em
`SdkVersion.BASELINE` (1.0.0). `SdkVersion` é semver sem metadado de build, ordenado com
pré-lançamento antes do lançamento; `parse` devolve ausência para o que não casa.

**Rationale**: `survey` (versão mínima ao publicar) e `collect` (versão mínima entregue) usam o
mesmo catálogo — dois módulos, por isso `core`. Tipo novo sem versão declarada não compila.

**Alternatives considered**: tabela de capacidades no banco — dado de produto que muda com release
do SDK, não com operação.

---

## D-08 — Janela fixa em memória, três baldes (FR-022 a FR-024, FR-027)

**Decision**: `FixedWindowRateLimiter(capacity, window, clock)` com `ConcurrentHashMap` de janelas
por assunto e expurgo oportunista uma vez por janela. Baldes: por origem (120/min), por chave
(1.200/min, todas as rotas públicas menos `/collect/sdk-errors`), registros de erro por chave
(30/min). Ordem em `SecurityWebMvcConfiguration`: origem → autenticação → chave (ou balde de erros).
Nada disso alcança `/applications/**`. Estouro lança `RateLimitExceeded` (`rate_limit.exceeded`,
`ErrorType.RATE_LIMITED`), e o `ApiExceptionHandler` põe `Retry-After` (arredondado para cima,
mínimo 1).

**Rationale**: origem antes da chave para que uma inundação anônima nem consulte o banco; balde por
chave, não por aplicação, para conter a chave vazada sem derrubar as outras do mesmo app.

**Alternatives considered**: token bucket — precisão que não compra nada aqui; limite por aplicação
— uma chave vazada derrubaria o app inteiro.

---

## D-09 — Origem só do que um proxy confiável escreveu (FR-025, FR-026)

**Decision**: `OriginResolver.resolve(par, cabeçalhos)`: par imediato não confiável é a origem;
atrás de confiável, vale `CF-Connecting-IP` (`origin.client-ip-header`) se não for confiável; senão
o `X-Forwarded-For` lido do fim para o começo, pulando confiáveis, parando no primeiro salto
ilegível. `IpLiteral` aceita só literais (IPv4 montado byte a byte; IPv6 só com `:`), tira porta e
colchetes, nunca resolve DNS. `IpRange.parse` falha na subida. O interceptor lê a requisição crua,
desembrulhando o `ForwardedHeaderFilter`. Faixas padrão: loopback e privadas.

**Rationale**: contar saltos fixos foi descartado — quando falta um salto, a contagem cai num valor
escrito pelo cliente.

**Alternatives considered**: confiar no primeiro `X-Forwarded-For` — escolhido pelo cliente;
`getRemoteAddr` depois do filtro — já reescrito a partir do cabeçalho.

---

## D-10 — Superfícies separadas por prefixo (FR-028)

**Decision**: `ApiKeyAuthenticationInterceptor` em `/collect/**` (401 `api_key.missing` /
`api_key.invalid`) e `AdminSurfaceInterceptor` em `/applications/**`, que lança
`ApiKeyForbiddenSurface` (`api_key.forbidden_surface`, 403) quando há `X-Pitaco-Key`. O resultado
da autenticação vai como atributo da requisição, nunca `ThreadLocal`.

**Rationale**: herdado do desenho da `004` e mantido aqui junto da proteção: apresentar a chave do
SDK no painel falha alto em vez de ser ignorado em silêncio.

**Alternatives considered**: ignorar o cabeçalho no painel — esconderia integração errada.

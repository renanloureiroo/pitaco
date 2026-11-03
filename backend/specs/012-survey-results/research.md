# Research: Resultados de pesquisa — agregados, recortes, respostas abertas e export

**Feature**: `012-survey-results` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Read model com SQL agrupado (FR-031)

**Decision**: porta `SurveyResultsReadModel` com métodos de projeção (`displayCountsOf`,
`timelineOf`, `answerCountsOf`, `optionCountsOf`, `numericCountsOf`, `questionsOf`,
`questionShapesOf`, `displayedVersionsOf`, `attributeCatalogOf`, `openAnswersOf`, `answersOf`,
`attributeNamesOf`, `displaysAfter`, `attributesOf`, `retainedSummaryOf`, `retainedCountsOf`),
implementada por `SurveyResultsReadModelJpa` sobre `ResultsSql`.

**Rationale**: cada número sai de uma consulta agrupada; nada é somado em memória linha a linha, e
nada passa pelo domínio da coleta.

**Alternatives considered**: tabelas de agregado mantidas na escrita — mais escrita no caminho
quente e um número que pode divergir do dado; ler entidades e agregar no Java — N+1 e memória.

---

## D-02 — Uma consulta, três fatos de escopo (FR-008, FR-021, FR-026, FR-029)

**Decision**: `SurveyScopeGateway.scopeOf(app, survey)` devolve `SurveyScope(everPublished,
openTextRetentionDays, templateKind, mayContainPersonalData)`. `ResultsScope.resolve` transforma
identificador malformado, inexistente ou de outra aplicação no mesmo `404` (`survey.not_found`, ou
`application.not_found` para aplicação malformada).

**Rationale**: a leitura de resultados precisa saber se há o que ler, qual prazo apaga texto, se a
pesquisa é NPS e se pode haver dado pessoal — tudo de uma vez, antes de qualquer consulta pesada.

**Alternatives considered**: três portas — três idas ao banco para a mesma linha.

---

## D-03 — O mesmo recorte para tudo (FR-012 a FR-014)

**Decision**: `ResultsSelection(from, to, attribute, attributeValue, versionNumber)`; atributo sem
valor é ausência. `ResultsQueryDTO` e `OpenAnswersQueryDTO` validam com `@AssertTrue`: período
ordenado, valor exige atributo, atributo não vazio; tamanhos 80/200; versão ≥ 1. O recorte volta em
`filter`, com `attributeAbsent`.

**Rationale**: agregado, taxa, respostas abertas, export e comportamento precisam contar sempre o
mesmo conjunto; o painel põe o recorte na URL.

**Alternatives considered**: recorte por leitura — divergência garantida.

---

## D-04 — Agregação por tipo, ausência distinta de zero (FR-001 a FR-007)

**Decision**: `QuestionAggregation.aggregate` devolve `Optional.empty()` sem resposta; escolha com
proporção sobre respondentes, ordem da definição, desconhecidas no fim pelo valor; numérico com
média e faixa inteira; NPS com `NpsGroups` (9–10, 7–8, 0–6; pontuação `(P − D) × 100 ÷ total`);
texto sinalizado. Não aplicável fica em contagem própria.

**Rationale**: um gráfico de distribuição com buracos esconde os valores que ninguém escolheu; "sem
resposta" e "zero" pedem telas diferentes.

**Alternatives considered**: proporção sobre exibidas — mistura quem nem viu a pergunta.

---

## D-05 — Taxa com definição única e abandono derivado (FR-009 a FR-011)

**Decision**: `ResponseRate.DEFINITION` volta junto dos números. `DisplayResolution.of(desfecho,
abertura, abandonedBefore)` reproduz a regra da coleta: `STARTED` antes de `agora − display-timeout`
é `ABANDONED`, depois é `IN_PROGRESS`. Série diária por `to_char(opened_at at time zone 'UTC')`.

**Rationale**: quem lê a tela e quem lê o export contam a mesma coisa; abandono nunca é gravado.

**Alternatives considered**: importar a regra da coleta — dois módulos se conhecendo pelo domínio.

---

## D-06 — Comparabilidade por pergunta e retenção só onde soma (FR-016 a FR-018)

**Decision**: só no consolidado, `QuestionComparability.of(formas, versõesExibidas)`: comparável se
todas as versões com exibição no recorte têm a mesma forma (`QuestionShape.sameMeaningAs`: tipo,
opções, faixa, condição). O congelado da retenção entra só quando `isVersionOnly()`;
`RetentionOutput.applied`/`notApplied` com nota fixa. Amostra = exibições com resposta dada (+
congeladas quando somadas); pequena abaixo de `small-sample-threshold`.

**Rationale**: marcar a pesquisa inteira por uma opção trocada jogaria fora o dado bom das outras
perguntas; somar o congelado num recorte de período contaria o que está fora dele.

**Alternatives considered**: bloquear o consolidado entre grupos — o operador perderia a soma segura.

---

## D-07 — Export em streaming, escopo antes do primeiro byte (FR-023 a FR-027)

**Decision**: `ExportSurveyResultsUseCase` resolve escopo, perguntas e nomes de atributo
sincronamente e devolve `ExportOutput` com `rows(consumer)`, que lê `displaysAfter(filtro, cursor,
500)` e, por lote, `attributesOf` e `answersOf`. `SurveyResultsController.export` devolve
`StreamingResponseBody` com `text/csv; charset=UTF-8`, `Content-Disposition` e, se
`mayContainPersonalData`, `X-Pitaco-Content-Warning: may-contain-personal-data`. `CsvExportWriter`
escreve BOM, CRLF, escapa vírgula/aspas/quebra.

**Rationale**: 404 e 400 ainda saem como `problem+json`; o arquivo nunca inteiro em memória; quem
consome o arquivo fora do painel recebe o mesmo lembrete que a tela mostra.

**Alternatives considered**: gerar o arquivo e servir depois — armazenamento temporário de dado
pessoal.

---

## D-08 — Respostas abertas com contexto numa consulta (FR-019 a FR-022)

**Decision**: `openAnswersOf` filtra `text_value ilike` com escape, `answered_at` depois do corte de
texto, ordena `answered_at desc, id desc`; `answersOf(displayIds da página)` traz o contexto,
traduzido por `ReadableAnswers` (opção pelo rótulo atual).

**Rationale**: o porquê do número está no texto, e o texto sem o resto da exibição perde metade do
sentido.

**Alternatives considered**: contexto por resposta — N+1.

---

## D-09 — A visão geral é composição no painel (US7)

**Decision**: nenhuma rota de aplicação. O painel lê `GET …/results` por pesquisa, no período atual e
no anterior, e uma vez por valor de atributo para a análise por segmento.

**Rationale**: tudo que a visão geral precisa já existe por pesquisa; um agregado por aplicação no
backend duplicaria regra (taxa, NPS, abandono) sem caso que o exija.

**Alternatives considered**: `GET /applications/{id}/overview` — segundo lugar para a mesma
definição de taxa, a abstrair quando houver um segundo consumidor.

# Implementation Plan: Resultados de pesquisa — agregados, recortes, respostas abertas e export

**Branch**: `012-survey-results` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-survey-results/spec.md`

## Summary

Módulo novo, `modules/results`, só de leitura, sobre um **read model** (`SurveyResultsReadModel`)
em vez de repositórios de entidade: cada número sai de uma consulta SQL agrupada (`ResultsSql`), e o
domínio do módulo só faz a matemática que não cabe no banco sem perder clareza (agregação por tipo,
NPS, taxa, comparabilidade, desfecho resolvido).

1. **Read model nativo** (D-01): `SurveyResultsReadModelJpa` executa `ResultsSql` por
   `SurveyResultsJpaRepository`; nada passa pelo domínio da coleta.
2. **Uma travessia de escopo** (D-02): `SurveyScopeGateway.scopeOf` responde, numa consulta,
   existência, se já publicou, prazo de texto livre, modelo e se há texto livre publicado.
3. **Recorte único** (D-03): `ResultsSelection` → `ResultsFilter`, o mesmo para resultados,
   respostas abertas, export e comportamento.
4. **Agregação no domínio** (D-04, D-05): `QuestionAggregation`, `NpsGroups`, `ResponseRate`,
   `DisplayResolution`, `QuestionComparability`.
5. **Retenção somada só onde é honesta** (D-06).
6. **Export em streaming por cursor** (D-07): `ExportOutput` com um `Consumer` de linhas, escrito
   por `CsvExportWriter` num `StreamingResponseBody`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA com consultas nativas, Validation),
springdoc-openapi, Lombok. **Nenhuma dependência nova** (CSV escrito à mão, RFC 4180).

**Storage**: nenhuma migration nesta fatia. Lê `survey_displays`, `survey_answers`,
`survey_answer_options`, `survey_display_attributes`, `respondents`, `survey_versions`,
`questions`, `surveys` e, da `011`, `aggregate_snapshots`/`aggregate_snapshot_counts`. Usa os
índices já existentes (`idx_survey_displays_survey_listing`, `idx_survey_answers_answered_at`,
`idx_aggregate_snapshots_survey`).

**Testing**: domínio (`QuestionAggregationTest`, `ResponseRateTest`, `QuestionComparabilityTest`);
casos de uso sobre `InMemorySurveyResultsReadModel` e `InMemoryResultsSurveyScopeGateway`
(`GetSurveyResultsUseCaseTest`, `GetSurveyResultsAdvancedAuthoringTest`,
`GetSurveyResultsRetentionTest`, `ListOpenAnswersUseCaseTest`, `ExportSurveyResultsUseCaseTest`);
`CsvExportWriterTest`, `CsvExportWriterNotApplicableTest`, `ResultsQueryDTOTest`; E2E
(`SurveyResultsE2ETest`, `OpenAnswersE2ETest`, `ExportResultsE2ETest`,
`ExportPersonalDataWarningE2ETest`, `AdvancedAuthoringResultsE2ETest`, `ResultsOpenApiTest`).

**Target Platform / Project Type**: Linux server, JVM UTC · web service

**Performance Goals**: sem SLO. Leitura de resultados com número fixo de consultas agrupadas,
independente do volume; respostas abertas com uma consulta de página, uma de contagem e uma de
contexto; export em lotes de 500 por cursor `(opened_at, id)`.

**Constraints**: nenhuma escrita; nenhuma soma linha a linha em memória; o export nunca carrega o
arquivo inteiro em memória.

**Scale/Scope**: 3 rotas (`results`, `results/open-answers`, `results/export`), 3 casos de uso, 1
read model com 17 consultas, 1 gateway de escopo.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | `results/domain` é Java puro; `SurveyResultsReadModel` e `SurveyScopeGateway` são portas em `application`; consultas nativas só em `infra/database/jpa`. | ✅ |
| **II. Domínio Que Se Valida** | Ausência é `Optional` (agregado, taxa, média do NPS no resumo); `DisplayResolution` concentra a regra de abandono. | ✅ |
| **III. Teste É Contrato** | Cada forma de agregado e cada aviso têm teste de domínio ou de caso de uso; E2E confere números contra o semeado e que nada muda no banco. | ✅ |
| **IV. Consistência da Superfície Pública** | `SurveyResultsSwagger` declara 200/400/403/404 nas quatro leituras (inclusive `behavior`, da `013`); o export documenta `X-Pitaco-Content-Warning`; erro de escopo sai como `problem+json` antes do streaming. | ✅ |
| **V. Performance Por Construção** | Consultas agrupadas; contexto da página numa consulta; export por cursor. | ✅ |

**Re-avaliação após Phase 1**: o read model é exceção consciente ao molde "porta de repositório de
entidade": resultados não têm entidade, têm projeções. Princípio V nomeia "projeção explícita" como
saída. Aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/012-survey-results/
├── plan.md · research.md · data-model.md · quickstart.md · tasks.md
├── contracts/results.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/modules/results/
├── domain/
│   ├── DisplayResolution.java, ResponseRate.java
│   ├── aggregation/NpsGroups.java, QuestionAggregate.java, QuestionAggregation.java
│   └── comparability/Comparability.java, QuestionComparability.java, QuestionShape.java
├── application/
│   ├── errors/SurveyNotFound.java
│   ├── gateways/SurveyScopeGateway.java
│   ├── outputs/SurveyResultsOutput.java, QuestionResultOutput.java, ResponseRateOutput.java,
│   │           NpsSummaryOutput.java, RetentionOutput.java, ResultsFilterOutput.java,
│   │           AttributeCatalogOutput.java, OpenAnswerOutput.java, ExportOutput.java
│   ├── readmodels/SurveyResultsReadModel.java
│   ├── services/ResultsScope.java
│   └── usecases/GetSurveyResultsUseCase.java, ListOpenAnswersUseCase.java,
│                ExportSurveyResultsUseCase.java, ResultsSelection.java, ReadableAnswers.java
└── infra/
    ├── config/ResultsProperties.java, UseCasesConfiguration.java
    ├── database/jpa/ResultsSql.java, SurveyResultsJpaRepository.java,
    │                SurveyResultsReadModelJpa.java
    ├── gateways/SurveyScopeGatewayJpa.java, ResultsScopeJpaRepository.java
    └── http/
        ├── controllers/SurveyResultsController.java, SurveyResultsSwagger.java
        ├── dtos/ResultsQueryDTO.java, OpenAnswersQueryDTO.java, SurveyResultsResponseDTO.java,
        │        OpenAnswerResponseDTO.java
        ├── export/CsvExportWriter.java
        └── presenters/SurveyResultsPresenter.java, OpenAnswerPresenter.java

src/main/resources/application.yml      (pitaco.results.small-sample-threshold)
```

**Structure Decision**: módulo próprio porque a leitura agregada atravessa autoria (definição das
perguntas), coleta (respostas) e privacidade (congelado) e não pertence a nenhuma; atravessa por
SQL de leitura, sem importar domínio de outro módulo. A regra de abandono é reproduzida em
`DisplayResolution` em vez de importada da coleta.

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| SQL nativo agrupado no read model | Somar em memória exibição a exibição não escala e viola o Princípio V. | Carregar entidades da coleta e agregar no Java. |
| Busca de texto com `ilike` sem índice textual | O conjunto já é recortado pela pesquisa e pelo período antes do filtro de texto; volume de texto livre por pesquisa é baixo. | Índice trigram — extensão nova sem problema medido. |

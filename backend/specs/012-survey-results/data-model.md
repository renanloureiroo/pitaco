# Data Model: Resultados de pesquisa — agregados, recortes, respostas abertas e export

**Feature**: `012-survey-results` · **Date**: 2026-09-12

Esta fatia **não cria tabela**. Tudo abaixo é tipo de leitura.

---

## 1. Domínio — `results/domain`

| Tipo | Forma |
| --- | --- |
| `ResponseRate(displayed, completed, dismissed, abandoned, inProgress, Optional<Double> rate)` | `rate` ausente sem exibição; `DEFINITION` textual |
| `DisplayResolution` | `COMPLETED`, `DISMISSED`, `ABANDONED`, `IN_PROGRESS`; `of(storedOutcome, openedAt, abandonedBefore)` |
| `QuestionAggregate` (selada) | `Choice(options[OptionShare])`, `Numeric(average, distribution[ValueShare])`, `Nps(promoters, passives, detractors, score, distribution)`, `Text()` |
| `OptionShare(value, label, count, share)` / `ValueShare(value, count, share)` | `share` sobre os que responderam |
| `NpsGroups(promoters, passives, detractors)` | `PROMOTER_MIN = 9`, `PASSIVE_MIN = 7`; `score()` |
| `QuestionShape(key, versionNumber, type, optionValues, range?, condition?)` | `sameMeaningAs` |
| `Comparability(comparable, versions)` | versões consideradas, ordenadas |

## 2. Recorte — `results/application/usecases/ResultsSelection`

`ResultsSelection(from?, to?, attribute?, attributeValue?, versionNumber?)` →
`SurveyResultsReadModel.ResultsFilter(applicationId, surveyId, from?, to?, AttributeFilter(name,
value?)?, versionNumber?)`. `isVersionOnly()` = sem período e sem atributo. Saída em
`ResultsFilterOutput(from?, to?, attribute?, attributeValue?, attributeAbsent, version?)`.

## 3. Escopo — `results/application/gateways/SurveyScopeGateway`

`SurveyScope(everPublished, openTextRetentionDays?, templateKind?, mayContainPersonalData)`;
`isNpsTemplate()`.

## 4. Read model — `results/application/readmodels/SurveyResultsReadModel`

| Record | Campos |
| --- | --- |
| `QuestionDefinition` | `key`, `statement`, `type`, `position`, `options`, `range?` |
| `DisplayCounts` | `displayed`, `completed`, `dismissed`, `abandoned`, `inProgress`, `responding` |
| `DailyCounts` | `day`, `displayed`, `completed` |
| `AnswerCounts` | `key`, `answered`, `skipped`, `notApplicable` |
| `OptionCount` / `NumericCount` | `key`, `value`, `count` |
| `AttributeValueCount` | `name`, `value`, `count` |
| `OpenAnswersQuery` | `filter`, `term?`, `notBefore?`, `page`, `size` |
| `OpenAnswerRow` | `displayId`, `key`, `text`, `answeredAt` |
| `AnswerRow` | `displayId`, `key`, `status`, `text?`, `number?`, `options`, `answeredAt` |
| `DisplayRow` | `id`, `respondentReference`, `versionNumber`, `storedOutcome`, `sdkVersion?`, `openedAt`, `closedAt?` |
| `DisplayCursor` | `openedAt`, `id` |
| `AttributeRow` | `displayId`, `name`, `value` |
| `RetainedSummary` / `RetainedCount` | da `011`: `respondingDisplays`, `discardedBefore` / `key`, `dimension`, `value`, `count` |

## 5. Outputs — `results/application/outputs`

- `SurveyResultsOutput(everPublished, responseRate, questions, sampleSize, smallSample, filter,
  attributes, nps?, retention?)`
- `ResponseRateOutput(... , rate?, definition, timeline[TimelinePoint(day, displayed, completed)])`
- `QuestionResultOutput(key, statement, type, position, answered, skipped, notApplicable,
  aggregate?, comparability?)`
- `NpsSummaryOutput(questionKey, respondents, promoters, passives, detractors, score?)`
- `RetentionOutput(snapshotApplied, discardedBefore, note)` com `APPLIED_NOTE` e `NOT_APPLIED_NOTE`
- `AttributeCatalogOutput(name, values[ValueCount(value, count)])`
- `OpenAnswerOutput(displayId, questionKey, statement, text, answeredAt, context[AnswerContext(questionKey, statement, value)])`
- `ExportOutput(attributeNames, questions[ExportQuestion(key, statement)], rows(consumer), mayContainPersonalData)`;
  `ExportRow(displayId, respondentReference, versionNumber, outcome, openedAt, closedAt?,
  sdkVersion?, attributes, answers)`; `ExportAnswer(text?, number?, options)` com `EMPTY` e
  `NOT_APPLICABLE`.

## 6. Erros

| Classe | `code` | Status |
| --- | --- | --- |
| `results/application/errors/SurveyNotFound` | `survey.not_found` | 404 |
| `app/application/errors/ApplicationNotFound` | `application.not_found` | 404 |

## 7. Configuração

| Chave | Padrão |
| --- | --- |
| `pitaco.results.small-sample-threshold` (`ResultsProperties`) | `10` |
| `pitaco.collect.display-timeout` (prazo de abandono, lido também aqui) | `30m` |

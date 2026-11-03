# Data Model: Autoria avançada — modelos, condição, duplicação e publicação consciente

**Feature**: `009-advanced-authoring` · **Date**: 2026-09-12

---

## 1. Survey — `survey/domain/entities` (alterado)

| Campo novo | Tipo | Nota |
| --- | --- | --- |
| `template` | `Optional<SurveyTemplate>` | `NPS`, `CSAT`, `CES`; coluna `template_kind varchar(8)` com `check` |
| `freeTextNotice` | `FreeTextNotice` | padrão `standard()`; colunas `free_text_notice_enabled` (default `true`) e `free_text_notice_text varchar(200)` |

Operações: `rename` (qualquer estado), `redefineFreeTextNotice`, `duplicateInto(destino, nome)`.

## 2. FreeTextNotice — `survey/domain/valueobjects`

`enabled: boolean`, `customText: Optional<String>` (1..200 após `strip`). `text()` = texto próprio
ou `DEFAULT_TEXT` ("Evite escrever dados pessoais, como nome, telefone ou e-mail."). Código
`survey.free_text_notice_invalid`.

## 3. Question — `survey/domain/entities` (alterado)

| Campo novo | Tipo | Coluna |
| --- | --- | --- |
| `labels` | `ScaleLabels(min, max)` | `range_min_label`, `range_max_label varchar(60)` |
| `condition` | `Optional<DisplayCondition>` | `condition_source_key`, `condition_operator`, `condition_min`, `condition_max` + `question_condition_values` |

`ScaleLabels`: até 60 caracteres cada, vazio vira ausente (`question.scale_labels_invalid`); só em
pergunta com faixa (`question.scale_labels_not_allowed`).

## 4. DisplayCondition — `survey/domain/valueobjects`

| Campo | Regra |
| --- | --- |
| `sourceKey` | `QuestionKey` obrigatória |
| `operator` | `EQUALS`, `NOT_EQUALS` (1 valor), `IN` (1..50), `BETWEEN` (só `min`/`max`, `min ≤ max`) |
| `values` | 1..120 caracteres, sem repetição |
| `min`, `max` | `Optional<Integer>` |

`fingerprint()` ignora a ordem dos valores; `pointingTo(novaChave)` serve à duplicação.

Restrições no banco:

- `ck_questions_condition_operator` — origem e operador presentes juntos.
- `ck_questions_condition_operator_values` — operador ∈ os quatro.
- `fk_questions_condition_source (version_id, condition_source_key) → questions (version_id, question_key)` deferível.
- `question_condition_values (question_id FK cascade, position, value varchar(120))`, PK `(question_id, position)`.

## 5. ConditionRejected — `survey/domain/valueobjects`

`DomainException` BUSINESS_RULE com extensões `field` e, quando a culpa é de outra pergunta,
`questionKey`.

| `code` | `field` |
| --- | --- |
| `question.condition_source_invalid` | `condition.sourceKey` |
| `question.condition_operator_invalid` | `condition.operator` |
| `question.condition_value_invalid` | `condition.values` |
| `question.condition_source_in_use` | `condition` (+ `questionKey` da dependente) |
| `question.condition_order_invalid` | `condition` (+ `questionKey` da dependente) |

## 6. Classificação e publicação — `survey/domain/publication`

- `ChangeClassification.Difference(questionKey, kind)`, `kind` ∈ `QUESTION_ADDED`,
  `QUESTION_REMOVED`, `TYPE_CHANGED`, `OPTIONS_CHANGED`, `RANGE_CHANGED`, `CONDITION_CHANGED`.
- `PublicationImpediment(code, field, questionKey?)` — `survey.no_questions`,
  `question.statement_missing`, `question.options_missing`, `trigger.missing`,
  `trigger.window_invalid`.
- `PublicationWarning(code, ruleId?, attribute?, competingSurveys, minRequiredVersion?,
  unsupportedShare?)` — `segmentation.no_known_match`, `segmentation.contradictory`,
  `trigger.competing_surveys`, `compatibility.unsupported_by_majority`.
- `CompetingSurvey(surveyId, name, priority)`, `KnownAttribute(name, values, saturated)`,
  `SdkTraffic(version, requests)`.

## 7. Portas de leitura sobre `collect` — `survey/application/gateways`

| Porta | Adaptador | Lê |
| --- | --- | --- |
| `AttributeCatalogGateway.knownIn(applicationId, names)` | `AttributeCatalogGatewayCollect` + `SurveyAttributeCatalogJpaRepository` | `application_attributes` e valores; `saturated` quando atingiu 200 |
| `SdkTrafficGateway.recentTraffic(applicationId, since)` | `SdkTrafficGatewayCollect` + `SurveySdkTrafficJpaRepository` | `sdk_version_daily_usage` (`010`) |

## 8. Coleta (reflexos)

- `AnswerStatus` ganha `NOT_APPLICABLE`; `SubmissionValidation` aceita só em pergunta com condição
  (`answer.not_applicable_unconditional` caso contrário) e não cobra obrigatoriedade dela.
- `PublishedSurveyCatalog.DeliverableQuestion` carrega `condition` e rótulos;
  `DeliverableSurvey` carrega o aviso. `RequiredSdk` soma `CONDITIONAL_DISPLAY`, `SCALE_LABELS` e
  `FREE_TEXT_NOTICE`.

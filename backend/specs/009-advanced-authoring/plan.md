# Implementation Plan: Autoria avançada — modelos, condição, duplicação e publicação consciente

**Branch**: `009-advanced-authoring` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-advanced-authoring/spec.md`

## Summary

Tudo acontece no módulo `survey`, com reflexos pontuais em `collect` (entrega da condição e do
aviso; status não aplicável) e em `core/catalog` (recursos do SDK). Movimentos:

1. **Modelos como conteúdo de domínio** (D-01): `SurveyTemplate` e `TemplateQuestions` produzem o
   `Question.Draft` oficial; a pesquisa guarda `template_kind`.
2. **Condição em duas camadas** (D-02, D-03): `DisplayCondition` valida a forma sozinha;
   `QuestionConditions` valida contra a versão (origem existe, vem antes, tipo compatível, valores
   dentro das opções/escala) e protege dependentes em remoção, alteração e reordenação. Recusa é
   `ConditionRejected` (422) com `field` e `questionKey` nas extensões.
3. **Condição pela chave estável** (D-04): colunas `condition_*` em `questions` com FK composta
   deferível `(version_id, condition_source_key) → (version_id, question_key)` e tabela filha
   `question_condition_values`.
4. **Duplicação** (D-05): `Survey.duplicateInto` + `SurveyVersion.duplicateFor` (chaves novas,
   condição reapontada, janela encerrada reaberta), num `@Transactional`.
5. **Aviso de texto livre** (D-06): `FreeTextNotice` na pesquisa, editado pelo mesmo `PATCH`.
6. **Classificação que enxerga condição e faixa** (D-07): `ChangeClassification.between` com seis
   tipos de diferença; `CosmeticDeclarationRefused` devolve `differences`.
7. **Avisos de publicação** (D-08): `CheckPublicationWarningsUseCase` reúne `SegmentationReach`,
   concorrência (`SurveyRepository.findLiveListeningTo`) e `CompatibilityReach`, por três portas de
   leitura sobre `collect`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1, springdoc-openapi, Jackson 3, Lombok. **Nenhuma nova.**

**Storage**: PostgreSQL + Flyway. `V20260912130759__advanced_authoring.sql` (modelo, rótulos,
condição). As colunas do aviso de texto livre (`free_text_notice_enabled`,
`free_text_notice_text`) vieram em `V20260912160000__privacy_retention.sql`, junto da fatia de
privacidade.

**Testing**: domínio puro (`DisplayConditionTest`, `SurveyVersionConditionsTest`,
`SurveyVersionDuplicateTest`, `SurveyNameCopyTest`, `TemplateQuestionsTest`, `FreeTextNoticeTest`,
`ChangeClassificationConditionTest`, `ComparabilityGroupTest`, `SegmentationReachTest`,
`CompatibilityReachTest`); casos de uso sobre fakes; E2E `AdvancedAuthoringE2ETest`,
`PublicationWarningsE2ETest`, `FreeTextNoticeE2ETest`, `ConditionalCollectE2ETest`.

**Target Platform / Project Type**: Linux server, JVM UTC · web service

**Performance Goals**: sem SLO. Avisos: uma consulta ao catálogo de atributos (só os nomes usados
nas regras), uma às pesquisas concorrentes, uma ao tráfego recente.

**Constraints**: condição nunca aponta para pergunta inexistente — garantido no domínio e por FK;
duplicação atômica; nenhuma regra nova bloqueia publicação além das já existentes.

**Scale/Scope**: 2 rotas novas (`duplicate`, `publication-warnings`), extensão de 4 (`POST
surveys`, `PATCH surveys`, perguntas, `publication`), 3 portas novas em `survey`
(`AttributeCatalogGateway`, `SdkTrafficGateway`, já existente `ApplicationScopeGateway`), 1
migration.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | Modelos, condição, classificação, alcance e compatibilidade são Java puro em `survey/domain`. `survey` lê o catálogo de atributos e o tráfego de SDK por portas (`AttributeCatalogGateway`, `SdkTrafficGateway`) com adaptadores em `survey/infra/gateways`. | ✅ |
| **II. Domínio Que Se Valida** | `DisplayCondition`, `ScaleLabels`, `FreeTextNotice`, `SurveyName.copy()` validam no construtor; a coerência com a versão é validada por `SurveyVersion` via `QuestionConditions`. | ✅ |
| **III. Teste É Contrato** | Cada regra de condição, de cópia e de classificação tem teste de domínio; cada rota nova tem E2E com estado do banco e cada erro do Swagger. | ✅ |
| **IV. Consistência da Superfície Pública** | Recusa de condição com `code` + extensões `field`/`questionKey`; recusa cosmética com `differences`; duplicação responde 201 com `Location` da aplicação de destino. | ✅ |
| **V. Performance Por Construção** | Valores da condição em tabela filha com PK `(question_id, position)`; FK composta usa a unicidade `(version_id, question_key)` já existente; avisos sem consulta em laço. | ✅ |

**Re-avaliação após Phase 1**: aprovado. A FK deferível é a única concessão: a versão é regravada
inteira, e a ordem das linhas no flush não importa.

## Project Structure

### Documentation (this feature)

```text
specs/009-advanced-authoring/
├── plan.md · research.md · data-model.md · quickstart.md · tasks.md
├── contracts/advanced-authoring.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/catalog/SdkFeature.java, SdkCapabilities.java                 (CONDITIONAL_DISPLAY, SCALE_LABELS, FREE_TEXT_NOTICE)
└── modules/
    ├── survey/
    │   ├── domain/
    │   │   ├── valueobjects/SurveyTemplate.java, DisplayCondition.java, ConditionOperator.java,
    │   │   │                ConditionRejected.java, ScaleLabels.java, FreeTextNotice.java,
    │   │   │                SurveyName.java (copy)
    │   │   ├── templates/TemplateQuestions.java
    │   │   ├── entities/Question.java, QuestionConditions.java, Survey.java, SurveyVersion.java
    │   │   └── publication/ChangeClassification.java, PublicationImpediment.java,
    │   │                   PublicationWarning.java, CompetingSurvey.java, KnownAttribute.java,
    │   │                   SegmentationReach.java, CompatibilityReach.java, SdkTraffic.java
    │   ├── application/
    │   │   ├── errors/CosmeticDeclarationRefused.java, PublishedSurveyCannotBeDiscarded.java,
    │   │   │          SurveyVersionHasNoChanges.java
    │   │   ├── gateways/AttributeCatalogGateway.java, SdkTrafficGateway.java
    │   │   ├── outputs/ConditionOutput.java, PublicationWarningOutput.java, CompetingSurveyOutput.java
    │   │   ├── services/QuestionDrafts.java
    │   │   └── usecases/CreateSurveyUseCase.java, DuplicateSurveyUseCase.java,
    │   │                DiscardSurveyUseCase.java, UpdateSurveyUseCase.java,
    │   │                PublishSurveyUseCase.java, CheckPublicationWarningsUseCase.java,
    │   │                AddQuestionUseCase.java, UpdateQuestionUseCase.java,
    │   │                RemoveQuestionUseCase.java, ReorderQuestionsUseCase.java
    │   └── infra/
    │       ├── gateways/AttributeCatalogGatewayCollect.java, SurveyAttributeCatalogJpaRepository.java,
    │       │            SdkTrafficGatewayCollect.java, SurveySdkTrafficJpaRepository.java
    │       ├── database/jpa/entities/QuestionJpaEntity.java, SurveyJpaEntity.java
    │       ├── database/jpa/mappers/QuestionJpaMapper.java, SurveyJpaMapper.java
    │       └── http/
    │           ├── controllers/SurveyController.java, SurveyControllerSwagger.java,
    │           │               QuestionController.java, QuestionControllerSwagger.java
    │           ├── dtos/CreateSurveyRequestDTO.java, DuplicateSurveyRequestDTO.java,
    │           │        ConditionDTO.java, ScaleRangeDTO.java, AddQuestionRequestDTO.java,
    │           │        UpdateQuestionRequestDTO.java, QuestionResponseDTO.java,
    │           │        UpdateSurveyRequestDTO.java, UpdateSurveyRequestDeserializer.java,
    │           │        SurveyResponseDTO.java, PublishSurveyRequestDTO.java,
    │           │        PublicationWarningDTO.java, PublicationWarningsResponseDTO.java,
    │           │        CompetingSurveyDTO.java
    │           └── presenters/PublicationWarningsPresenter.java, QuestionPresenter.java,
    │                          SurveyPresenter.java
    └── collect/
        ├── domain/collection/SubmissionValidation.java, SubmissionProblem.java   (NOT_APPLICABLE)
        ├── domain/entities/AnswerStatus.java
        ├── application/gateways/PublishedSurveyCatalog.java      (DeliverableCondition, aviso)
        ├── application/services/RequiredSdk.java
        └── infra/http/dtos/EligibilityResponseDTO.java, AnswerDTO.java

src/main/resources/db/migration/V20260912130759__advanced_authoring.sql
```

**Structure Decision**: tudo cabe nos módulos existentes. `domain/templates` e
`domain/publication` são subpacotes de `survey/domain` para regra de saída e de conteúdo pronto.

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| FK composta deferível na condição | Garante no banco que a origem é da mesma versão, mesmo com a versão regravada inteira (D-04). | Só a validação de domínio — um bug de mapper gravaria condição órfã. |

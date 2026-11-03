# Implementation Plan: Controle de exposição — prioridade, cota, descanso e catálogo observado

**Branch**: `008-exposure-control` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-exposure-control/spec.md`

## Summary

A exposição é um `record` de domínio da pesquisa (`Exposure`), gravado em três colunas novas de
`surveys`. A elegibilidade passa a usar dois campos novos da candidata (`priority`,
`ignoresQuietPeriod`) e o intervalo da aplicação. O encerramento por cota atravessa de `collect`
para `survey` por uma porta (`SurveyQuotaGateway`) que chama o caso de uso da autoria dentro da
transação da conclusão. Os catálogos ganham leitura e o de atributos nasce.

Movimentos principais:

1. **`Exposure` como value object** (D-01) com `withPriority`, `withResponseQuota`,
   `withoutResponseQuota`, `ignoringQuietPeriod`; editada pelo `PATCH` de pesquisa que já existia,
   com `Patch<Integer>` para a cota.
2. **Desempate e descanso na elegibilidade** (D-02, D-03): `tiebreak()` = prioridade desc,
   `publishedAt` asc, `surveyId`; `isResting` só consulta o banco quando pode barrar alguém.
3. **Cota sob trava** (D-04, D-05): leitura sem trava filtra o caso sem cota; com cota, releitura
   `select … for update` da linha da pesquisa serializa conclusões; `QuotaClosure` usa update
   condicional (`endIfLive`) para gravar uma transição só.
4. **Cota reduzida** (D-06): `UpdateSurveyUseCase` trava a pesquisa e, se a cota definida nesta
   chamada já foi alcançada, fecha pela mesma `QuotaClosure`.
5. **Catálogo de atributos** (D-08): upsert em lote a partir de um JSON, limite de 200 valores no
   próprio `insert`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Lombok. **Nenhuma dependência nova.**

**Storage**: PostgreSQL + Flyway. Migration `V20260912123029__exposure_control.sql`: três colunas
em `surveys` com `check`, índice parcial `idx_survey_displays_completed`, tabelas
`application_attributes` e `application_attribute_values`.

**Testing**: domínio em JUnit puro (`ExposureTest`, `SurveyExposureTest`, `QuietPeriodTest`);
casos de uso sobre fakes (`InMemorySurveyQuotaGateway`, `InMemoryCompletedResponsesGateway`,
`InMemoryObservedAttributeRepository`, `InMemoryCollectApplicationScopeGateway`); E2E
`ExposureControlE2ETest`, `SurveyExposureE2ETest`, `ListObservedEventsE2ETest`,
`ListObservedAttributesE2ETest`, incluindo corridas reais de conclusão.

**Target Platform**: Linux server, JVM UTC

**Project Type**: web service

**Performance Goals**: sem SLO. Elegibilidade sem cota continua com o mesmo número de consultas;
o descanso custa uma consulta (`lastOpenedAt`) só quando pode barrar; a contagem de concluídas
usa índice parcial; o catálogo de atributos custa dois comandos por consulta, quantos atributos
vierem.

**Constraints**: um encerramento por cota por pesquisa, mesmo sob concorrência; falha de catálogo
nunca vira erro ao SDK; nenhum atributo nem valor de atributo em log.

**Scale/Scope**: 4 rotas novas (`quota-progress`, `events`, `attributes`, e a extensão do `PATCH`
de pesquisa), 4 casos de uso novos (`GetQuotaProgressUseCase`, `ListObservedEventsUseCase`,
`ListObservedAttributesUseCase`, `EndSurveyByQuotaUseCase`), 3 portas novas
(`SurveyQuotaGateway`, `CompletedResponsesGateway`, `ObservedAttributeRepository`), 1 migration.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | `Exposure`, `QuietPeriod`, `ObservedAttribute` são Java puro. `collect` não conhece `survey`: `SurveyQuotaGateway` (porta em `collect`) é implementada por `SurveyQuotaGatewaySurvey`, que chama `EndSurveyByQuotaUseCase`; `survey` lê concluídas por `CompletedResponsesGateway` implementada em `survey/infra/gateways`. | ✅ |
| **II. Domínio Que Se Valida** | `Exposure` valida faixa e cota (`survey.priority_invalid`, `survey.response_quota_invalid`); `QuietPeriod` recusa intervalo não positivo; `Survey.endByQuota()` decide sozinho se encerra. | ✅ |
| **III. Teste É Contrato** | Teste de domínio para cada invariante; caso de uso sobre fake; E2E com corrida real de duas conclusões e de pausa + conclusão. | ✅ |
| **IV. Consistência da Superfície Pública** | Rotas novas sob `/applications/**` com Swagger próprio (`QuotaProgressSwagger`, `ObservedEventSwagger`, `ObservedAttributeSwagger`) declarando 200/400/403/404; `PageResponseDTO` nas listagens. | ✅ |
| **V. Performance Por Construção** | Índice parcial para a contagem; índices de listagem dos catálogos na mesma migration; catálogo de atributos em dois comandos nativos; nenhuma consulta em laço. | ✅ |

### Re-avaliação após Phase 1 (design)

O encerramento por cota cruza módulos dentro de uma transação. A alternativa (evento assíncrono)
deixaria uma janela em que a pesquisa continua entregue depois de atingida. A travessia é por
porta e caso de uso, e o `@Transactional` do projeto em `EndSurveyByQuotaUseCase` se junta à
transação aberta da conclusão. **Aprovado.**

## Project Structure

### Documentation (this feature)

```text
specs/008-exposure-control/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── exposure.md
│   └── observed-catalog.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/modules/
├── survey/
│   ├── domain/valueobjects/Exposure.java                        novo
│   ├── domain/entities/Survey.java                              alterado (exposure, endByQuota)
│   ├── domain/entities/TransitionReason.java                    alterado (+ QUOTA_REACHED)
│   ├── application/gateways/CompletedResponsesGateway.java      novo
│   ├── application/repositories/SurveyRepository.java           alterado (lock, endIfLive)
│   ├── application/services/QuotaClosure.java                   novo
│   ├── application/usecases/EndSurveyByQuotaUseCase.java        novo
│   ├── application/usecases/UpdateSurveyUseCase.java            alterado
│   ├── infra/gateways/CompletedResponsesGatewayCollect.java     novo
│   ├── infra/gateways/SurveyCompletedResponsesJpaRepository.java novo
│   ├── infra/database/jpa/entities/SurveyJpaEntity.java         alterado
│   ├── infra/database/jpa/mappers/SurveyJpaMapper.java          alterado
│   ├── infra/database/jpa/repositories/SurveyJpaRepository.java, SurveyRepositoryJpa.java  alterados
│   └── infra/http/dtos/UpdateSurveyRequestDTO.java, UpdateSurveyRequestDeserializer.java,
│                       SurveyResponseDTO.java                    alterados
└── collect/
    ├── domain/eligibility/QuietPeriod.java                      novo
    ├── domain/entities/ObservedAttribute.java, ObservedAttributeId.java   novos
    ├── application/gateways/ApplicationScopeGateway.java        alterado (+ quietPeriodDaysOf)
    ├── application/gateways/PublishedSurveyCatalog.java         alterado (SurveyCandidate)
    ├── application/gateways/SurveyQuotaGateway.java             novo
    ├── application/repositories/ObservedAttributeRepository.java novo
    ├── application/repositories/SurveyDisplayRepository.java    alterado (lastOpenedAt, countCompleted)
    ├── application/outputs/ObservedEventOutput.java, ObservedAttributeOutput.java  novos
    ├── application/usecases/FindEligibleSurveyUseCase.java      alterado
    ├── application/usecases/SubmitSurveyDisplayUseCase.java     alterado (closeOnQuota)
    ├── application/usecases/GetQuotaProgressUseCase.java        novo
    ├── application/usecases/ListObservedEventsUseCase.java      novo
    ├── application/usecases/ListObservedAttributesUseCase.java  novo
    ├── infra/gateways/SurveyQuotaGatewaySurvey.java             novo
    ├── infra/gateways/SurveyScopeJpaRepository.java             alterado (quota, lock)
    ├── infra/gateways/PublishedSurveyCatalogSurvey.java, PublishedSurveyJpaRepository.java  alterados
    ├── infra/gateways/ApplicationScopeGatewayApp.java           alterado
    ├── infra/database/jpa/entities/ApplicationAttributeJpaEntity.java,
    │                               ApplicationAttributeValueJpaEntity.java   novos
    ├── infra/database/jpa/repositories/ApplicationAttributeJpaRepository.java,
    │       ApplicationAttributeValueJpaRepository.java, ObservedAttributeRepositoryJpa.java  novos
    ├── infra/config/UseCasesConfiguration.java                  alterado
    └── infra/http/
        ├── controllers/QuotaProgressController.java, QuotaProgressSwagger.java,
        │               ObservedEventController.java, ObservedEventSwagger.java,
        │               ObservedAttributeController.java, ObservedAttributeSwagger.java   novos
        ├── dtos/QuotaProgressResponseDTO.java, ListObservedEventsQueryDTO.java,
        │        ObservedEventResponseDTO.java, ListObservedAttributesQueryDTO.java,
        │        ObservedAttributeResponseDTO.java                novos
        └── presenters/QuotaProgressPresenter.java, ObservedEventPresenter.java,
                       ObservedAttributePresenter.java            novos

src/main/resources/db/migration/V20260912123029__exposure_control.sql   novo
```

**Structure Decision**: sem módulo novo. O progresso da cota mora em `collect` porque contar
concluídas é dela; o encerramento mora em `survey` porque registrar a transição é regra da autoria.

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| Trava pessimista da linha da pesquisa na conclusão com cota | Duas conclusões simultâneas precisam enxergar uma à outra na contagem (FR-013). | Contar sem trava — encerramento duplo ou perdido sob concorrência. |

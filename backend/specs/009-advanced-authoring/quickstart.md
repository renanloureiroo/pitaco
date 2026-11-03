# Quickstart — validar `009-advanced-authoring`

Contrato em [`contracts/advanced-authoring.md`](./contracts/advanced-authoring.md).

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# domínio
./mvnw test -Dtest='TemplateQuestionsTest,DisplayConditionTest,SurveyVersionConditionsTest,SurveyVersionDuplicateTest,SurveyNameCopyTest,FreeTextNoticeTest,ChangeClassificationConditionTest,ChangeClassificationTest,ComparabilityGroupTest,SegmentationReachTest,CompatibilityReachTest,PublicationImpedimentsTest,SubmissionValidationNotApplicableTest'

# casos de uso e DTOs
./mvnw test -Dtest='CreateSurveyFromTemplateUseCaseTest,DuplicateSurveyUseCaseTest,DiscardSurveyUseCaseTest,UpdateSurveyFreeTextNoticeTest,CheckPublicationWarningsUseCaseTest,PublicationUseCasesTest,AdvancedAuthoringRequestDTOsTest,UpdateSurveyNoticeRequestDTOTest'

# E2E
./mvnw test -Dtest='AdvancedAuthoringE2ETest,PublicationWarningsE2ETest,FreeTextNoticeE2ETest,ConditionalCollectE2ETest,PublishSurveyE2ETest'
```

## 3. Roteiro manual

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `POST …/surveys` com `{"name":"NPS","template":"nps"}` e `GET` do detalhe | uma pergunta NPS 0–10 com os dois rótulos, `templateKind: "nps"` |
| 2 | acrescentar texto livre condicionado a `between 0 e 6` na NPS | `201`, condição no detalhe |
| 3 | acrescentar condição `between` sobre uma pergunta de escolha | `422 question.condition_operator_invalid`, `field: condition.operator` |
| 4 | remover a pergunta NPS | `422 question.condition_source_in_use` com `questionKey` da dependente |
| 5 | definir disparo, publicar e consultar `POST /collect/eligibility` | a pergunta condicionada chega com `condition`; `freeTextNotice.enabled: true` com texto padrão |
| 6 | enviar a submissão com a condicionada como `NOT_APPLICABLE` | `204` |
| 7 | abrir versão, mudar só o rótulo e publicar `cosmetic` | `201`, mesmo grupo |
| 8 | abrir versão, mudar a condição e publicar `cosmetic` | `422 survey_version.cosmetic_refused` com `condition_changed` |
| 9 | `POST …/duplicate` com outra aplicação de destino | `201`, `Location` na aplicação de destino, rascunho com chaves novas |
| 10 | `PATCH …` com `{"freeTextNoticeText":null}` | volta ao texto padrão |
| 11 | `GET …/publication-warnings` com outra pesquisa no ar no mesmo evento | `trigger.competing_surveys` com nome e prioridade |
| 12 | `DELETE …` da pesquisa publicada | `422 survey.published_cannot_be_discarded` |

## 4. O que precisa estar verde

- [X] Toda regra de condição com teste de domínio e a recusa apontando o campo
- [X] Cópia com chaves novas, condição reapontada e janela encerrada reaberta
- [X] Classificação cosmética recusando condição e faixa, aceitando rótulo
- [X] Avisos de concorrência, alcance e compatibilidade sem bloquear a publicação
- [X] Não aplicável aceito só em pergunta condicionada

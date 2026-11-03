# Quickstart — validar `008-exposure-control`

Contratos em [`contracts/exposure.md`](./contracts/exposure.md) e
[`contracts/observed-catalog.md`](./contracts/observed-catalog.md).

## Pré-requisitos

Java 21, wrapper Maven, Docker de pé.

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# domínio
./mvnw test -Dtest='ExposureTest,SurveyExposureTest,QuietPeriodTest'

# casos de uso
./mvnw test -Dtest='UpdateSurveyUseCaseTest,EndSurveyByQuotaUseCaseTest,GetQuotaProgressUseCaseTest,FindEligibleSurveyUseCaseTest,ListObservedEventsUseCaseTest,ListObservedAttributesUseCaseTest'

# E2E, inclusive as corridas de conclusão
./mvnw test -Dtest='SurveyExposureE2ETest,ExposureControlE2ETest,ListObservedEventsE2ETest,ListObservedAttributesE2ETest'
```

## 3. A migration aplicou

```sql
select column_name, data_type, column_default
  from information_schema.columns
 where table_name = 'surveys'
   and column_name in ('priority', 'response_quota', 'ignores_quiet_period');

select indexname from pg_indexes
 where indexname in ('idx_survey_displays_completed', 'idx_application_attributes_listing');
```

## 4. Roteiro manual

Pré-condição: aplicação com chave, `quietPeriodDays: 15`, e duas pesquisas A e B publicadas no
evento `checkout.completed`, A publicada primeiro.

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `POST /collect/eligibility` (respondente novo) | recebe A (publicada há mais tempo) |
| 2 | `PATCH …/surveys/{B}` com `{"priority":10}` e repetir 1 com outro respondente | recebe B; repetir devolve sempre B |
| 3 | abrir exibição de B para o respondente R e consultar um evento de uma terceira pesquisa C | silêncio (descanso) |
| 4 | `PATCH …/surveys/{C}` com `{"ignoresQuietPeriod":true}` e repetir 3 | recebe C |
| 5 | `PATCH …/surveys/{A}` com `{"responseQuota":2}`; concluir duas exibições de A | A encerrada; `GET …/transitions` traz `quota_reached` |
| 6 | `GET …/surveys/{A}/quota-progress` | `{"responseQuota":2,"completedResponses":2}` |
| 7 | `PATCH …/surveys/{B}` com `{"responseQuota":null}` | cota some; demais campos intactos |
| 8 | `PATCH …/surveys/{B}` com `{"priority":101}` | `400`, nada muda |
| 9 | consultar a elegibilidade com `"attributes":{"plano":"pro","pais":"BR"}` e `GET /applications/{id}/attributes` | `plano` e `pais`, cada um com o valor |
| 10 | `GET /applications/{id}/events?size=101` | `400` |
| 11 | qualquer leitura com `X-Pitaco-Key` | `403 api_key.forbidden_surface` |

## 5. O que precisa estar verde

- [X] Corrida de duas conclusões que atingem a cota: uma transição só
- [X] Corrida de pausa manual + conclusão: termina encerrada
- [X] Cota reduzida até o concluído encerra na hora; acima, só grava
- [X] Descanso entre pesquisas diferentes e isenção furando
- [X] Prioridade maior vence mesmo publicada depois, de forma estável
- [X] Catálogo de atributos com limite de 200 valores e amortização

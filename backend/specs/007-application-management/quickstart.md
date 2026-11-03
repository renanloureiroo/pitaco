# Quickstart — validar `007-application-management`

Como provar que a fatia funciona. Corpos e códigos estão em
[`contracts/application-management.md`](./contracts/application-management.md).

## Pré-requisitos

- Java 21 e o wrapper Maven do repositório
- Docker de pé (Testcontainers sobe Postgres e a stack LGTM nos E2E)

## 1. Suíte completa — o portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# domínio
./mvnw test -Dtest='ApplicationTest,SlugTest,NameTest,ObservedEventTest'

# casos de uso e DTOs
./mvnw test -Dtest='CreateApplicationUseCaseTest,UpdateApplicationUseCaseTest,ApplicationStatusUseCasesTest,CreateApplicationRequestDTOTest,UpdateApplicationRequestDTOTest,FindEligibleSurveyUseCaseTest'

# E2E
./mvnw test -Dtest='CreateApplicationE2ETest,UpdateApplicationE2ETest,ApplicationStatusE2ETest,CollectEligibilityE2ETest'
```

## 3. Roteiro manual

API em `http://localhost:8080/api` (`./mvnw spring-boot:run`).

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `POST /applications` com `{"name":"Pitaço Café"}` | `201`, `slug: "pitaco-cafe"`, `Location` com o id |
| 2 | o mesmo de novo | `409 application.already_exists_with_same_slug` |
| 3 | `POST /applications` com `{"name":"x","retentionDays":30,"openTextRetentionDays":60}` | `422 application.open_text_retention_invalid` |
| 4 | `PATCH /applications/{id}` com `{"quietPeriodDays":15,"retentionDays":180,"openTextRetentionDays":30}` | `200`, os três prazos na resposta |
| 5 | `PATCH` com `{"quietPeriodDays":null}` | `200`, `quietPeriodDays` some da resposta; os outros ficam |
| 6 | `PATCH` com `{}` | `200`, `updatedAt` igual ao do passo 5 |
| 7 | `PATCH` com `{"name":null}` | `400 request.invalid` |
| 8 | `PATCH` com `{"retentionDays":10}` (texto livre em 30) | `422 application.retention_invalid`, nada muda |
| 9 | `POST /applications/{id}/deactivate` duas vezes | `200` `inactive` nas duas; `updatedAt` não anda na segunda |
| 10 | com uma chave emitida antes, `POST /collect/eligibility` | `200` sem `survey` |
| 11 | `POST /applications/{id}/activate` e repetir o passo 10 com pesquisa publicada | a pesquisa volta |
| 12 | qualquer rota acima com `X-Pitaco-Key` | `403 api_key.forbidden_surface` |

### Catálogo de eventos

Com a aplicação ativa, consulte a elegibilidade duas vezes com `"event":"teste.catalogo"` e confira
no banco:

```sql
select name, first_seen_at, last_seen_at
  from application_events
 where application_id = '<id>';
```

Esperado: uma linha só para `teste.catalogo`. Com a aplicação inativa, a mesma consulta não cria
linha nova.

## 4. O que precisa estar verde

- [X] `./mvnw verify` verde
- [X] Cada invariante de prazo com teste de domínio, incluindo "edição recusada não mexe no
      `updatedAt`"
- [X] E2E de cada rota com estado lido do banco, `400`, `404`, `409`/`422` quando aplicável e `403`
      da chave
- [X] E2E provando silêncio da aplicação inativa e retorno ao reativar
- [X] E2E provando uma linha por evento no catálogo e nenhuma linha para aplicação inativa

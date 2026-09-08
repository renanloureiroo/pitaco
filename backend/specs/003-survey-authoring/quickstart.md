# Quickstart — validando a autoria de pesquisa

**Feature**: `003-survey-authoring`

Como provar, de ponta a ponta, que a fatia funciona. Os tipos estão em
[data-model.md](./data-model.md); o contrato HTTP, em
[contracts/surveys.openapi.yaml](./contracts/surveys.openapi.yaml); o porquê de cada escolha, em
[research.md](./research.md).

## Pré-requisitos

- Java 21 e Docker de pé (Testcontainers e o `docker-compose` do `spring-boot-docker-compose`).
- Nada mais: Postgres, Redis e a stack LGTM sobem junto com a aplicação.

## Portão automatizado

```bash
./mvnw verify
```

Verde, sem teste ignorado. É o portão obrigatório da constituição — nada desta fatia conta como
pronto antes dele.

Recortes úteis durante o desenvolvimento:

```bash
# Domínio — JUnit puro, sem Docker
./mvnw test -Dtest='SurveyTest,SurveyVersionTest,QuestionTest,SurveyNameTest,QuestionStatementTest'
./mvnw test -Dtest='TriggerTest,EventNameTest,TriggerWindowTest,SamplingRateTest,SegmentationRuleTest'
./mvnw test -Dtest='PublicationImpedimentsTest,ChangeClassificationTest,SurveyStateDerivationTest'

# Casos de uso — sobre os fakes in-memory, sem Docker
./mvnw test -Dtest='*UseCaseTest'

# DTOs de entrada — constraints espelhando os value objects
./mvnw test -Dtest='*RequestDTOTest,*QueryDTOTest'

# E2E — precisa de Docker
./mvnw test -Dtest='*SurveyE2ETest,*QuestionE2ETest,*TriggerE2ETest,*VersionE2ETest'
```

Sem Docker, exclua as classes que sobem contexto:

```bash
./mvnw test -Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'
```

## Validação manual

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080/api` · Swagger em `http://localhost:8080/api/swagger-ui.html`.

O roteiro abaixo é o SC-001 executado à mão: uma pesquisa do nada até o ar, sem tocar no banco.

### 1. Uma aplicação ativa para pendurar a pesquisa

```bash
APP=$(curl -sS -X POST http://localhost:8080/api/applications \
  -H 'content-type: application/json' \
  -d '{"name":"Loja","slug":"loja"}' | jq -r .id)
```

### 2. A pesquisa nasce em rascunho

```bash
SURVEY=$(curl -sS -X POST http://localhost:8080/api/applications/$APP/surveys \
  -H 'content-type: application/json' \
  -d '{"name":"NPS pós-checkout"}' | jq -r .id)
```

Esperado: `201`, header `Location`, `state: "draft"`, `draftVersionNumber: 1`,
`publishedVersionNumber: null`.

### 3. As perguntas

```bash
BASE=http://localhost:8080/api/applications/$APP/surveys/$SURVEY

curl -sS -X POST $BASE/questions -H 'content-type: application/json' -d '{
  "statement":"O quanto você recomendaria a loja?","type":"nps","required":true
}' | jq '{id, key, position}'

curl -sS -X POST $BASE/questions -H 'content-type: application/json' -d '{
  "statement":"O que mais pesou na sua nota?","type":"single_choice","required":false,
  "options":[{"label":"Preço","value":"price"},{"label":"Entrega","value":"delivery"}]
}' | jq '{id, key, position}'
```

Esperado: `201` em cada uma, `position` 1 e 2, `key` distintas. Guarde as duas `key` — a
permanência delas é o que a etapa 8 verifica.

**Recusas que devem acontecer** (todas `400`, com o campo apontado):

```bash
curl -sS -X POST $BASE/questions -H 'content-type: application/json' \
  -d '{"statement":"","type":"free_text","required":false}'                       # enunciado vazio
curl -sS -X POST $BASE/questions -H 'content-type: application/json' \
  -d '{"statement":"Comentário","type":"free_text","required":false,
       "options":[{"label":"a","value":"a"}]}'                                    # tipo não aceita opção
curl -sS -X POST $BASE/questions -H 'content-type: application/json' \
  -d '{"statement":"Nota","type":"scale","required":false,"range":{"min":5,"max":5}}'  # faixa de tamanho zero
```

**E uma que deve ser aceita**: pergunta de escolha **sem** opção. O rascunho aceita incompleto —
a pendência aparece na etapa 5.

### 4. O disparo e as regras

```bash
curl -sS -X PUT $BASE/trigger -H 'content-type: application/json' -d '{
  "eventName":"checkout.completed",
  "windowStart":"2026-09-01T00:00:00Z",
  "samplingRate":0.25
}' | jq

curl -sS -X POST $BASE/trigger/rules -H 'content-type: application/json' \
  -d '{"attribute":"plan","operation":"equals","value":"pro"}' | jq
```

Recusas esperadas: janela com fim ≤ início (`400`), proporção acima de 1 (`400`), regra
`present` carregando valor (`400`), regra `equals` sem valor (`400`).

### 5. Os impedimentos, antes de tentar publicar

```bash
curl -sS $BASE/publication-impediments | jq
```

Com uma pergunta de escolha sem opção, esperado é a lista trazer
`question.options_missing` com a `questionKey` dela. Corrija a pergunta e repita: a lista deve
ficar vazia. **Esta é a mesma lista que a publicação usa** — é o cenário 9 de US4.

### 6. Publicar

```bash
curl -sS -i -X POST $BASE/publication | jq
```

Esperado: `201`, `number: 1`, `publishedAt` preenchido, `changeKind: null`,
`comparabilityGroup: 1`. Com a janela já aberta, `GET $BASE` devolve `state: "active"`; com
`windowStart` no futuro, `"scheduled"`.

Tentando de novo: `409 survey.already_published`. Tentando `DELETE $BASE`:
`422 survey.published_cannot_be_discarded`.

Tentando escrever conteúdo agora — acrescentar pergunta, mudar o disparo, remover regra —:
`422 survey.content_frozen` em todas. É a verificação manual de SC-003.

### 7. Controlar o que está no ar

```bash
curl -sS -X POST $BASE/pause  | jq .state     # paused
curl -sS -X POST $BASE/resume | jq .state     # active (ou scheduled)
curl -sS -X POST $BASE/end    | jq .state     # ended
curl -sS -X POST $BASE/resume                 # 422 survey.transition_not_allowed
curl -sS $BASE/transitions | jq
```

O histórico deve trazer a publicação e as três transições manuais com instante e motivo, mais
`window_opened` se a janela já tiver aberto.

> Para seguir para a etapa 8, refaça o roteiro sem encerrar a pesquisa.

### 8. Nova versão, com classificação

```bash
curl -sS -X POST $BASE/versions | jq '.number, .status'      # 2, draft
```

A cópia deve trazer as perguntas com as **mesmas** `key` da versão 1 e `id` novos, mais o disparo
e as regras. Corrija um enunciado e publique como cosmética:

```bash
curl -sS -X POST $BASE/publication -H 'content-type: application/json' \
  -d '{"changeKind":"cosmetic","changeSummary":"Correção de acentuação"}' | jq
```

Esperado: `201`, `number: 2`, `changeKind: "cosmetic"`, `comparabilityGroup: 1` — o mesmo da v1.
`GET $BASE/versions/1` deve continuar devolvendo o texto **original**.

Agora as quatro recusas de SC-007 — abra uma v3 e, em cada tentativa, declare `cosmetic`:

| Mudança na v3 | Esperado |
| --- | --- |
| pergunta acrescentada | `422 survey_version.cosmetic_refused`, `question_added` |
| pergunta removida | `422`, `question_removed` |
| tipo de pergunta trocado | `422`, `type_changed` |
| conjunto de opções alterado | `422`, `options_changed` |

Cada uma delas passa se declarada `semantic` — e aí `comparabilityGroup` vira 2.

Duas verificações finais:

```bash
curl -sS -X POST $BASE/versions                    # 409 survey_version.draft_already_open
curl -sS $BASE/versions/comparability | jq         # {"groups":[{"group":1,"versions":[1,2]},...]}
```

E publicar um rascunho de versão sem nenhuma alteração: `422 survey_version.no_changes`.

## Isolamento entre aplicações

O critério SC-004 se verifica com duas aplicações:

```bash
OTHER=$(curl -sS -X POST http://localhost:8080/api/applications \
  -H 'content-type: application/json' -d '{"name":"Outra","slug":"outra"}' | jq -r .id)

curl -sS -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/api/applications/$OTHER/surveys/$SURVEY        # 404
curl -sS http://localhost:8080/api/applications/$OTHER/surveys | jq '.total'   # 0
```

`404`, nunca `403` — e o corpo traz `survey.not_found`, o mesmo de um identificador inventado.

## O que o E2E cobre além disto

O roteiro manual demonstra; o E2E é o que garante. Por endpoint, o mínimo da constituição:
caminho feliz com status, corpo, `Location` **e o estado lido de volta do banco**; `400` de
validação; cada erro anunciado no OpenAPI; JSON malformado; e a verificação de que o estado
**não** mudou nos caminhos de falha.

Acrescentam-se os que são desta feature:

- publicar com 20 perguntas e ler as 20 de volta na ordem, idênticas (SC-005);
- reordenar e conferir que as posições ficam consecutivas e sem repetição, inclusive depois de
  remover a do meio (FR-014);
- tentar as seis escritas de conteúdo em pesquisa publicada sem rascunho aberto (SC-003);
- as quatro diferenças que derrubam a declaração de cosmética (SC-007);
- uma pergunta atravessando três versões com a mesma `key` (SC-008);
- travessia de páginas da listagem sem repetir nem omitir.

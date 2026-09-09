# Contrato consumido — API administrativa de coleta

As quatro consultas que esta feature usa, como o painel as enxerga. Todas são `GET`, todas ficam
sob `/applications/{applicationId}/**` — superfície **administrativa**, o que significa que o
cliente HTTP **não** envia cabeçalho de chave de aplicação (o backend recusa com
`403 api_key.forbidden_surface` quem o apresentar ali).

Envelope de página, recusa RFC 9457 e tratamento de erro seguem o
[contrato de 001](../../001-painel-operacao-pesquisas/contracts/backend-api.md).

---

## Mudança requerida no backend

> **Escopo desta entrega.** Ver [R7](../research.md#r7) para justificativa e tamanho.

`GET /applications/{applicationId}/surveys/{surveyId}/displays` filtra hoje por `versionId`
(UUID). O painel não tem acesso a esse identificador — a API de versões só expõe `number`.

| Antes | Depois |
|---|---|
| `versionId` — UUID, validado por regex | `versionNumber` — inteiro, mínimo 1 |

O `versionId` **permanece** nos corpos de resposta. O que muda é apenas o parâmetro de consulta,
que hoje não tem consumidor possível. Recusa esperada para número malformado ou menor que 1:
`400`, apontando o campo — mesmo tratamento dos demais parâmetros de página.

Número de versão que não existe na pesquisa devolve **página vazia**, não `404`: é filtro sem
resultado, não recurso inexistente — a mesma semântica que o filtro de desfecho já tem.

---

## 1. Exibições de uma pesquisa

```
GET /applications/{applicationId}/surveys/{surveyId}/displays
```

**Parâmetros de consulta** (todos opcionais; ausente não restringe)

| Param | Tipo | Regra |
|---|---|---|
| `versionNumber` | inteiro ≥ 1 | *(após a mudança acima)* |
| `outcome` | `STARTED` \| `COMPLETED` \| `DISMISSED` | `ABANDONED` não é oferecido |
| `openedFrom` | instante ISO UTC | limite inferior, **inclusive** |
| `openedTo` | instante ISO UTC | limite superior, **inclusive** |
| `page` | inteiro ≥ 0 | padrão `0` |
| `size` | inteiro 1..100 | padrão `20` |

**Sucesso `200`** — `PageResponse<DisplaySummary>`, ordenado por `openedAt` desc.

```jsonc
{
  "items": [{
    "id": "d1f0a3c8-…",
    "versionId": "8c2b5e14-…",
    "versionNumber": 3,
    "comparabilityGroup": 2,
    "outcome": "COMPLETED",
    "sdkVersion": "1.4.0",        // ausente quando não informada
    "openedAt": "2026-09-08T14:22:31Z",
    "closedAt": "2026-09-08T14:23:07Z"  // ausente ⇔ desfecho não é final
  }],
  "page": 0, "size": 20, "total": 137, "totalPages": 7
}
```

**Recusas**

| Status | Situação | Tratamento no painel |
|---|---|---|
| `400` | período com início após o fim; página ou tamanho fora da faixa | não deve acontecer: o formulário recusa antes ([data-model](../data-model.md#filtros-de-listagem)) e a leitura do `searchParams` ignora valor incoerente |
| `404` | aplicação ou pesquisa inexistente | `notFound()` |

Pesquisa que existe e nunca foi exibida devolve **página vazia**, não `404` — a distinção que o
estado vazio da tela depende ([R6](../research.md#r6)).

---

## 2. Detalhe de uma exibição

```
GET /applications/{applicationId}/displays/{displayId}
```

Rota **plana** sob a aplicação: o identificador de exibição é global e a exibição já determina sua
pesquisa. O painel espelha isso na sua própria rota ([R5](../research.md#r5)).

**Sucesso `200`** — `DisplayDetail`

```jsonc
{
  "id": "d1f0a3c8-…",
  "respondentId": "b5c9e740-…",
  "surveyId": "6e1d9b30-…",
  "versionId": "8c2b5e14-…",
  "versionNumber": 3,
  "comparabilityGroup": 2,
  "outcome": "COMPLETED",
  "sdkVersion": "1.4.0",
  "openedAt": "2026-09-08T14:22:31Z",
  "closedAt": "2026-09-08T14:23:07Z",
  "attributes": { "plano": "pro" },   // pode vir {} — ausência exibível
  "answers": [{
    "questionKey": "3f9a1c72-…",
    "status": "ANSWERED",             // ANSWERED | SKIPPED | EXPIRED
    "text": "o relatório podia exportar em CSV",  // ausente fora de texto livre
    "number": 9,                      // ausente fora de numérica
    "options": []                     // sempre presente; vazio fora de escolha
  }]
}
```

**Invariantes que o painel assume** (e que os testes exercitam):

- `answers` vem **na ordem das perguntas da versão exibida** — é a fonte da ordem na tela.
- `answers` é vazio na exibição aberta e na dispensada.
- O campo preenchido é o que o tipo da pergunta determina; **nenhum** vem preenchido quando a
  situação é `SKIPPED` ou `EXPIRED`.

**Recusas**: `404` para exibição inexistente **ou** pertencente a outra aplicação — as duas
levam à mesma tela de não encontrado, e é assim que deve ser: o painel não confirma existência
fora do escopo da aplicação.

---

## 3. Respondentes de uma aplicação

```
GET /applications/{applicationId}/respondents?page&size
```

**Sucesso `200`** — `PageResponse<Respondent>`

```jsonc
{
  "items": [{
    "id": "b5c9e740-…",
    "identityKind": "APP_REFERENCE",   // APP_REFERENCE | DEVICE
    "identityValue": "user-8821",      // opaco; exibido como veio
    "firstSeenAt": "2026-08-30T09:11:00Z",
    "lastSeenAt": "2026-09-08T14:22:31Z"
  }],
  "page": 0, "size": 20, "total": 42, "totalPages": 3
}
```

Sem filtro além da página — é o que a API oferece, e o painel não inventa filtro que ela não tem.

---

## 4. Exibições de um respondente

```
GET /applications/{applicationId}/respondents/{respondentId}/displays
```

Aceita `outcome`, `openedFrom`, `openedTo`, `page`, `size` — **não** aceita versão, e o painel não
oferece esse filtro aqui (FR-022): o histórico atravessa pesquisas diferentes, e número de versão
só significa algo dentro de uma pesquisa.

**Sucesso `200`** — `PageResponse<RespondentDisplay>`: os mesmos campos da listagem por pesquisa,
mais `surveyId`.

---

## Leituras auxiliares, já existentes

Não são consultas novas — são de `surveys`, já consumidas pelo painel, reaproveitadas aqui.

| Consulta | Para quê | Onde |
|---|---|---|
| `GET …/surveys/{surveyId}` | distinguir "nunca publicada" de "nunca exibida" | listagem de exibições |
| `GET …/surveys/{surveyId}/versions/{number}` | enunciados das respostas ([R2](../research.md#r2)) | detalhe da exibição |
| `GET …/surveys/{surveyId}/versions/comparability` | povoar o seletor de versão | listagem de exibições |

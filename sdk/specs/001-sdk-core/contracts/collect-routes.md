# Contrato — rotas `/collect` consumidas pelo core

**Feature**: `001-sdk-core`

O lado do SDK do contrato publicado em `backend/docs/backend/contrato-sdk.md`. Aqui está o que o
SDK manda e como ele trata cada resposta. A forma exata dos DTOs vem do OpenAPI
(`openapi/pitaco.json` → `src/api/openapi.ts`); a rota de eventos é detalhada em
`specs/003-sdk-eventos-interacao/contracts/collect-events-route.md`.

## Regras comuns

| Item | Valor |
| --- | --- |
| Endereço | `${baseUrl}/collect/...`; o SDK não distingue acesso direto de gateway |
| Método | `POST` em todas |
| Cabeçalhos | `Content-Type: application/json`, `Accept: application/json`, `X-Pitaco-Key: <apiKey>`, `X-Pitaco-Sdk-Version: <SDK_VERSION>` |
| Timeout | elegibilidade: `eligibilityTimeoutMs` (padrão 3000); demais: 10000 |
| Erro | RFC 9457; o SDK decide por `status` e, quando precisa, por `code`, nunca pela mensagem |

## Classificação comum de resposta

| Resposta | `HttpOutcome` |
| --- | --- |
| 2xx | `success` (ou `malformed`, se o corpo esperado não for JSON) |
| 401, 403 | `unauthorized` |
| 429 | `rate_limited` — `Retry-After` em segundos ou data HTTP; piso 1 s, teto 1 h, 30 s se ausente |
| 408, 5xx, status 0 | `retry` (`server`) |
| rede fora | `retry` (`network`) |
| timeout | `retry` (`timeout`), com a conexão abortada |
| demais 4xx | `rejected` com `code` |
| dentro do prazo de um 429 anterior | `blocked`, sem sair |

---

## 1. `POST /collect/eligibility` — direto, fora da fila

**Quando**: a cada `track(evento, atributos?)`, salvo chave recusada ou limite de sessão (`004`).

```json
{
  "event": "checkout_completed",
  "respondent": { "reference": "u-8f1c", "deviceId": "0d0f8a5e-…" },
  "attributes": { "plano": "pro" }
}
```

`reference` só vai quando o app informou; `deviceId` vai sempre; `attributes` só quando não vazio.

| Resultado | O que o SDK faz |
| --- | --- |
| `200 { "survey": null }` | nada; aviso de depuração |
| `200` com pesquisa renderizável | oferece (ou entrega ao portão da `004`) |
| `200` com pesquisa sem pergunta renderizável | suprime (rota 4) |
| `200` com forma que o SDK não entende, ou corpo não JSON | nada; aviso + relatório `malformed_response` |
| `401`/`403` | desliga as consultas até o fim da sessão; aviso único |
| `429`/`blocked` | nada; a próxima consulta espera o prazo |
| rede/5xx | nada; aviso único com a dica de endereço por alvo |
| timeout | nada; aviso de depuração |
| outro 4xx | nada; aviso + relatório `unknown` |

---

## 2. `POST /collect/displays` — pela fila

**Quando**: no `present()`, quando o contêiner ficou visível.

```json
{
  "displayId": "3b1f0a2c-…",
  "surveyId": "…",
  "versionId": "…",
  "respondent": { "reference": "u-8f1c", "deviceId": "0d0f8a5e-…" },
  "attributes": { "plano": "pro" },
  "sdkVersion": "1.0.0"
}
```

Respondente e atributos são os do instante da apresentação.

---

## 3. `POST /collect/displays/{displayId}/submission` — pela fila

**Quando**: no desfecho (`completed` ou `dismissed`), depois da abertura.

```json
{
  "outcome": "DISMISSED",
  "answers": [
    { "questionKey": "q-nps", "status": "ANSWERED", "value": 9 },
    { "questionKey": "q-motivo", "status": "NOT_APPLICABLE" },
    { "questionKey": "q-escala", "status": "SKIPPED" }
  ]
}
```

`value` só em `ANSWERED`: texto, inteiro ou lista de textos sem repetição.

---

## 4. `POST /collect/suppressions` — pela fila

**Quando**: a pesquisa chegou sem nenhuma pergunta renderizável.

```json
{
  "surveyId": "…",
  "versionId": "…",
  "reason": "unknown_question_type",
  "questionTypes": ["MATRIX"],
  "features": [],
  "deviceId": "0d0f8a5e-…",
  "respondentReference": "u-8f1c"
}
```

`respondentReference` só quando há referência. Também gera `placement_suppressed` no `onEvent`.

---

## 5. `POST /collect/sdk-errors` — direto, fora da fila, sem esperar resposta

```json
{
  "kind": "malformed_response",
  "message": "eligibility: body_not_json",
  "context": { "route": "eligibility" },
  "occurredAt": "2026-09-12T13:45:00.000Z"
}
```

Desligado por `errorReporting={false}`. Mesma `kind|message` uma vez; até 10 por sessão; falha
desiste em silêncio.

---

## Tratamento na fila (rotas 2, 3, 4 e 6)

| Resultado | Item |
| --- | --- |
| 2xx, ou `202` com corpo ilegível | removido (eventos: só os `seq` enviados; o resto sai na mesma passada) |
| `retry` | mantido; `attempts + 1`; próxima tentativa em `2 s × 2^(attempts−1)`, até 5 min; passada interrompida |
| `rate_limited` / `blocked` | mantido; fila inteira pausada até o prazo, persistido |
| `unauthorized` | descartado; runtime desliga as consultas |
| `rejected` (400, 404, 409, 422…) | descartado; relatório `unknown` com rota, status e `code`; abertura descartada leva junto envio e eventos da exibição |

Ordem: `submission` e `events` de uma exibição nunca saem enquanto a `open_display` dela estiver
na fila. Passada forçada (ignora backoff, nunca `Retry-After`) na montagem do Provider e na volta
do segundo plano.

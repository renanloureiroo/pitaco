# Contrato público do SDK

A superfície que o SDK `@pitaco/react-native` consome: as cinco rotas sob `/collect`, os
cabeçalhos, as formas de requisição e resposta, e o que cada código significa. É derivado do
código atual (DTOs em `modules/collect/infra/http/dtos`, Swagger em
`modules/collect/infra/http/controllers/*Swagger.java`) e é a base para construir o SDK.

Se este documento e o Swagger divergirem, o Swagger vence: ele é gerado do código.

---

## Regra que vale para todas as rotas

**Falha do Pitaco é silêncio no app hospedeiro.** Nenhum código de erro desta página chega ao
usuário final. Em desenvolvimento o SDK pode avisar o integrador; em produção, desiste calado.

## Endereço

O SDK é configurado com `baseUrl` e `apiKey`, as duas obrigatórias (ADR-0008). A proposta para o
SDK é que `baseUrl` aponte para o prefixo que serve `/collect`:

| Topologia | `baseUrl` | Rota chamada |
| --- | --- | --- |
| Direta | `https://pitaco.exemplo.com/api` | `https://pitaco.exemplo.com/api/collect/eligibility` |
| Pelo gateway do app | `https://gateway.exemplo.com/pitaco` | `https://gateway.exemplo.com/pitaco/collect/eligibility` |

O SDK acrescenta `/collect/...` e não distingue as duas topologias. O contrato do gateway está em
[`proxy.md`](proxy.md).

## Cabeçalhos

| Cabeçalho | Obrigatório | O que é |
| --- | --- | --- |
| `X-Pitaco-Key` | sim | A chave da aplicação, exatamente como emitida. Pública por natureza: vive no bundle. |
| `X-Pitaco-Sdk-Version` | recomendado | Versão semver do SDK, até 40 caracteres. Valor fora do formato é ignorado, nunca recusado. |
| `Content-Type` | sim | `application/json` em toda rota. |

Sem chave ou com chave desconhecida ou revogada, toda rota responde `401`.

## Formato de erro

Toda recusa sai em RFC 9457 (`application/problem+json`), com `code` estável e `traceId`:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Chave de API inválida",
  "instance": "/api/collect/eligibility",
  "code": "api_key.invalid",
  "traceId": "8f3a1c2b9d4e5f60"
}
```

O SDK decide pelo `status` e, quando precisa, pelo `code`. A mensagem muda sem aviso.

| Status | `code` | Quando |
| --- | --- | --- |
| 400 | `request.invalid` | Corpo malformado ou constraint violada. Traz `errors`, campo para mensagem. |
| 401 | `api_key.missing` | Sem `X-Pitaco-Key`. |
| 401 | `api_key.invalid` | Chave desconhecida ou revogada. |
| 429 | `rate_limit.exceeded` | Limite da chave ou da origem. Traz `Retry-After` em segundos. |

## Limites de requisição

Janela fixa de um minuto, em memória, por instância da API. Os padrões de produção:

| Balde | Capacidade | Vale para |
| --- | --- | --- |
| Por origem (IP) | 120 por minuto | todas as rotas `/collect` |
| Por chave | 1200 por minuto | todas as rotas `/collect`, menos `/collect/sdk-errors` |
| Relatórios de erro, por chave | 30 por minuto | só `/collect/sdk-errors` |

O `429` traz `Retry-After`. O SDK respeita o valor antes de tentar de novo.

---

## 1. `POST /collect/eligibility`

"Há pesquisa para este respondente agora?" É o caminho mais quente, e o caso mais comum é não
haver nada.

### Requisição

```json
{
  "event": "checkout.completed",
  "respondent": {
    "reference": "u-8f1c",
    "deviceId": "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11"
  },
  "attributes": { "plano": "premium", "pais": "BR" }
}
```

| Campo | Regra |
| --- | --- |
| `event` | Obrigatório, até 80 caracteres. Comparado por igualdade exata com o disparo. |
| `respondent.reference` | Referência opaca do app, até 200 caracteres. Prevalece sobre o dispositivo. Nunca dado pessoal. |
| `respondent.deviceId` | UUID gerado e guardado pelo SDK. Obrigatório quando não há `reference`. |
| `attributes` | Opcional. Até 50 pares; nome até 80 caracteres, valor até 200. Só o necessário, nunca dado pessoal. |

Pelo menos um de `reference` e `deviceId` precisa vir. O SDK sempre manda o `deviceId`.

### Resposta

**Sempre `200`.** Não existe `204` nesta rota: ausência de pesquisa é sucesso, e o SDK
desserializa uma forma só.

Sem pesquisa:

```json
{ "survey": null }
```

Aplicação inativa, pesquisa pausada, fora da janela, evento sem pesquisa, regra de segmentação
não satisfeita, respondente em descanso, não sorteado e já respondida devolvem todos
`survey: null`. O SDK não tem como, nem precisa, distinguir.

Com pesquisa, a versão publicada inteira numa resposta só:

```json
{
  "survey": {
    "surveyId": "…",
    "versionId": "…",
    "versionNumber": 3,
    "freeTextNotice": {
      "enabled": true,
      "text": "Evite escrever dados pessoais, como nome, telefone ou e-mail."
    },
    "questions": [
      {
        "key": "q-nps",
        "position": 1,
        "statement": "Em uma escala de 0 a 10, o quanto você recomendaria este app a um amigo ou colega?",
        "type": "NPS",
        "required": true,
        "options": [],
        "range": { "min": 0, "max": 10, "minLabel": "Nada provável", "maxLabel": "Extremamente provável" },
        "condition": null
      },
      {
        "key": "q-motivo",
        "position": 2,
        "statement": "O que mais pesou na sua nota?",
        "type": "SINGLE_CHOICE",
        "required": false,
        "options": [
          { "label": "Preço", "value": "preco", "position": 1 },
          { "label": "Atendimento", "value": "atendimento", "position": 2 }
        ],
        "range": null,
        "condition": { "sourceKey": "q-nps", "operator": "between", "values": [], "min": 0, "max": 6 }
      }
    ]
  }
}
```

Com `default-property-inclusion: non_null`, campos nulos podem vir ausentes em vez de `null`. O
SDK trata os dois do mesmo jeito.

#### Pergunta

| Campo | O que é |
| --- | --- |
| `key` | Chave estável da pergunta. É ela que vai de volta no envio. |
| `position` | Ordem de exibição, a partir de 1. A ordem definida é a que o respondente vê. |
| `type` | `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `RATING`, `SCALE`, `NPS` ou `FREE_TEXT`. |
| `required` | Obrigatória impede avançar em branco. |
| `options` | Só nas de escolha. `value` é o que se envia; `label` é o que se mostra. |
| `range` | Só nas numéricas. Faixa inclusiva, com rótulos opcionais dos extremos. NPS é sempre 0 a 10. |
| `condition` | Presente quando a pergunta só aparece se a resposta a uma anterior satisfaz a condição. |

**Tipo desconhecido é pulado em silêncio.** Campo desconhecido dentro de pergunta conhecida é
ignorado. Se depois do descarte não sobrar pergunta renderizável, o SDK não exibe nada, não abre
exibição e sinaliza a supressão (seção 4).

#### Condição

Avaliada **localmente** pelo SDK enquanto o respondente percorre a pesquisa, sem nova consulta.

| `operator` | Satisfeita quando a resposta à `sourceKey` |
| --- | --- |
| `equals` | é igual a `values[0]`. Em múltipla escolha, **contém** o valor. |
| `not_equals` | é diferente de `values[0]`. Em múltipla escolha, não contém. |
| `in` | está em `values`. Em múltipla escolha, contém algum deles. |
| `between` | é um número entre `min` e `max`, inclusive. |

Nas numéricas, `values` traz os números como texto. A origem é sempre uma pergunta anterior, e
nunca de texto livre, então não existe ciclo.

Regra proposta para o SDK: origem pulada ou não aplicável não satisfaz condição nenhuma. A
pergunta que a condição pula é enviada com `status: NOT_APPLICABLE`.

#### Aviso de texto livre

`freeTextNotice.enabled` diz se o SDK mostra, junto dos campos de texto livre, o aviso curto de
não escrever dado pessoal. `text` vem resolvido, com o padrão aplicado. Desligado, o SDK não
mostra nada.

### Efeitos colaterais

A consulta registra o evento e os atributos no catálogo da aplicação e conta a versão do SDK.
Nada disso cria respondente nem exibição.

---

## 2. `POST /collect/displays`

Abre a exibição. **O SDK chama só quando vai de fato mostrar a pesquisa**, depois de descartar o
que não sabe renderizar. Pesquisa que não aparece não conta como exibida.

### Requisição

```json
{
  "displayId": "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
  "surveyId": "…",
  "versionId": "…",
  "respondent": { "reference": "u-8f1c", "deviceId": "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11" },
  "attributes": { "plano": "premium" },
  "sdkVersion": "1.0.0"
}
```

| Campo | Regra |
| --- | --- |
| `displayId` | UUID **gerado no dispositivo**. É a chave de idempotência de todo o ciclo da exibição. |
| `surveyId`, `versionId` | Os recebidos na elegibilidade. A versão fica congelada na exibição. |
| `respondent` | A mesma identificação usada na elegibilidade. |
| `attributes` | Instantâneo dos atributos neste momento, mesmos limites da elegibilidade. |
| `sdkVersion` | Até 40 caracteres. |

### Respostas

| Status | Quando |
| --- | --- |
| 201 | Exibição aberta. Corpo abaixo e `Location` da exibição. |
| 200 | A mesma abertura de novo, mesma pesquisa e versão: a exibição existente, sem criar outra. |
| 400 | Corpo malformado, `displayId` fora do formato UUID, respondente sem identificação. |
| 404 | `survey_version.not_found`: versão inexistente, em rascunho ou de outra aplicação. |
| 409 | `display.identifier_conflict`: o `displayId` já foi usado para outra pesquisa ou versão. |
| 422 | `application.inactive`. |

```json
{
  "displayId": "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
  "surveyId": "…",
  "versionId": "…",
  "outcome": "STARTED",
  "openedAt": "2026-09-08T18:22:31Z"
}
```

---

## 3. `POST /collect/displays/{displayId}/submission`

Respostas e desfecho num ato só, atômico: o envio é validado inteiro antes de qualquer gravação,
e uma recusa deixa zero linhas.

### Requisição

```json
{
  "outcome": "COMPLETED",
  "answers": [
    { "questionKey": "q-nps", "status": "ANSWERED", "value": 4 },
    { "questionKey": "q-motivo", "status": "ANSWERED", "value": "preco" },
    { "questionKey": "q-extra", "status": "SKIPPED", "value": null },
    { "questionKey": "q-condicional", "status": "NOT_APPLICABLE", "value": null }
  ]
}
```

| `outcome` | Significado |
| --- | --- |
| `COMPLETED` | Concluída. Toda obrigatória precisa estar respondida. |
| `DISMISSED` | Dispensada. Aceita o parcial, e o que veio é preservado. Dispensa é dado, não erro. |

Abandono nunca é enviado: a exibição sem desfecho vira abandonada na leitura, depois de 30
minutos.

| `status` | `value` |
| --- | --- |
| `ANSWERED` | Obrigatório, na forma do tipo abaixo. |
| `SKIPPED` | Ausente. A pessoa viu a pergunta e não respondeu. |
| `NOT_APPLICABLE` | Ausente. Só em pergunta com `condition`, quando a condição a pulou. |

| Tipo | Forma de `value` |
| --- | --- |
| `SINGLE_CHOICE` | Texto: o `value` de uma opção. |
| `MULTIPLE_CHOICE` | Lista de textos, sem repetição, cada um o `value` de uma opção. |
| `RATING`, `SCALE`, `NPS` | Inteiro dentro de `range`. |
| `FREE_TEXT` | Texto, até 2000 caracteres. |

### Respostas

| Status | Quando |
| --- | --- |
| 204 | Gravado, ou reconhecido como reenvio idêntico. |
| 400 | Corpo malformado, desfecho desconhecido, `value` com forma que não é texto, inteiro ou lista de textos. |
| 404 | `display.not_found`: exibição inexistente ou de outra aplicação. |
| 409 | `display.already_closed`: exibição já fechada, e o envio traz desfecho diferente ou resposta nova. |
| 422 | `submission.rejected`, com **todos** os problemas em `errors[]`, ou `application.inactive`. |

Cada item de `errors[]` traz `questionKey` e um `code` entre `answer.question_unknown`,
`answer.question_duplicated`, `answer.required_missing`, `answer.value_missing`,
`answer.value_type_mismatch`, `answer.option_unknown`, `answer.options_empty`,
`answer.options_duplicated`, `answer.value_out_of_range`, `answer.text_too_long` e
`answer.not_applicable_unconditional`.

O envio continua aceito se a pesquisa foi pausada, encerrada ou republicada depois da abertura: a
validação usa a versão exibida. É o que deixa concluir quem estava no meio quando a cota foi
atingida.

---

## 4. `POST /collect/suppressions`

A pesquisa chegou, mas o SDK não sabe renderizar nenhuma das perguntas. Nada foi exibido e
nenhuma exibição foi aberta.

```json
{
  "surveyId": "…",
  "versionId": "…",
  "reason": "unknown_question_type",
  "questionTypes": ["MATRIX"],
  "features": [],
  "respondentReference": "u-8f1c",
  "deviceId": "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11"
}
```

| Campo | Regra |
| --- | --- |
| `reason` | `unknown_question_type` ou `unsupported_feature`. |
| `questionTypes`, `features` | Opcionais, até 20 itens de até 40 caracteres, para diagnóstico. |
| `respondentReference`, `deviceId` | Não são gravados. Servem só para contar uma vez a mesma supressão. |

Responde **`202` sem corpo** tanto quando grava quanto quando descarta. Descarta pesquisa de
outra aplicação, versão que não é publicada, aplicação inativa e a mesma supressão do mesmo
respondente na mesma versão nas últimas 24 horas. `400`, `401` e `429` seguem a regra geral.

---

## 5. `POST /collect/sdk-errors`

Canal próprio do Pitaco para falhas internas do SDK. Nunca a ferramenta de erro do app
hospedeiro. O integrador pode desligar.

```json
{
  "kind": "render_error",
  "message": "Tipo de pergunta sem renderizador: MATRIX",
  "context": { "questionType": "MATRIX", "stage": "render" },
  "occurredAt": "2026-09-12T13:45:00Z"
}
```

| Campo | Regra |
| --- | --- |
| `kind` | `render_error`, `network_error`, `malformed_response`, `storage_error` ou `unknown`. Fora da lista vira `unknown`. |
| `message` | Até 4000 caracteres no envio; guardada com até 500. E-mail e número longo são mascarados. |
| `context` | Texto, número, booleano e um nível de objeto; guarda até 20 chaves e cerca de 2 KB. |
| `occurredAt` | Instante no dispositivo. Ausente ou no futuro, vale o do recebimento. |

**O relatório descreve o SDK, nunca o usuário.** Não mandar dado pessoal, identificação do
respondente, atributo nem conteúdo de resposta. O servidor descarta chaves de contexto com
palavras de dado pessoal (email, user, device, answer, text, value, token…) como rede de
segurança, não como licença.

Responde `202` sem corpo; relatório de aplicação inativa é descartado com a mesma resposta. Tem o
balde próprio de 30 por minuto por chave. **Falha neste envio desiste em silêncio**, sem gerar
novo relatório.

---

## Fila local e idempotência

O desenho que o servidor espera do SDK:

1. Gerar o `displayId` no dispositivo antes de abrir a exibição, e guardá-lo com a pesquisa.
2. Gravar abertura e envio na fila local **antes** de tentar enviar.
3. Reenviar em ordem: a abertura primeiro, depois o envio. Um envio que recebe `404` porque a
   abertura nunca chegou volta para trás da abertura.
4. Repetir a mesma abertura devolve `200`; repetir o mesmo envio devolve `204`. Reenvio nunca
   duplica.

| Resultado | O que o SDK faz com o item |
| --- | --- |
| 2xx | Remove da fila. |
| Rede, timeout, 5xx | Mantém e tenta de novo depois, inclusive numa abertura futura do app. |
| 429 | Mantém e respeita `Retry-After`. |
| 400, 401, 404 fora do caso acima, 409, 422 | Descarta. Tentar de novo não muda o resultado. |

A fila tem limite de tamanho e de idade, e descarta o que passa do limite.

## Compatibilidade

O servidor guarda, em `core/catalog/SdkCapabilities`, a versão mínima do SDK para cada tipo de
pergunta e cada recurso. Hoje tudo exige `1.0.0`. Quando um tipo ou recurso novo entrar, ele
nasce com a versão que o suporta, e a supressão passa a informar a partir de qual versão a
pesquisa funcionaria.

Mudança neste contrato é aditiva: campo novo não quebra SDK antigo, e remoção só acontece depois
de os consumidores migrarem, em release separado.

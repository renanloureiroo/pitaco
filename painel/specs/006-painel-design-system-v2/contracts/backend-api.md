# Contrato consumido — leituras da Visão geral e do shell

Esta entrega não consome rota nova do backend. A Visão geral e o shell compõem leituras já
especificadas, sempre do servidor do painel, sem chave de aplicação. Envelope de página e recusa
RFC 9457 seguem o [contrato de 001](../../001-painel-operacao-pesquisas/contracts/backend-api.md).

Notação: `{a}` = `applicationId`, `{s}` = `surveyId`.

---

## Shell

| Método e caminho | Uso | Entrada | Campos usados |
|---|---|---|---|
| `GET /applications` | seletor de aplicação na barra lateral | `page=0`, `size=100` | `items[].id`, `name`, `status` |
| `GET /applications/{a}` | trilha (nome) | — | `name` |

Falha na listagem: a barra aparece sem aplicações. Falha na leitura da aplicação: a trilha mostra
"Aplicação".

---

## Visão geral

| Método e caminho | Quantas vezes | Entrada | Campos usados |
|---|---|---|---|
| `GET /applications/{a}` | 1 | — | `Application` inteiro (cabeçalho e configuração) |
| `GET /applications/{a}/surveys` | 1 | `page=0`, `size=100` | `id`, `name`, `state`, `templateKind`, `publishedVersionNumber` |
| `GET /applications/{a}/surveys/{s}/results` | 2 por pesquisa publicada | atual: `from`; anterior: `from`, `to` | `responseRate.*` (contagens, `timeline`), `nps.*`, `attributes` |
| `GET /applications/{a}/surveys/{s}/results` | até 6 (segmento) | `from`, `attribute`, `attributeValue` | `responseRate.*`, `nps.*` |
| `GET /applications/{a}/respondents` | 1 | `page=0`, `size=1` | `total` |
| `GET /applications/{a}/sdk-versions` | 1 | — | `versions[].version`, `recentRequestCount` |
| `GET /applications/{a}/sdk-errors` | 1 | `page=0`, `size=1` | `total` |

Contratos completos: resultado em
[004](../../004-painel-resultados-comportamento/contracts/backend-api.md), respondentes em
[002](../../002-painel-leitura-coleta/contracts/backend-api.md), SDK em
[005](../../005-painel-saude-privacidade/contracts/backend-api.md).

**Tolerância a falha**:

| Leitura | Se falhar |
|---|---|
| aplicação | `404` → não encontrado; outra falha → estado de falha |
| lista de pesquisas | estado de falha |
| resultado de uma pesquisa | a pesquisa entra com zeros e não soma |
| respondentes, SDK, erros | o bloco mostra zero ou vazio |
| um valor de segmento | a linha é omitida |

---

## Configuração da aplicação (na Visão geral)

| Método e caminho | Uso | Saída |
|---|---|---|
| `POST /applications/{a}/deactivate` | Desativar, depois de confirmar | `200` `Application` |
| `POST /applications/{a}/activate` | Reativar | `200` `Application` |
| `PATCH /applications/{a}` | Editar (ver [005](../../005-painel-saude-privacidade/contracts/backend-api.md)) | `200` `Application` |

**Funções**: `setApplicationStatusAction` → `deactivateApplication` / `activateApplication` (em
`features/applications`). Revalidação: `refresh()`.

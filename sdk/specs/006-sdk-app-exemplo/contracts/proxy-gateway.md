# Contrato — proxy local (`example/proxy/server.mjs`)

**Feature**: `006-sdk-app-exemplo`

Faz o papel do gateway do app hospedeiro, seguindo `backend/docs/backend/proxy.md`. Sem dependência
além de `node:http` e `node:https`.

## Execução

```bash
npm run proxy                       # http://0.0.0.0:8787/pitaco -> http://localhost:8080/api
node proxy/server.mjs --port 8787 --target http://localhost:8080/api --prefix /pitaco
```

Também lê `PITACO_PROXY_PORT`, `PITACO_PROXY_TARGET` e `PITACO_PROXY_PREFIX`. O SDK aponta o
`baseUrl` para `http://<host>:<porta><prefixo>`.

## Roteamento

| Requisição | Resposta |
| --- | --- |
| `POST {prefixo}/collect/*` | repassada para `{alvo}/collect/*`, mesmo restante do caminho e query |
| outra rota | `404` `application/problem+json`, `code: gateway.route_not_found`; o Pitaco não é tocado |
| outro método em rota `/collect` | `405` |

## Cabeçalhos

| Cabeçalho | Tratamento |
| --- | --- |
| `X-Pitaco-Key`, `X-Pitaco-Sdk-Version`, `Content-Type` e demais do cliente | repassados sem alterar |
| cabeçalhos de salto e de origem | removidos |
| `X-Forwarded-For` | **substituído** pelo IP que o proxy viu |
| `X-Forwarded-Proto`, `X-Forwarded-Host` | definidos pelo proxy |
| `CF-Connecting-IP` do cliente | **descartado** |

## Resposta do Pitaco

Status, corpo e cabeçalhos devolvidos intactos — inclusive `Retry-After` e `Location` —, sem
cache; o corpo da requisição passa por `pipe`, sem reescrita.

## Falhas

| Situação | Resposta |
| --- | --- |
| Pitaco inalcançável | `502` em JSON (`gateway.upstream_unavailable`) |
| sem conexão em 5 s ou sem resposta em 10 s | `504` em JSON (`gateway.timeout`) |

Nunca HTML.

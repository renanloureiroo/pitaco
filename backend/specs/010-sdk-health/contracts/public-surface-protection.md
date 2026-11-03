# Contrato — Proteção da superfície pública

**Feature**: `010-sdk-health` · Vale para `/collect/**`. O contrato do gateway do app hospedeiro
está em `docs/backend/proxy.md`.

## Ordem dos interceptors (`SecurityWebMvcConfiguration`)

| # | Interceptor | Padrão | Falha |
| --- | --- | --- | --- |
| 1 | `OriginRateLimitInterceptor` | `/collect/**` | `429 rate_limit.exceeded` |
| 2 | `ApiKeyAuthenticationInterceptor` | `/collect/**` | `401 api_key.missing` / `401 api_key.invalid` |
| 3 | `ApiKeyRateLimitInterceptor` | `/collect/**` menos `/collect/sdk-errors` | `429` |
| 3' | `SdkErrorRateLimitInterceptor` | `/collect/sdk-errors` | `429` |
| — | `AdminSurfaceInterceptor` | `/applications/**` | `403 api_key.forbidden_surface` se houver `X-Pitaco-Key` |

O `/api` vem do `context-path` e não entra nos padrões. Nenhum limite alcança `/applications/**`.

## Baldes padrão

| Balde | Assunto | Capacidade |
| --- | --- | --- |
| Por origem | endereço resolvido | 120 / minuto |
| Por chave | `apiKeyId` | 1.200 / minuto |
| Registros de erro | `apiKeyId` | 30 / minuto |

Janela fixa em memória, por instância.

## `429`

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 37
Content-Type: application/problem+json

{ "status": 429, "code": "rate_limit.exceeded", "detail": "Limite de requisições excedido; tente novamente mais tarde", "traceId": "…" }
```

`Retry-After` em segundos, arredondado para cima, nunca zero.

## Resolução da origem

1. Par imediato ilegível: usado como veio.
2. Par imediato fora de `trusted-proxies`: é a origem; nenhum cabeçalho é lido.
3. Par confiável e `client-ip-header` (padrão `CF-Connecting-IP`) com endereço não confiável: é a
   origem.
4. Senão, `X-Forwarded-For` do fim para o começo, pulando confiáveis; o primeiro não confiável é a
   origem; salto ilegível encerra a leitura.
5. Nada encontrado: o par imediato.

Endereço com porta ou colchetes é normalizado; nome de host não é endereço e nunca vira consulta
DNS. CIDR inválido em `trusted-proxies` impede a subida.

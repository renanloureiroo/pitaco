# Quickstart — validar `010-sdk-health`

Contratos em [`contracts/sdk-health.md`](./contracts/sdk-health.md) e
[`contracts/public-surface-protection.md`](./contracts/public-surface-protection.md).

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# domínio e borda, sem Docker
./mvnw test -Dtest='SdkVersionTest,SdkCapabilitiesTest,SdkErrorContextTest,SdkErrorReportTest,SdkErrorKindTest,SuppressionDedupKeyTest,SuppressionShareTest,FixedWindowRateLimiterTest,OriginResolverTest,RateLimitInterceptorChainTest,BufferedSdkUsageRecorderTest'

# casos de uso
./mvnw test -Dtest='ListSdkVersionsUseCaseTest,ReportSdkErrorUseCaseTest,ListSdkErrorsUseCaseTest,RecordSuppressionUseCaseTest,GetSurveyHealthUseCaseTest,PurgeHealthDataUseCaseTest'

# E2E
./mvnw test -Dtest='SdkVersionsE2ETest,SdkErrorReportE2ETest,SuppressionE2ETest,SurveyHealthE2ETest,PublicSurfaceRateLimitE2ETest,PublicSurfaceSecurityE2ETest'
```

## 3. Roteiro manual

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `POST /collect/eligibility` com `X-Pitaco-Sdk-Version: 1.2.0`; aguardar 1 min; `GET /applications/{id}/sdk-versions` | `1.2.0` com `recentShare` |
| 2 | a mesma consulta com `X-Pitaco-Sdk-Version: banana` | `200`; nada novo na distribuição |
| 3 | `POST /collect/sdk-errors` com `"message":"falhou para ana@exemplo.com"` e `"context":{"userEmail":"x","stage":"render"}` | `202`; na listagem, `message` com `[email]` e `context` só com `stage` |
| 4 | `POST /collect/suppressions` duas vezes com o mesmo `deviceId` | `202` nas duas; uma linha em `suppression_events` |
| 5 | `POST /collect/suppressions` com `reason: "outro"` | `400` |
| 6 | `GET /applications/{id}/surveys/{s}/health` | exibidas, supressões por motivo e versão, `minRequiredVersion`, `eventLastSeenAt` |
| 7 | `GET …/health?from=2026-09-12T00:00:00Z&to=2026-09-01T00:00:00Z` | `400` |
| 8 | `GET /applications/{id}/sdk-versions` com `X-Pitaco-Key` | `403 api_key.forbidden_surface` |
| 9 | `POST /collect/eligibility` sem chave | `401 api_key.missing` |

### Limite de requisições

Com o perfil local, reduza `pitaco.collect.rate-limit.per-key.capacity` para 3 e chame a
elegibilidade quatro vezes com a mesma chave: a quarta responde `429` com `Retry-After`. Com
`curl -H "X-Forwarded-For: 1.2.3.4, 10.0.0.5"` direto na API, a origem contada continua sendo o
endereço de quem conectou.

## 4. O que precisa estar verde

- [X] Nenhuma escrita síncrona por consulta para contar versão
- [X] Nenhum registro gravado com e-mail ou número longo
- [X] Supressões simultâneas do mesmo dispositivo contam uma vez
- [X] Origem forjada não escapa do limite; `CF-Connecting-IP` vale atrás do túnel
- [X] Balde de erros isolado da elegibilidade

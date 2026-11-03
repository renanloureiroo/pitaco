# Quickstart — validar `004-sdk-controle-onde-quando`

Superfície em [`contracts/placement-api.md`](./contracts/placement-api.md).

## Pré-requisitos

- `cd sdk && npm ci && npm ci --prefix example`

---

## 1. O portão

```bash
cd sdk
npm run verify
```

## 2. Só esta fatia

```bash
npx jest --ci src/core/runtime                   # sessão, bloqueio, adiamento, reset, placement_
npx jest --ci src/react/__tests__/PitacoBlock    # componente declarativo
npx jest --ci src/react/__tests__/PitacoProvider.lifecycle   # runtime substituído encerrado
npx jest --ci src/react/__tests__/hooks          # ações inertes fora do Provider, diagnóstico
```

## 3. O que precisa estar coberto

| # | Cenário | Onde (`runtime.test.ts`, salvo indicação) |
| --- | --- | --- |
| 1 | Depois de exibida (mesmo dispensada), nenhuma outra na sessão | "sessão de app: no máximo uma pesquisa exibida" |
| 2 | `simulateAppReopen` zera o limite | idem |
| 3 | Oferecida e nunca apresentada não esgota a sessão | idem |
| 4 | Contagem de referências; só o primeiro `block` emite `placement_blocked` | "bloqueio: block/unblock" |
| 5 | Motivos diferentes independentes | idem |
| 6 | `unblock` sem `block` não lança, avisa | idem |
| 7 | Retida durante bloqueio; desbloquear exibe | idem |
| 8 | Bloqueio não interrompe pesquisa aberta | idem |
| 9 | Retida por bloqueio além do prazo descartada, sem consumir a sessão | idem |
| 10 | `defer` represa; `release` exibe | "adiamento: defer/release" |
| 11 | `release` sem `defer` não lança, avisa | idem |
| 12 | Prazo do adiamento descarta, sem consumir a sessão | idem |
| 13 | `defer` + `block`: `release` mantém retida até desbloquear | idem |
| 14 | `reset` descarta a retida; bloqueios e limite continuam | "reset" |
| 15 | `placement_` nunca entram na fila nem vão ao servidor | "nunca entram na fila…" |
| 16 | Dois `<PitacoBlock />` com o mesmo motivo não se anulam | `PitacoBlock.test.tsx` |
| 17 | Trocar `deferTimeoutMs` com retida não emite `placement_expired` pelo antigo | `PitacoProvider.lifecycle.test.tsx` |

## 4. No aparelho

No app de exemplo, a aba **Depuração** mostra no estado (`diagnostics()`, lido a cada segundo) se a
sessão já exibiu pesquisa, e o botão **Simular app reaberto** zera o limite. A tela "Testar
pesquisas" chama `simulateAppReopen()` antes de cada abertura justamente porque, sem isso, só a
primeira abertura da sessão consultaria o servidor. Os `placement_` aparecem na lista de eventos,
agrupados em "Sem exibição (placement_)" quando o painel agrupa por exibição.

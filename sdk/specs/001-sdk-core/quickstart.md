# Quickstart — validar `001-sdk-core`

Como provar que o core funciona. Não traz código de implementação: traz o que rodar e o que
esperar. A superfície pública está em [`contracts/public-api.md`](./contracts/public-api.md); as
rotas consumidas, em [`contracts/collect-routes.md`](./contracts/collect-routes.md).

## Pré-requisitos

- Node 20 ou mais novo
- `cd sdk && npm ci` (e `npm ci --prefix example`, porque o portão também checa o exemplo)

---

## 1. O portão

```bash
cd sdk
npm run verify
```

Roda `eslint .`, `tsc --noEmit`, `jest --ci`, `bob build`, e `typecheck` e `lint` do exemplo.
Precisa terminar com código 0, sem teste ignorado.

## 2. Só o core

```bash
# máquina de estado, condição, schema
npx jest --ci src/core/machine src/core/survey

# fila, transporte, storage, identidade, UUID, relatório
npx jest --ci src/core/queue src/core/transport src/core/storage src/core/__tests__ src/core/errors

# runtime de ponta a ponta sobre o servidor falso
npx jest --ci src/core/runtime

# Provider e hooks
npx jest --ci src/react/__tests__/hooks src/react/__tests__/PitacoProvider.lifecycle

# versão e peers
npx jest --ci src/__tests__/version
```

## 3. Regenerar o contrato

```bash
# com o backend no ar em localhost:8080 (atualiza openapi/pitaco.json)
npm run gen:api

# sem backend: usa o snapshot versionado e só regenera src/api/openapi.ts
PITACO_OPENAPI_URL=http://localhost:1/nada npm run gen:api
```

Depois, `npm run typecheck` precisa continuar verde: divergência de DTO aparece ali.

## 4. Conferir que não há módulo nativo

```bash
npm run build
grep -rnE "require\(|from " lib/module lib/commonjs | grep -E "reanimated|gesture-handler|gorhom|safe-area-context|async-storage|react-native-mmkv"
```

Esperado: nenhuma linha com `import`/`require` real — só comentários e mensagens de aviso dos
adaptadores de storage citam esses nomes.

---

## 5. Cenários que precisam estar cobertos

| # | Cenário | Onde está provado |
| --- | --- | --- |
| 1 | Sem pesquisa: nada disponível, nenhuma abertura | `runtime.test.ts` — "sem pesquisa…" |
| 2 | Abertura só quando o contêiner fica visível | `runtime.test.ts` — "a exibição só abre…" |
| 3 | API fora do ar, lenta (3 s), timeout configurável | `runtime.test.ts`, `http.test.ts` |
| 4 | Chave inválida desliga as consultas, com um aviso | `runtime.test.ts` — "chave inválida…" |
| 5 | 429 respeita `Retry-After`, inclusive depois de reabrir | `http.test.ts`, `queue.test.ts` |
| 6 | Pergunta desconhecida pulada; só desconhecidas → supressão | `runtime.test.ts`, `schema.test.ts` |
| 7 | Resposta sem rede enviada exatamente uma vez ao reabrir | `queue.test.ts`, `runtime.test.ts` |
| 8 | Reenvio sem duplicar | `queue.test.ts`, `runtime.test.ts` |
| 9 | Item velho e excesso descartados | `queue.test.ts` |
| 10 | Dispensa na segunda pergunta preservando a primeira | `machine.test.ts`, `runtime.test.ts` |
| 11 | Logout troca o `deviceId` e mantém a fila | `runtime.test.ts`, `identity.test.ts` |
| 12 | Configuração inválida não derruba a árvore | `hooks.test.tsx`, `runtime.test.ts` |
| 13 | Hooks fora do Provider inertes, com aviso | `hooks.test.tsx` |
| 14 | Relatório de erro sem dado de usuário, uma vez, com teto | `reporter.test.ts` |
| 15 | Runtime substituído encerrado; fila entregue pelo novo | `PitacoProvider.lifecycle.test.tsx` |

## 6. Ponta a ponta com o backend local

O app de exemplo (`006-sdk-app-exemplo`) é o harness: com o backend em `localhost:8080` e o seed
aplicado, abrir uma pesquisa, responder em modo avião (ou com a rede cortada), fechar o app,
reabrir com rede e conferir no painel de depuração que a fila esvazia e o envio sai uma vez.

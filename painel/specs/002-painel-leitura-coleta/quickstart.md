# Quickstart — validar a leitura da coleta

Como provar que esta feature funciona ponta a ponta. Não contém código de implementação: é o
guia de execução e verificação. Os detalhes de contrato estão em
[contracts/](./contracts/backend-api.md) e o modelo em [data-model.md](./data-model.md).

---

## Pré-requisitos

- Node 20+ e as dependências instaladas (`npm ci`)
- Para validar contra a API real: backend do Pitaco no ar, **já com a mudança de
  `versionNumber`** ([R7](./research.md#r7)), e `.env.local` apontando para ele (veja
  `.env.example`)
- Para validar sem backend: nada além do repositório — o E2E sobe seu próprio servidor de API
  simulado

---

## Portão de qualidade (o que precisa passar antes do merge)

```bash
npm run verify     # lint + typecheck + vitest + playwright
```

Ou, isoladamente, durante o desenvolvimento:

```bash
npm run lint
npm run typecheck      # next typegen && tsc --noEmit
npm test               # vitest run
npm run test:e2e       # playwright test
```

A constituição não admite suíte vermelha no merge. `npm run typecheck` roda `next typegen`
antes do compilador porque os helpers `PageProps` / `LayoutProps` das rotas novas só existem
depois da geração de tipos — rotas novas **exigem** essa ordem.

---

## Validação da mudança no backend

A única mudança fora do painel. No repositório `backend/`:

```bash
./mvnw test -Dtest='*Display*'
```

**O que precisa estar coberto** (ver [R7](./research.md#r7)):

| Verificação | Resultado esperado |
|---|---|
| Filtro por número de versão existente | só exibições daquela versão |
| Filtro por número que não existe na pesquisa | **página vazia**, não `404` |
| Número malformado ou menor que 1 | `400` apontando o campo |
| Sem filtro de versão | exibições de todas as versões |
| `total` com filtro de versão aplicado | igual ao tamanho do conjunto sem paginação — é o que pega a junção esquecida na `countQuery` |

---

## Validação E2E do painel

```bash
npm run test:e2e -- coleta.spec.ts
npm run test:e2e:ui -- coleta.spec.ts    # para observar passo a passo
```

O `e2e/stub-api/` semeia exibições, respostas e respondentes diretamente ([R9](./research.md#r9)):
o painel é somente leitura e não sabe criá-los. Cada teste cria e usa apenas seus próprios dados.

**Cenários que o `coleta.spec.ts` precisa cobrir** — são os do
[contrato de rotas](./contracts/ui-routes.md#fluxo-coberto-por-e2e-sc-007), e juntos provam SC-007.

---

## Roteiro de verificação manual

Contra a API real, com uma pesquisa publicada e com coleta.

### 1. Exibições da pesquisa (US1)

| Passo | O que conferir |
|---|---|
| Abrir a pesquisa → aba **Exibições** | listagem da mais recente para a mais antiga; versão, grupo, desfecho, SDK, abertura, fechamento |
| Filtrar por versão | só aquela versão; recarregar mantém; voltar de um detalhe mantém |
| Filtrar por desfecho | só aquele desfecho |
| Informar período | limites **inclusive** nos dois extremos |
| Informar início posterior ao fim | recusa no campo, **sem navegar**, listagem anterior intacta |
| Encontrar uma exibição `STARTED` | fechamento como "ainda aberta" — nunca vazio nem `0` |
| Localizar o rótulo do fuso | presente e correto ([R3](./research.md#r3)) |

### 2. Conteúdo de uma exibição (US2)

| Passo | O que conferir |
|---|---|
| Abrir uma exibição concluída | cada resposta ao lado do enunciado da **versão exibida**, na ordem da versão |
| Encontrar uma resposta pulada | diz "Pulada" — distinta de vazia |
| Encontrar uma de texto livre expirada | diz "Texto expirado" e explica a retenção (SC-006) |
| Ver o bloco de atributos | rotulado como da **exibição**, não do respondente |
| Abrir uma exibição aberta ou dispensada | estado explícito de "sem respostas" coerente com o desfecho |
| Acessar um `displayId` inexistente | tela de não encontrado, com caminho de volta |
| Seguir o vínculo do respondente | chega ao histórico |

**Verificação de degradação** ([R2](./research.md#r2)): com a leitura da versão indisponível, o
detalhe **ainda renderiza** — respostas pela chave, com aviso. Nenhuma resposta some.

### 3. Respondentes (US3)

| Passo | O que conferir |
|---|---|
| Aplicação → aba **Respondentes** | forma de identificação em português (nunca `APP_REFERENCE` cru), valor, primeiro e último contato |
| Abrir um respondente | exibições de **qualquer** pesquisa, com a coluna de pesquisa visível |
| Procurar o filtro de versão | **não** existe aqui (FR-022) |
| Filtrar por desfecho e período | recorte muda |
| Abrir uma exibição do histórico | mesmo detalhe da US2 |

### 4. Estados e limites

| Passo | O que conferir |
|---|---|
| Pesquisa publicada e nunca exibida | "Nenhuma exibição ainda" |
| Pesquisa nunca publicada | "Esta pesquisa ainda não foi publicada" + vínculo |
| Filtro sem resultado | "Nenhuma exibição neste recorte" + limpar filtros |
| Aplicação sem respondentes | vazio convidativo, nunca erro |
| Derrubar a API e recarregar | estado de falha com nova tentativa, sem página em branco |
| `?page=999` | volta a um recorte válido, sem erro |

### 5. Contagem de leituras (SC-009)

Com a aba de rede do navegador aberta, conferir contra a
[tabela de leituras por tela](./contracts/ui-routes.md#leituras-por-tela): 3 leituras nas
exibições da pesquisa, 2 no detalhe, 1 em cada listagem de respondente. **Nenhuma** requisição
existe só para produzir um número na tela.

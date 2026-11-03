# Quickstart — Painel de operação (001)

Como rodar o painel e como provar que a feature funciona. Detalhes de contrato ficam em
[`contracts/backend-api.md`](./contracts/backend-api.md) e
[`contracts/ui-routes.md`](./contracts/ui-routes.md); os tipos, em
[`data-model.md`](./data-model.md).

---

## Pré-requisitos

- Node ≥ 20 e npm.
- Para desenvolvimento contra dados reais: o backend Pitaco rodando em `../backend`
  (`docker compose up` e `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), respondendo em
  `http://localhost:8080/api`.
- Para rodar apenas o painel e a suíte E2E, o backend **não** é necessário: o E2E sobe um servidor
  de API simulado.

## Configuração

Crie `.env.local` na raiz do painel:

```bash
PITACO_API_URL=http://localhost:8080/api
```

É a única variável. Ela é lida **somente no servidor** — não existe equivalente `NEXT_PUBLIC_*`,
porque nada no navegador fala com o backend. Sem ela, a primeira leitura falha com mensagem
explícita em vez de tentar uma URL relativa.

## Rodar

```bash
npm install
npm run dev          # http://localhost:3000 → redireciona para /aplicacoes
```

---

## Validação por história de usuário

Cada bloco abaixo prova uma história da [spec](./spec.md) e pode ser executado sozinho.

### US1 — Ver e criar aplicações

1. Abrir `/aplicacoes` sem nenhuma aplicação: a tela mostra o estado vazio, não um erro.
2. "Nova aplicação" → preencher só o nome → enviar. O slug é derivado pelo backend.
3. A tela vai para o detalhe. Os três prazos aparecem como **não configurados**, nunca `0`.
4. Voltar à lista, filtrar por situação, avançar de página: filtro e página ficam na URL e
   sobrevivem ao "voltar" do navegador.
5. Enviar o formulário com um slug inválido (`Acme App`): a recusa aponta o campo e o que foi
   digitado permanece.
6. Acessar `/aplicacoes/nao-existe`: tela de não encontrado, com caminho de volta.

### US2 — Emitir e revogar chaves

1. Em uma aplicação, abrir "Chaves": estado vazio com a emissão em destaque.
2. Emitir uma chave com rótulo. O segredo aparece **uma vez**, com aviso e botão de cópia.
3. Fechar o diálogo e recarregar: o segredo não aparece em lugar nenhum — nem na tabela, nem no
   HTML da página.
4. Revogar a chave: a confirmação é exigida; depois, a situação muda e a ação some daquela linha.

### US3 — Montar uma pesquisa

1. Criar uma pesquisa informando o nome: nasce em rascunho, sem perguntas e sem disparo.
2. Adicionar uma pergunta `single_choice` sem opções: o formulário recusa antes de enviar.
3. Adicionar perguntas de três tipos diferentes (`single_choice`, `nps`, `free_text`).
4. Editar o enunciado de uma; remover outra (com confirmação); reordenar as restantes.
5. Recarregar: a montagem está exatamente como ficou.
6. Descartar a pesquisa (nunca publicada) e conferir que sumiu da listagem.

### US4 — Disparo e regras

1. Abrir "Disparo": estado "não configurado".
2. Definir evento e janela; salvar. Redefinir com outro evento: substitui, não duplica.
3. Adicionar uma regra `equals` (com valor) e uma `present` (sem campo de valor).
4. Remover uma: a outra permanece.

### US5 — Publicar e versionar

1. Com a pesquisa incompleta, abrir "Publicação": os impedimentos aparecem e o botão fica
   desabilitado.
2. Resolver o que falta; a lista esvazia e o botão libera.
3. Publicar: a versão 1 aparece em "Versões" com o conteúdo congelado.
4. Abrir nova versão de rascunho: volta a editar sem alterar o que está no ar.
5. Descartar o rascunho de versão (com confirmação): sobra a versão publicada.

### US6 — Ciclo de vida

1. Na pesquisa publicada, o cabeçalho oferece apenas as transições que a API autoriza.
2. Pausar → retomar → encerrar (com aviso de irreversibilidade).
3. Encerrada: nenhuma transição oferecida, conteúdo somente leitura.

---

## Testes

```bash
npm run lint         # ESLint
npm run typecheck    # tsc --noEmit, modo estrito
npm run test         # Vitest: schemas, cliente HTTP, apresentadores, componentes cliente
npm run test:e2e     # Playwright: os cinco fluxos críticos
npm run verify       # tudo acima — é o portão de merge da constituição
```

### O que o Vitest cobre

Módulos puros e componentes cliente. **Server Components assíncronos não são testados em
unidade** — limitação documentada pelo próprio Next.js. Por isso a lógica vive fora deles, e esses
fluxos são cobertos por Playwright. Rede real é proibida: `fetch` é substituído por
`vi.stubGlobal` nos testes do cliente HTTP.

### Como o E2E encontra uma API

O `fetch` do painel roda no servidor Node, fora do alcance da interceptação do Playwright. Por
isso o `playwright.config.ts` sobe **dois** servidores: o simulador de API em `e2e/stub-api/` e o
Next apontando para ele.

```bash
npm run test:e2e                 # contra o simulador (padrão, sem backend)
E2E_API=real npm run test:e2e    # contra o backend real em PITACO_API_URL
```

Cada teste cria sua própria aplicação e trabalha só dentro dela — é o que dá isolamento com
`fullyParallel: true`, sem reset global. Quando o simulador divergir do backend, o árbitro é
[`contracts/backend-api.md`](./contracts/backend-api.md).

---

## Limites conhecidos desta entrega

- **Sem login**: a superfície administrativa do backend ainda não autentica pessoas. O painel
  assume ambiente confiável.
- **Sem resultados**: não há endpoint de leitura de respostas; nenhuma tela mostra resultado.
- **Sem SDK e sem aplicação de teste**: fora de escopo por decisão.

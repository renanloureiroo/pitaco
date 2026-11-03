# Contrato exposto — rotas e estados de tela

A interface é o contrato que o painel oferece a quem o usa e aos testes E2E. Este documento fixa
as rotas, os parâmetros, os estados de cada tela e os `data-testid` estáveis dos quais o
Playwright depende.

**Convenções**

- Segmentos em português, sem acento ([R11](../research.md#r11)).
- Em Next 16, `params` e `searchParams` são `Promise` — as páginas usam `PageProps<'/rota'>` e
  `LayoutProps<'/rota'>`, helpers globais (sem import).
- Toda tela de leitura tem quatro estados distinguíveis: **carregando** (`loading.tsx` ou
  `<Suspense>`), **conteúdo**, **vazio** e **falha** (`error.tsx`, com `retry()`).
- Seletores dos testes: papel acessível primeiro; `data-testid` só onde o papel não desambigua.

---

## Mapa de rotas

| Rota | Params | SearchParams | Leituras |
|---|---|---|---|
| `/` | — | — | redireciona para `/aplicacoes` |
| `/aplicacoes` | — | `status?`, `page?`, `size?` | `listApplications` |
| `/aplicacoes/nova` | — | — | — |
| `/aplicacoes/[applicationId]` | `applicationId` | — | `getApplication` |
| `/aplicacoes/[applicationId]/chaves` | `applicationId` | `status?`, `page?`, `size?` | `listApiKeys` |
| `/aplicacoes/[applicationId]/pesquisas` | `applicationId` | `page?`, `size?` | `listSurveys` |
| `/aplicacoes/[applicationId]/pesquisas/nova` | `applicationId` | — | — |
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]` | + `surveyId` | — | `getSurvey`, `getTransitions` |
| `.../[surveyId]/disparo` | + `surveyId` | — | `getSurvey` |
| `.../[surveyId]/publicacao` | + `surveyId` | — | `getPublicationImpediments`, `getSurvey` |
| `.../[surveyId]/versoes` | + `surveyId` | `page?`, `size?` | `listVersions`, `getVersionComparability` |
| `.../[surveyId]/versoes/[number]` | + `number` | — | `getVersion` |

O layout de `[applicationId]` monta o breadcrumb com o nome da aplicação; essa leitura fica sob
`<Suspense>` dentro do layout, para não bloquear a navegação ([R6](../research.md#r6)).

O layout de `[surveyId]` monta o cabeçalho da pesquisa (nome, estado, transições permitidas), do
mesmo jeito.

---

## `/aplicacoes` — listagem

**Conteúdo**: tabela com nome, slug, situação (badge), data de criação; ação "Nova aplicação";
filtro de situação; paginação.

**Vazio**: mensagem convidando ao cadastro, com a ação em destaque — nunca erro.

**Testids**: `applications-table`, `application-row`, `application-status-filter`,
`new-application-link`, `pagination-next`, `pagination-prev`, `empty-state`, `error-state`,
`retry-button`.

**Comportamento**: filtro e página vivem na URL; voltar de uma tela de detalhe preserva ambos.

---

## `/aplicacoes/nova` — cadastro

**Campos**: nome (obrigatório), slug (opcional), descanso, retenção, retenção de texto livre.

**Sucesso**: redireciona para o detalhe da aplicação criada.

**Recusa**: mensagem por campo, vinda de `errors` do backend; o que foi digitado permanece.

**Testids**: `application-form`, `field-error-<campo>`, `form-error`, `submit-button`.

**Envio duplicado**: `pending` do `useActionState` desabilita o submit.

---

## `/aplicacoes/[applicationId]` — detalhe

**Conteúdo**: nome, slug, situação, datas, e os três prazos. Prazo ausente é renderizado como
"não configurado" — **nunca** `0`. Links para chaves e pesquisas.

**Não encontrado**: `notFound()` → `not-found.tsx` do segmento, com caminho de volta à listagem.

**Testids**: `application-detail`, `quiet-period`, `retention`, `open-text-retention`.

---

## `/aplicacoes/[applicationId]/chaves`

**Conteúdo**: tabela com rótulo, prefixo, situação, emissão, revogação; ação "Emitir chave";
ação "Revogar" apenas em chaves `active`.

**Emissão**: ao concluir, abre `Dialog` com o segredo, aviso de exibição única e botão de cópia.
Ao fechar, o segredo é descartado do estado do componente.

**Revogação**: `AlertDialog` de confirmação antes de executar.

**Testids**: `api-keys-table`, `api-key-row`, `issue-key-button`, `issue-key-form`,
`secret-dialog`, `secret-value`, `copy-secret-button`, `close-secret-dialog`,
`revoke-key-button`, `confirm-dialog`, `confirm-button`, `cancel-button`.

**Invariante testada**: após fechar o diálogo ou recarregar, `secret-value` não existe em lugar
nenhum do documento.

---

## `/aplicacoes/[applicationId]/pesquisas`

**Conteúdo**: tabela com nome, estado (badge), versão publicada, criação; ação "Nova pesquisa".

**Vazio**: mensagem com a ação em destaque.

**Testids**: `surveys-table`, `survey-row`, `survey-state-badge`, `new-survey-link`.

---

## `.../pesquisas/[surveyId]` — montagem

**Cabeçalho (layout)**: nome (editável), estado, transições permitidas, navegação entre
montagem / disparo / publicação / versões.

**Conteúdo**: sequência de perguntas na ordem de `position`, cada uma com enunciado, tipo,
obrigatoriedade e opções; ações adicionar, editar, remover, mover.

**Somente leitura** quando `state` é `ended`, ou quando `content.source` é `published` e não há
rascunho de versão aberto.

**Reordenação**: mover para cima/baixo envia a permutação completa dos identificadores. Com uma
única pergunta, a ação não é oferecida.

**Formulário de pergunta**: o campo de opções aparece apenas para `single_choice` e
`multiple_choice`, e é exigido antes do envio; o campo de faixa aparece para `rating` e `scale`.

**Testids**: `survey-header`, `survey-state`, `rename-survey-button`, `questions-list`,
`question-item`, `add-question-button`, `question-form`, `question-type-select`,
`question-options`, `add-option-button`, `edit-question-button`, `remove-question-button`,
`move-question-up`, `move-question-down`, `discard-survey-button`.

---

## `.../pesquisas/[surveyId]/disparo`

**Conteúdo**: disparo (evento, início e fim da janela, taxa de amostragem) e a lista de regras
vigentes, exibidos juntos.

**Sem disparo**: estado explícito "não configurado" com a ação de definir.

**Regra**: o campo de valor aparece e é exigido só em `equals` e `not_equals`.

**Testids**: `trigger-panel`, `trigger-form`, `event-name-input`, `window-start-input`,
`window-end-input`, `sampling-rate-input`, `rules-list`, `rule-item`, `add-rule-button`,
`rule-operation-select`, `rule-value-input`, `remove-rule-button`.

---

## `.../pesquisas/[surveyId]/publicacao`

**Conteúdo**: lista de impedimentos traduzidos em frases acionáveis; quando há `questionKey`, o
item liga à pergunta correspondente na montagem. Lista vazia libera o botão de publicar.

**A partir da versão 2**: campos de natureza da mudança (`cosmetic`/`semantic`) e resumo.

**Testids**: `impediments-list`, `impediment-item`, `publish-button`, `publish-form`,
`change-kind-select`, `change-summary-input`.

**Invariante testada**: com impedimento presente, `publish-button` está desabilitado. Clique duplo
não cria duas versões.

---

## `.../pesquisas/[surveyId]/versoes` e `.../versoes/[number]`

**Listagem**: número, situação, publicação, natureza da mudança, grupo de comparabilidade —
da mais recente para a mais antiga. Painel de comparabilidade mostra os grupos.

**Detalhe**: conteúdo congelado (perguntas e disparo daquela versão), somente leitura.

**Ações**: abrir nova versão de rascunho; descartar rascunho de versão (com confirmação).

**Testids**: `versions-table`, `version-row`, `comparability-panel`, `open-draft-version-button`,
`discard-draft-version-button`, `version-detail`, `version-questions`.

---

## Ciclo de vida (no cabeçalho da pesquisa)

**Ações oferecidas**: exatamente as que `getTransitions` autoriza — nada é derivado do estado.

**Encerrar**: `AlertDialog` avisando que é irreversível.

**Encerrada**: nenhuma transição oferecida; conteúdo somente leitura.

**Testids**: `pause-survey-button`, `resume-survey-button`, `end-survey-button`,
`transitions-history`.

---

## Componentes compartilhados e seus testids

| Componente | Testid | Onde |
|---|---|---|
| `EmptyState` | `empty-state` | toda listagem |
| `ErrorState` | `error-state`, `retry-button` | todo `error.tsx` |
| `ConfirmDialog` | `confirm-dialog`, `confirm-button`, `cancel-button` | toda ação destrutiva |
| `Pagination` | `pagination-prev`, `pagination-next`, `pagination-info` | toda listagem paginada |
| `PageHeader` | `page-header`, `breadcrumb` | todas as telas |

---

## Fluxos cobertos por E2E (SC-006)

1. `aplicacoes.spec.ts` — cadastrar aplicação, ver na lista, filtrar, abrir detalhe.
2. `chaves.spec.ts` — emitir chave, ver o segredo uma vez, recarregar e confirmar a ausência,
   revogar com confirmação.
3. `pesquisas.spec.ts` — criar pesquisa, adicionar perguntas de tipos diferentes, editar, remover,
   reordenar, recarregar e conferir persistência.
4. `publicacao.spec.ts` — consultar impedimentos com pesquisa incompleta, completar, publicar,
   conferir a versão na listagem.
5. `ciclo-de-vida.spec.ts` — pausar, retomar, encerrar com confirmação, conferir que encerrada não
   oferece transição.

Cada teste cria sua própria aplicação e opera só dentro dela ([R9](../research.md#r9)).

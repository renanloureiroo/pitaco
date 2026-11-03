# Contrato exposto — rotas, estados de tela e testids

Continuação dos contratos de rotas de [001](../../001-painel-operacao-pesquisas/contracts/ui-routes.md)
e [002](../../002-painel-leitura-coleta/contracts/ui-routes.md), cujas convenções valem aqui.

---

## Rotas novas

| Rota | Params | SearchParams | Leituras |
|---|---|---|---|
| `/aplicacoes/[applicationId]/saude` | `applicationId` | `tipo?`, `versao?`, `page?`, `size?` | `getSdkVersions`, `listSdkErrors` |
| `/aplicacoes/[applicationId]/privacidade` | `applicationId` | `page?`, `size?` | `getRetentionPreview`, `listDeletionAudits` |

As duas ficam sob o layout da aplicação (trilha) e são alcançadas pela navegação da aplicação, no
grupo **Operação** ("Saúde", "Privacidade").

## Rotas alteradas

| Rota | Mudança |
|---|---|
| `…/pesquisas/[surveyId]/resultados` | avisos de saúde no topo; nota de retenção após a taxa de resposta |
| `/aplicacoes/[applicationId]` | ação "Editar" abre o diálogo de edição da aplicação |

---

## `/aplicacoes/[applicationId]/saude`

**Ordem**: título + rótulo do fuso → **Versões do SDK em uso** (nota do critério, tabela ou vazio)
→ **Erros do SDK** (nota de privacidade, filtros, lista e paginação, ou vazio).

**Estados**:

| Situação | O que aparece |
|---|---|
| nenhuma versão | `empty-state` "Nenhum SDK falou com esta aplicação ainda" |
| nenhum erro, sem filtro | `sdk-errors-empty` "Nenhum erro reportado pelo SDK desta aplicação." |
| nenhum erro, com filtro | `sdk-errors-empty` "Nenhum erro reportado neste recorte." |
| aplicação inexistente | não encontrado |
| falha de leitura | `error.tsx` da aplicação, com nova tentativa |

**Testids**: `timezone-note`, `sdk-versions`, `sdk-versions-table`, `sdk-version-row`,
`sdk-version`, `sdk-version-share`, `sdk-version-stale`, `sdk-errors`, `sdk-error-filters`,
`filter-kind`, `filter-sdk-version`, `apply-filters`, `clear-filters`, `sdk-errors-list`,
`sdk-error-item`, `sdk-error-kind`, `sdk-error-version`, `sdk-error-message`,
`sdk-error-context-toggle`, `sdk-error-context`, `sdk-errors-empty`.

---

## `/aplicacoes/[applicationId]/privacidade`

**Ordem**: título + rótulo do fuso → **política de retenção** (descrição, aviso, último descarte,
"Editar prazos", "Exportar pelos resultados das pesquisas") → **Excluir respondente** →
**Exclusões feitas** (nota, lista, paginação).

**Formulário de exclusão**: tipo de identificação (`Referência do app` \| `Dispositivo`),
identificação, botão desabilitado sem identificação e durante o envio. Confirmação pelo
`ConfirmDialog` compartilhado: título "Excluir este respondente?", descrição com
"irreversível". Resultado em `erasure-result`.

**Estados**:

| Situação | O que aparece |
|---|---|
| sem política | descrição "Sem política de retenção…", sem aviso e sem convite a exportar |
| algo a descartar | `retention-warning` com data e contagens |
| descarte desligado | `retention-warning` dizendo que está desligado |
| nunca houve descarte | `retention-last-run` "Nenhum descarte aconteceu ainda nesta aplicação." |
| exclusão concluída | `erasure-result` "Excluído: N exibições e M respostas apagadas." |
| exclusão sem alvo | `erasure-result` "… Nada foi apagado." |
| sem exclusões | `deletion-audits-empty` |

**Testids**: `timezone-note`, `retention-policy`, `retention-policy-description`,
`retention-warning`, `retention-last-run`, `erase-respondent`, `erase-respondent-form`,
`erase-identity-kind`, `erase-identity-input`, `erase-respondent-button`, `confirm-dialog`,
`confirm-button`, `cancel-button`, `erasure-result`, `deletion-audits`, `deletion-audits-table`,
`deletion-audit-row`, `deletion-audit-displays`, `deletion-audit-answers`,
`deletion-audits-empty`.

---

## Resultados — acréscimos

**Testids**: `survey-health-notices`, `survey-health-suppression`, `survey-health-event-missing`,
`retention-note`.

## Diálogo de edição da aplicação

**Campos**: Nome; Período de descanso (dias); Retenção (dias) — "Em branco, nada é descartado
por tempo."; Retenção de texto livre — "Em branco, o texto livre segue o prazo geral."

**Testids**: `edit-application-button`, `edit-application-form`, `edit-application-submit`; no
detalhe, `quiet-period`, `retention`, `open-text-retention`.

---

## Fluxos cobertos por E2E

| Arquivo | Cenário |
|---|---|
| `e2e/saude.spec.ts` | versões com fatia e marca "sumiu do tráfego", navegando pela barra lateral |
| `e2e/saude.spec.ts` | erros listados, filtrados por tipo na URL, com contexto |
| `e2e/saude.spec.ts` | supressão relevante nos resultados com motivo e versão mínima, sem vazio |
| `e2e/saude.spec.ts` | evento nunca visto distinguível de supressão, e que some quando o evento chega |
| `e2e/privacidade.spec.ts` | exclusão: cancelar não apaga; confirmar apaga, registra sem a referência e tira da listagem |
| `e2e/privacidade.spec.ts` | exclusão sem alvo: "Nada foi apagado", sem registro |
| `e2e/privacidade.spec.ts` | prévia com prazo de 30 dias: "1 resposta e 1 texto livre serão descartados", "Nenhum descarte" |
| `e2e/privacidade.spec.ts` | nota de retenção com e sem o agregado conforme o recorte |
| `e2e/aplicacoes.spec.ts` | edição de nome e prazos com prazo em branco removido; recusa de texto livre acima da geral sem fechar o diálogo |

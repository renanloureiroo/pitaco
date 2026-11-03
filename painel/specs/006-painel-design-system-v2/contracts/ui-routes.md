# Contrato exposto — shell, Visão geral, tema e celular

Continuação dos contratos de rotas de 001 a 005. Nenhuma rota nova; uma rota muda de conteúdo e
os layouts mudam de forma.

---

## Layouts

| Arquivo | Antes | Agora |
|---|---|---|
| `src/app/layout.tsx` | `<html lang="pt-BR">` | + fontes Inter e JetBrains Mono, classe `dark`, `ThemeProvider`, `Toaster` |
| `src/app/aplicacoes/layout.tsx` | — | shell: `AppSidebar` (sob `<Suspense>`) + conteúdo |
| `src/app/aplicacoes/[applicationId]/layout.tsx` | trilha + `SectionNav` da aplicação | só a trilha (`breadcrumb`), conteúdo até 1280px |
| `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx` | abas da pesquisa | as mesmas abas, com rolagem horizontal |

## Rota alterada

| Rota | Params | SearchParams | Leituras |
|---|---|---|---|
| `/aplicacoes/[applicationId]` | `applicationId` | `periodo?` (`7` \| `30` \| `90`), `segmento?` | `getApplication`, `getApplicationOverview`, `getSegmentBreakdown` |

---

## Barra lateral (`AppSidebar`)

**Fora de uma aplicação**: marca, troca de tema, seletor ("Todas as aplicações"), item
Aplicações.

**Dentro de uma aplicação**:

| Grupo | Item | Destino | Ativo quando |
|---|---|---|---|
| Análise | Visão geral | `/aplicacoes/{id}` | caminho exato |
| Análise | Pesquisas | `/aplicacoes/{id}/pesquisas` | caminho ou subcaminho |
| Análise | Respondentes | `/aplicacoes/{id}/respondentes` | idem |
| Operação | Chaves | `/aplicacoes/{id}/chaves` | idem |
| Operação | Saúde | `/aplicacoes/{id}/saude` | idem |
| Operação | Privacidade | `/aplicacoes/{id}/privacidade` | idem |

**Seletor**: menu com cada aplicação (ponto de situação), "Todas as aplicações" e "Nova aplicação".

**Celular (abaixo de `lg`)**: barra superior com "Abrir menu" e a marca; gaveta com `role="dialog"`,
`aria-modal="true"`, `aria-label="Menu"`, botão "Fechar menu", fundo clicável, Escape.

**Testids e papéis**: `app-sidebar`, `application-switcher`, `theme-toggle` (rótulo "Alternar tema
claro e escuro"); navegação com `aria-label` "Navegação principal" ou "Navegação da aplicação";
item atual com `aria-current="page"`.

---

## `/aplicacoes/[applicationId]` — Visão geral

**Ordem**: cabeçalho (nome; "Visão geral · N pesquisas, M no ar · R respondentes conhecidos";
período; Editar; Desativar/Reativar) → KPIs (4) → tendência (2/3) + desfechos (1/3) → NPS
consolidado (1/3) + análise por segmento (2/3) → desempenho por pesquisa → respondentes, versões
do SDK, erros do SDK → Configuração.

**KPIs**:

| Testid | Rótulo | Valor | Variação | Nota |
|---|---|---|---|---|
| `kpi-displayed` | Exibições | soma | relativa, bom para cima | "vs. N dias anteriores" |
| `kpi-rate` | Taxa de resposta | concluídas ÷ exibidas | pontos, bom para cima | "N concluídas" |
| `kpi-nps` | NPS | consolidado | pontos de NPS, bom para cima | "N respostas" ou "nenhuma pesquisa de NPS" |
| `kpi-abandon` | Abandono | abandonadas ÷ exibidas | pontos, bom para baixo | "N exibições" |

Cada KPI expõe também `{testid}-value`.

**Estados**:

| Situação | O que aparece |
|---|---|
| sem pesquisas | `empty-state` "Nenhuma pesquisa ainda" + "Nova pesquisa"; Configuração visível |
| sem pesquisa de NPS com resposta | análise por segmento: "Precisa de uma pesquisa de NPS com respostas no período" |
| sem atributo | "Nenhum atributo recebido no período…" |
| sem tráfego de SDK | "Nenhum tráfego de SDK na janela recente." |
| aplicação inexistente | não encontrado |
| lista de pesquisas indisponível | estado de falha com nova tentativa |

**Testids**: `application-overview`, `kpi-displayed`, `kpi-rate`, `kpi-nps`, `kpi-abandon`,
`trend-chart`, `outcome-breakdown`, `outcome-completed`, `outcome-dismissed`, `outcome-abandoned`,
`outcome-inProgress`, `nps-breakdown`, `nps-breakdown-score`, `segment-breakdown`, `segment-row`,
`survey-performance`, `survey-performance-row`, `sdk-adoption`, `application-detail`,
`edit-application-button`, `deactivate-application-button`, `activate-application-button`.

**Vínculos de período e segmento**: `?periodo={p}&segmento={atributo}`; trocar um preserva o outro.

---

## Tema

| Situação | Resultado |
|---|---|
| primeira visita | escuro |
| `theme-toggle` | alterna claro ⇄ escuro, sem recarregar |
| navegação | o tema escolhido se mantém |

---

## Fluxos cobertos por E2E

| Arquivo | Cenário |
|---|---|
| `e2e/smoke.spec.ts` | a raiz leva a `/aplicacoes` com título de página visível |
| `e2e/coleta.spec.ts` | Respondentes alcançado pela barra lateral |
| `e2e/saude.spec.ts` | Saúde alcançada pela barra lateral |
| `e2e/privacidade.spec.ts` | Privacidade alcançada pela barra lateral |
| `e2e/aplicacoes.spec.ts` | desativar com confirmação e reativar; cancelar mantém ativa; edição pelo detalhe |

A Visão geral, a troca de tema e a gaveta do celular não têm cenário E2E próprio nesta entrega.

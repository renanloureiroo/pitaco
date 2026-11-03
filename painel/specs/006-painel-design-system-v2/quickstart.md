# Quickstart — validar o Design System v2, o shell e a Visão geral

Como provar que esta feature funciona. Regras visuais em `DESIGN.md`, tokens em
`src/app/globals.css`, contrato em [contracts/](./contracts/ui-routes.md).

---

## Pré-requisitos

- Node 20+ e dependências instaladas (`npm ci`)
- Para a Visão geral com números: backend no ar com a demo carregada (`seed/`), que cria
  aplicações com pesquisas de NPS, atributos, respostas e tráfego de SDK

---

## Portão de qualidade

```bash
npm run verify
```

Isoladamente:

```bash
npm test -- src/features/analytics src/shared src/components/ui
npm run test:e2e -- smoke.spec.ts aplicacoes.spec.ts coleta.spec.ts saude.spec.ts privacidade.spec.ts
```

---

## Roteiro de verificação manual

### 1. Barra lateral (US1)

| Passo | O que conferir |
|---|---|
| Abrir `/` | vai para `/aplicacoes`; barra com marca, tema, seletor "Todas as aplicações" |
| Abrir uma aplicação | grupos Análise e Operação; Visão geral ativa |
| Abrir uma pesquisa | Pesquisas continua ativa; abas da pesquisa abaixo do cabeçalho |
| Seletor → outra aplicação | Visão geral da outra |
| Seletor → Nova aplicação | `/aplicacoes/nova`, sem item de aplicação ativo |

### 2. Visão geral (US2, US3, US4)

| Passo | O que conferir |
|---|---|
| Abrir a Visão geral | quatro KPIs, tendência, desfechos, NPS, segmento, desempenho, SDK, configuração |
| Trocar para 7 e 90 dias | URL com `periodo`; todos os números mudam |
| KPI com variação | seta, valor, cor pelo sentido bom; leitor de tela diz "subiu"/"caiu" |
| Conferir a taxa | concluídas ÷ exibidas da soma, não média das pesquisas |
| Passar o cursor na tendência | exibidas, concluídas e a taxa do dia |
| Escolher outro atributo no segmento | URL com `segmento`; período mantido |
| Barra divergente | NPS negativo à esquerda em vermelho, positivo à direita em verde |
| Clicar numa pesquisa da tabela | resultados dela |
| `?periodo=15` | 30 dias |
| Aplicação sem pesquisas | "Nenhuma pesquisa ainda" com "Nova pesquisa" |

### 3. Ativar e desativar (US5)

| Passo | O que conferir |
|---|---|
| Desativar → cancelar | continua ativa |
| Desativar → confirmar | inativa no cabeçalho, na configuração e na listagem; botão vira Reativar |
| Reativar | ativa, sem confirmação |

### 4. Tema (US6)

| Passo | O que conferir |
|---|---|
| Primeira visita (armazenamento limpo) | escuro |
| Botão de tema | claro em todo o painel, sem recarregar |
| Navegar por Visão geral, Resultados, Montagem | tema mantido; gráficos e pré-visualização acompanham |
| Ferramenta de contraste do navegador | texto ≥ 4.5:1 nos dois temas |

### 5. Celular (US7)

| Passo | O que conferir |
|---|---|
| Largura de 360px | barra superior com menu e marca; sem barra lateral fixa |
| Abrir o menu | gaveta modal; Escape, fundo e botão fecham |
| Seguir um item da gaveta | gaveta fecha, tela muda |
| Visão geral e Resultados | uma coluna; tabelas rolam sozinhas; página sem rolagem horizontal |
| Abas da pesquisa | rolam de lado |

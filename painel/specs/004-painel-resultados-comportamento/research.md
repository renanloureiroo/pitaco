# Phase 0 — Pesquisa e decisões

As decisões de [001](../001-painel-operacao-pesquisas/research.md) e
[002](../002-painel-leitura-coleta/research.md) continuam valendo — leitura em Server Component,
resultado tipado, estado de listagem na URL, fuso de referência rotulado — e não são redecididas.

---

## R1 — Feature nova `results`

**Decisão**: `src/features/results/`, espelhando o módulo `results` do backend. A feature não
importa nenhuma outra. As páginas em `app/` compõem `results` com `surveys`
(`getVersionComparability`, para o seletor de versão) e com `health` (avisos de supressão e de
evento nunca visto).

**Rationale**: o resultado é uma leitura derivada da coleta, com regras que não são de autoria
nem de coleta item a item: recorte, comparabilidade entre versões somadas, amostra pequena,
retenção. O backend separou pelo mesmo motivo. `collect` (002) continua sendo item a item; a
regra de 002 de "nenhum número" vale para `collect`, não para `results`.

**Alternativas consideradas**:
- *Estender `collect`*: rejeitada. Quebraria a regra de 002 dentro da própria feature.
- *Estender `surveys`*: rejeitada. Misturaria autoria com leitura derivada de coleta.

---

## R2 — Um recorte, lido uma vez, para todas as leituras

**Decisão**: o recorte vive na URL (`periodo`, `de`, `ate`, `atributo`, `valor`, `ausente`,
`versao`). `parseResultsFilters` lê e saneia; `toResultsQuery` converte para o formato do backend
(`from`, `to`, `attribute`, `attributeValue`, `version`) uma única vez por requisição; e o mesmo
objeto vai para resultado, respostas abertas, comportamento e export.

**Regras de leitura** (valor inválido nunca quebra a tela):

| Situação na URL | Leitura |
|---|---|
| `periodo` em 7/30/90 | vira `from = agora − N dias`, sem `to`; vence `de`/`ate` |
| `de` > `ate` | os dois são descartados |
| `atributo` sem `valor` e sem `ausente=1` | não é recorte |
| `atributo` + `ausente=1` | enviado como `attribute` sem `attributeValue`: o backend lê como "sem o atributo" |
| `versao` não inteira ou < 1 | consolidado |
| `q` vazio ou acima de 200 caracteres | sem busca |

**Formulário**: `ResultsFiltersForm` (cliente) recusa período personalizado invertido sem
navegar; aplicar e limpar removem `page`. Limpar tira só os parâmetros do recorte, preservando o
resto da URL.

**Rationale**: a regra de produto é "um número sob um recorte e outro sob outro seria pior do que
nenhum". Ler e converter uma vez torna isso estrutural, não disciplinar.

**Alternativas consideradas**:
- *Recorte em estado de cliente*: rejeitada. Perderia compartilhamento e o "voltar" do navegador
  (R12 de 001).
- *Cada componente ler a URL*: rejeitada. Abre a porta para dois recortes na mesma tela.

---

## R3 — Export por Route Handler, em fluxo

**Decisão**: o botão aponta para `/api/aplicacoes/{a}/pesquisas/{s}/resultados/export?…` (rota
do próprio painel), montada por `exportHref` com o mesmo recorte da tela. O Route Handler
(`src/app/api/.../export/route.ts`) chama `backendExportUrl`, faz `fetch` com
`cache: "no-store"` e devolve `upstream.body` sem ler, preservando `content-type` e
`content-disposition` e o status do backend. Falha de rede vira `502` com texto simples.

**Confirmação**: `ExportButton` usa o `ConfirmDialog` compartilhado com `EXPORT_NOTICE`; o
download (`window.location.assign`) só acontece no `onConfirm`.

**Rationale**: o navegador não fala com o backend (decisão de 001). Server Action não serve para
download de arquivo. Repassar o corpo sem ler mantém o painel fora do caminho de memória de um CSV
grande, e o backend continua dono do nome do arquivo e do BOM.

**Alternativas consideradas**:
- *Server Action que devolve o CSV como string*: rejeitada. Carrega o arquivo inteiro no painel e
  no cliente.
- *Link direto para o backend*: rejeitada. Expõe a API ao navegador.

---

## R4 — Cada tipo na sua forma, com contagem e proporção juntas

**Decisão** (`QuestionResultCard`):

| Agregado | Forma | Regra |
|---|---|---|
| `choice` | barras horizontais por opção | contagem e proporção na mesma linha; cor `chart-1…8` pela **posição** da opção, para a mesma opção ter a mesma cor em todo recorte |
| `numeric` | média + histograma | cada coluna com valor, contagem e proporção no título |
| `nps` | pontuação com sinal + três grupos + histograma 0–10 | cores semânticas (`danger-fill` 0–6, `neutral-fill` 7–8, `success-fill` 9–10), nunca série |
| `text` | frase apontando para as respostas abertas | o texto não é agregável |
| ausente | "Ninguém respondeu esta pergunta ainda." | distinto de contagens zeradas |

**Ausências**: `formatRate(undefined)` é "—"; pontuação ausente não vira zero; não aplicáveis só
aparecem quando há, com `NOT_APPLICABLE_NOTE`.

**Rationale**: proporção sem contagem esconde amostra pequena; cor por posição permite comparar
dois recortes lado a lado; NPS usa semântica porque os grupos têm significado, não identidade.

---

## R5 — Comparabilidade dita por pergunta

**Decisão**: no consolidado, o backend devolve `comparability { comparable, versions }` por
pergunta. Incomparável vira um alerta (`comparability-warning`) com as versões em linguagem de
frase ("1 e 2", "1, 2 e 3") e a sugestão de escolher uma versão na visão; comparável entre mais
de uma versão vira uma nota discreta (`comparability-safe`). Visão por versão, ou uma versão só,
não traz marca.

**Rationale**: a decisão de separar grupos é tomada na publicação (`003-painel-autoria-avancada`);
o lugar de lembrar dela é onde a soma acontece, pergunta a pergunta, porque numa mesma pesquisa
umas mudam e outras não.

---

## R6 — Comportamento como o backend apura

**Decisão**: a aba Comportamento lê `GET …/results/behavior` com o mesmo recorte e mostra:
indicadores (instrumentadas e cobertura, tempo ativo como soma das medianas por pergunta, taxa de
troca de resposta como média entre as perguntas de escolha, dispensas e sua proporção sobre as
instrumentadas), funil, tempo por pergunta (mediana e p90 na mesma escala), vias de dispensa
(rótulos em português, as sem via contadas à parte), atrito e as definições do backend num
`details`. Os nomes de campo antigos e atuais do backend (`changeRate`/`answerChangeRate`,
`validationFailures`/`validationBlocks`) são aceitos por `changeRateOf` e `validationBlocksOf`.

**O que o painel calcula**: só os resumos de apresentação acima, e cada rótulo diz o que foi
somado ou calculado. Nenhuma métrica nova.

**Base**: exibições instrumentadas (ao menos um evento), decisão do backend para SDKs sem eventos
não diluírem as taxas. Zero instrumentadas é um vazio próprio, não uma tela de zeros.

**Resumo em Resultados**: `BehaviorSummary` mostra onde mais desistem, o tempo ativo e a troca de
resposta, com vínculo para a aba.

---

## R7 — Atualização sem recarregar

**Decisão**: `RefreshControl` chama `router.refresh()` no clique e a cada 30 segundos, só com a
aba visível (`document.visibilityState`), e mostra a hora da última atualização.

**Rationale**: resposta nova chega enquanto a tela está aberta; recarregar perderia a rolagem e o
foco. `router.refresh()` reexecuta só o Server Component. Nada de polling em aba de fundo.

---

## R8 — Três vazios e a leitura de saúde

**Decisão**: `resultsEmptyVariant` decide entre nunca publicada, sem exibição e sem exibição no
recorte, a partir de `everPublished`, `responseRate.displayed` e da presença de recorte. Quando a
saúde da pesquisa (`005-painel-saude-privacidade`) traz aviso de supressão, o vazio "sem exibição"
não aparece. A leitura de saúde é complementar: se falhar, a tela continua. As demais leituras
(resultado, respostas abertas, comportamento, versões) derrubam a tela para o `error.tsx` do
segmento, com nova tentativa.

---

## R9 — Servidor de API simulado

**Decisão**: `e2e/stub-api/routes/results.ts` apura resultado, respostas abertas, export e
comportamento a partir das exibições semeadas por `/stub/collect`, aplicando o mesmo recorte. O
E2E inteiro de resultados se pula com `E2E_API=real`: a coleta só nasce pelo SDK.

# Phase 0 — Pesquisa e decisões

As decisões de [001](../001-painel-operacao-pesquisas/research.md) e
[002](../002-painel-leitura-coleta/research.md) continuam valendo e não são redecididas.

---

## R1 — Duas features: `health` e `privacy`

**Decisão**: `src/features/health/` para versões, erros e saúde da pesquisa;
`src/features/privacy/` para retenção, exclusão e registro. Nenhuma importa a outra nem outra
feature.

**Rationale**: os dois eixos têm leitores e cuidados diferentes. Saúde fala do SDK e da entrega —
leitura técnica, sem dado de pessoa. Privacidade fala do dado do respondente — tem escrita
irreversível e uma regra dura (nunca exibir quem foi excluído). Juntá-los numa feature "operação"
misturaria as duas regras numa fronteira só.

**Alternativas consideradas**:
- *Saúde dentro de `collect`*: rejeitada. `collect` é leitura item a item de exibições; versões e
  erros do SDK não são exibição.
- *Retenção dentro de `applications`*: rejeitada para a leitura (prévia e registro). Os **prazos**,
  que são atributos da aplicação, continuam em `applications` e são editados lá ([R5](#r5)).

---

## R2 — Versões e erros: exibir o que o backend marca

**Decisão**: a tabela de versões mostra o que vem do backend, na ordem dele (versão mais nova
primeiro), com `stale` virando a marca "Sumiu do tráfego" e `recentShare` ausente virando "—". A
nota `STALE_NOTE` explica o critério de 14 dias e a diferença entre fatia recente e total.

**Erros**: `sdkErrorReportSchema` usa `.catch("unknown")` no tipo e `.catch({})` no contexto: um
tipo novo do backend ou um contexto malformado não derrubam a lista. O contexto fica recolhido
(`sdk-error-context-toggle`), porque a lista serve para achar o padrão e o detalhe serve a quem
investiga.

**Filtros**: `tipo` e `versao` na URL, lidos por `parseSdkErrorFilters` (tipo inválido ignorado,
versão cortada em 40 caracteres, primeiro valor de parâmetro repetido). O formulário cliente volta
à primeira página ao filtrar e limpar.

---

## R3 — Saúde da pesquisa vira aviso, e é complementar

**Decisão**: `surveyHealthNotices` transforma a leitura de saúde em até dois avisos com
linguagem própria:

| Condição | Aviso | Texto essencial |
|---|---|---|
| `relevant` | `suppression` | proporção das vezes em que seria exibida, motivo dominante, versão mínima, "não é falta de interesse" |
| `eventName` presente e `eventLastSeenAt` ausente | `event_missing` | o evento nunca foi recebido; conferir o nome no disparo e a integração |

A página de resultados lê a saúde em paralelo e, se a leitura falhar, segue sem avisos. Com aviso
de supressão, o vazio "Nenhuma exibição ainda" é omitido.

**Rationale**: as duas causas pedem correções opostas — atualizar o SDK do app ou corrigir o
nome do evento — e nenhuma é "esperar mais". Mas a saúde não é a razão de existir da tela de
resultados; sua indisponibilidade não pode esconder os números.

**Alternativas consideradas**:
- *Tela própria de saúde por pesquisa*: rejeitada. O aviso precisa estar onde a pessoa percebe o
  zero.

---

## R4 — Exclusão: Server Action, confirmação, `refresh()`

**Decisão**: `EraseRespondentForm` envia `identityKind` (`reference` \| `device`) e `identity`
pelo `ConfirmDialog` compartilhado, com o título "Excluir este respondente?" e a descrição de
`erasureDescription` (irreversível, sem lixeira, registro sem quem). `eraseRespondentAction`
valida, chama `DELETE /applications/{a}/respondents?reference=…` ou `?deviceId=…`, e termina em
`refresh()`, devolvendo o resultado no estado da action para a tela dizer quanto saiu.

**Rationale**: a exclusão termina na mesma tela, e o registro novo precisa aparecer na lista ao
lado. `refresh()` realinha a página sem navegar.

**Pedido sem alvo**: `deleted: false` não é erro — é "Nada foi apagado", e o backend não cria
registro.

---

## R5 — Prazos editados na aplicação

**Decisão**: o diálogo `EditApplicationDialog` (em `applications`) edita nome, descanso, retenção
e retenção de texto livre por `PATCH /applications/{a}`. Como o diálogo sempre mostra os três
prazos, o que fica em branco vai como `null` — a única forma de remover um prazo pela tela. A
recusa de regra do backend (texto livre maior que a geral) volta como mensagem no diálogo, que
continua aberto com os valores.

A tela de Privacidade não edita: ela lê a prévia e leva à aplicação ("Editar prazos").

**Rationale**: os prazos são atributos da aplicação, e o mesmo diálogo atende a quem quer mudar o
nome ou o descanso. Duplicar o formulário em Privacidade criaria dois lugares para o mesmo dado.

---

## R6 — Prévia de retenção em linguagem de gente

**Decisão**: `describeRetentionPolicy` descreve os prazos (o de texto livre só quando difere do
geral) e lembra que o agregado continua; `retentionWarning` escolhe entre "na próxima execução"
(com data e contagens), "até uma semana depois" e "descarte desligado nesta instância", ou nada;
`lastRunMessage` diz se já houve descarte. Datas no fuso de referência.

**Rationale**: a prévia existe para dar tempo de exportar. Um aviso sem data e sem número não
cumpre essa função.

---

## R7 — Nota de retenção nos resultados

**Decisão**: `RetentionNote` (em `results`) mostra `retentionNoteText`:
- `snapshotApplied` verdadeiro → "Inclui agregados de respostas descartadas … antes de {data}. As
  respostas em si não aparecem nas respostas abertas nem no export."
- falso → "Respostas dadas antes de {data} … não entram neste recorte: o agregado guardado não
  tem data nem atributo."

**Rationale**: o backend congela o agregado antes de apagar; o painel precisa dizer quando esse
agregado entrou na conta, para uma queda não ser lida como queda de resposta.

---

## R8 — Servidor de API simulado

**Decisão**: `e2e/stub-api/routes/health.ts` (versões, erros, saúde da pesquisa e as semeaduras
`/stub/sdk-versions`, `/stub/sdk-errors`, `/stub/suppressions`) e `privacy.ts` (exclusão, registro,
prévia e `/stub/retention-snapshots`). Os testes que dependem de semeadura se pulam com
`E2E_API=real`, com o motivo escrito.

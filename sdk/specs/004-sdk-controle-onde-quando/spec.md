# Feature Specification: SDK React Native — controle de onde e quando a pesquisa aparece

**Feature Branch**: `004-sdk-controle-onde-quando`

**Created**: 2026-09-13

**Status**: Implemented

**Input**: User description: "O app precisa mandar em onde e quando a pesquisa aparece: nada na tela de pagamento, uma por sessão e dar pra segurar e soltar depois."

## Contexto

O servidor decide **se** uma pessoa deve ver uma pesquisa. Ele não sabe **onde** ela está no app
nem **quando** é um bom momento: a pesquisa que chega no meio do pagamento atrapalha a conversão;
a segunda pesquisa no mesmo uso do app vira incômodo; a pesquisa disparada numa tela às vezes
precisa esperar a próxima para aparecer.

Esta feature dá ao app esse controle, sem tirar do servidor a decisão de elegibilidade:

- **Uma pesquisa por sessão de app**: depois que uma pesquisa foi exibida, nenhuma outra aparece
  até o app ser reaberto — e o SDK nem consulta o servidor nesse intervalo.
- **Bloqueio**: telas em que nada pode aparecer declaram um bloqueio; a pesquisa que chega durante
  ele fica retida e aparece quando a tela libera, dentro de um prazo.
- **Adiamento**: o app segura a próxima pesquisa e a solta quando for oportuno, dentro do mesmo
  prazo.

Tudo o que acontece antes de existir exibição — pesquisa disponível, retida, adiada, liberada,
descartada, limite de sessão — é contado ao app por eventos `placement_`, só pelo `onEvent`, fora
do catálogo enviado ao servidor.

## Clarifications

### Session 2026-09-13

- Q: O que é "sessão de app"? → A: O tempo de vida do runtime do SDK, sem persistência. Um processo
  novo nasce com o limite zerado — é o próprio "reabrir o app zera o limite".
- Q: O limite conta oferta ou exibição? → A: Exibição aberta de fato. Uma pesquisa oferecida que
  nunca ficou visível não consome a sessão; uma exibida e dispensada sem resposta consome.
- Q: Bloqueio interrompe uma pesquisa já aberta? → A: Não. Só segura a que chegar enquanto ele
  estiver ativo.
- Q: Dois bloqueios com o mesmo motivo se anulam quando um sai? → A: Não. Bloqueio conta
  referências por motivo; só libera quando o último sai.
- Q: Quanto tempo uma pesquisa retida espera? → A: `deferTimeoutMs`, 5 minutos por padrão,
  configurável entre 1 s e 1 h, o mesmo prazo para bloqueio e adiamento. O relógio começa quando a
  pesquisa é retida, não quando o bloqueio ou o adiamento foi pedido.
- Q: O que acontece com a pesquisa retida no logout? → A: É descartada sem exibição — ela foi
  consultada para quem está saindo. Bloqueios e adiamento pedidos continuam, e o limite de sessão
  também.
- Q: Quantas pesquisas podem ficar retidas? → A: Uma. Outra que chegar enquanto há uma retida é
  ignorada.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - No máximo uma pesquisa por uso do app (Priority: P1)

Depois que uma pesquisa foi exibida — respondida, dispensada ou abandonada —, novos `track` no
mesmo uso do app não mostram outra e nem chegam ao servidor. Ao reabrir o app, o limite volta a
zero.

**Why this priority**: proteger a experiência de quem usa o app é a primeira condição para o app
aceitar o Pitaco; uma sequência de pesquisas é o motivo mais comum de desinstalar um SDK desses.

**Independent Test**: exibir e dispensar uma pesquisa, chamar `track` de novo e conferir que
nenhuma consulta saiu e que o `onEvent` recebeu `placement_session_limited`; recriar o runtime (ou
`simulateAppReopen()`) e conferir que a consulta volta a sair.

**Acceptance Scenarios**:

1. **Given** uma pesquisa exibida nesta sessão, mesmo dispensada sem resposta, **When** o app chama
   `track`, **Then** nenhuma consulta de elegibilidade sai e o `onEvent` recebe
   `placement_session_limited` com o nome do evento.
2. **Given** uma pesquisa oferecida mas nunca apresentada, **When** o app chama `track` de novo,
   **Then** a consulta sai normalmente.
3. **Given** o limite atingido, **When** o app é reaberto (runtime novo), **Then** a próxima
   consulta sai.
4. **Given** uma ferramenta de diagnóstico, **When** ela chama `simulateAppReopen()`, **Then** o
   limite volta a zero sem recriar identidade nem fila.
5. **Given** um logout (`reset()`), **When** o app chama `track`, **Then** o limite da sessão
   continua valendo.

---

### User Story 2 - Nada aparece na tela de pagamento (Priority: P1)

A tela de pagamento monta `<PitacoBlock reason="pagamento" />` (ou chama `block`/`unblock`). Se um
`track` trouxer uma pesquisa enquanto o bloqueio está ativo, ela fica retida; quando a tela sai, a
pesquisa aparece — se ainda estiver dentro do prazo.

**Why this priority**: é o pedido mais concreto de quem tem fluxo de conversão; sem ele, o app teria
de evitar chamar `track` em telas inteiras.

**Independent Test**: montar `<PitacoBlock />`, chamar `track` com pesquisa disponível, conferir
`placement_blocked`, `placement_survey_held` e nada disponível; desmontar e conferir
`placement_available` e a pesquisa disponível.

**Acceptance Scenarios**:

1. **Given** nenhum bloqueio, **When** o primeiro `block(reason)` é chamado, **Then** o `onEvent`
   recebe `placement_blocked` com os motivos ativos; bloqueios seguintes não repetem o evento.
2. **Given** um bloqueio ativo, **When** chega uma pesquisa, **Then** ela fica retida, sem exibição,
   e o `onEvent` recebe `placement_survey_held`.
3. **Given** uma pesquisa retida por bloqueio, **When** o último `unblock` acontece dentro do prazo,
   **Then** a pesquisa fica disponível e o `onEvent` recebe `placement_available`.
4. **Given** uma pesquisa retida por bloqueio, **When** o prazo vence antes do desbloqueio, **Then**
   ela é descartada sem abrir exibição, sem consumir o limite da sessão, e o `onEvent` recebe
   `placement_survey_discarded` com `heldMs`.
5. **Given** dois `<PitacoBlock reason="pagamento" />` montados, **When** um desmonta, **Then** o
   bloqueio continua até o outro desmontar.
6. **Given** motivos diferentes ativos, **When** um é desbloqueado, **Then** o outro continua
   valendo.
7. **Given** uma pesquisa já aberta, **When** um bloqueio começa, **Then** ela não é interrompida.
8. **Given** `unblock` sem `block` correspondente, **When** é chamado, **Then** nada lança e sai um
   aviso único em desenvolvimento.

---

### User Story 3 - Segurar a pesquisa e soltar depois (Priority: P2)

O app chama `defer()` antes de um momento em que a pesquisa não deve aparecer — por exemplo, ao
disparar o evento numa tela de transição — e `release()` quando a pessoa chega à tela certa. Se o
prazo vencer antes, a pesquisa é descartada sem exibição.

**Why this priority**: resolve o caso do evento que acontece numa tela mas cuja pesquisa faz
sentido na próxima. É mais raro que o bloqueio.

**Independent Test**: chamar `defer()`, chamar `track` com pesquisa disponível, conferir
`placement_deferred` e nada disponível; chamar `release()` e conferir `placement_released` com
`heldMs`, `placement_available` e a pesquisa disponível.

**Acceptance Scenarios**:

1. **Given** `defer()` chamado, **When** chega uma pesquisa, **Then** ela fica retida e o `onEvent`
   recebe `placement_deferred`.
2. **Given** uma pesquisa retida por adiamento, **When** o app chama `release()` dentro do prazo,
   **Then** o `onEvent` recebe `placement_released` com `heldMs`, depois `placement_available`, e a
   pesquisa fica disponível.
3. **Given** uma pesquisa retida por adiamento, **When** o prazo vence sem `release()`, **Then** ela
   é descartada sem exibição, sem consumir o limite da sessão, e o `onEvent` recebe
   `placement_expired` com `heldMs`.
4. **Given** `defer()` e um bloqueio ativos, **When** o app chama `release()`, **Then** a pesquisa
   continua retida até o último `unblock`; e `unblock` com `defer()` pendente também não libera.
5. **Given** `defer()` chamado sem nenhuma pesquisa chegando, **When** o tempo passa, **Then** nada
   vence: o prazo é da pesquisa retida, não do pedido.
6. **Given** `release()` sem `defer()`, **When** é chamado, **Then** nada lança e sai um aviso único
   em desenvolvimento.

---

### User Story 4 - O app enxerga o que o SDK decidiu (Priority: P3)

Cada decisão de posicionamento chega ao app como evento `placement_` no `onEvent`, com a pesquisa
candidata quando existe uma, e um diagnóstico do estado (limite, bloqueios, adiamento, retida)
fica disponível para ferramentas de depuração. Nenhum desses eventos vai ao servidor.

**Why this priority**: torna o comportamento explicável ("por que a pesquisa não apareceu?"), mas
o controle em si já funciona sem.

**Independent Test**: percorrer bloqueio e adiamento e conferir os `placement_` recebidos e que a
fila não tem nenhum deles; ler `diagnostics()` em cada passo.

**Acceptance Scenarios**:

1. **Given** qualquer evento `placement_`, **When** é emitido, **Then** vai só para o `onEvent` e
   nunca entra na fila nem é enviado.
2. **Given** um evento com pesquisa candidata, **When** é emitido, **Then** traz `surveyId`,
   `versionId` e `triggerEvent`; `placement_blocked` e `placement_session_limited` trazem `null`
   onde não há candidata.
3. **Given** o runtime, **When** o app lê `diagnostics()`, **Then** recebe se a sessão já exibiu
   pesquisa, os motivos de bloqueio ativos, se há adiamento pedido e se há pesquisa retida.
4. **Given** o Provider trocando de runtime (outra configuração), **When** havia uma pesquisa
   retida, **Then** o temporizador antigo não emite mais nada pelo ouvinte antigo.
5. **Given** um portão de exibição próprio instalado no runtime, **When** havia uma candidata
   retida pelo portão padrão, **Then** ela é descartada sem exibição.

---

### Edge Cases

- **Chave recusada**: `track` nem consulta, antes até do limite de sessão.
- **Uma segunda pesquisa chegando com uma retida**: ignorada, sem evento próprio.
- **Bloqueio que começa entre a oferta e a apresentação**: a janela é de milissegundos e não é
  escondida retroativamente.
- **Motivo de bloqueio inválido** (não texto, vazio ou acima de 80 caracteres): cai no motivo
  padrão, com aviso.
- **Logout com retida**: descartada sem exibição; bloqueios, adiamento pedido e limite continuam.
- **`placement_expired` e o pedido de adiamento**: depois do vencimento, o pedido de `defer()`
  continua ativo até um `release()`.
- **Desmonte definitivo do Provider com uma retida**: o runtime é só desligado (o StrictMode remonta
  o mesmo runtime); o temporizador ainda pode vencer e emitir pelo ouvinte da árvore desmontada,
  sem abrir exibição nem enviar nada.
- **`usePitaco()` fora do Provider**: `block`, `unblock`, `defer`, `release` e `simulateAppReopen`
  não fazem nada e não lançam; `diagnostics()` devolve `null`.

## Requirements *(mandatory)*

### Functional Requirements

**Sessão de app**

- **FR-001**: Depois que uma exibição for apresentada, `track` MUST NOT consultar o servidor até o
  fim da sessão do runtime e MUST emitir `placement_session_limited` com o nome do evento.
- **FR-002**: Oferta nunca apresentada MUST NOT consumir o limite.
- **FR-003**: O limite MUST viver só em memória; um runtime novo MUST nascer com ele zerado.
- **FR-004**: `simulateAppReopen()` MUST zerar só o limite, sem afetar identidade, fila, bloqueios
  ou adiamento.

**Bloqueio**

- **FR-005**: `block(reason?)` e `unblock(reason?)` MUST contar referências por motivo; sem motivo,
  vale o motivo padrão.
- **FR-006**: O primeiro bloqueio ativo MUST emitir `placement_blocked` com os motivos ativos.
- **FR-007**: Pesquisa que chegar com bloqueio ativo MUST ficar retida e emitir
  `placement_survey_held`.
- **FR-008**: O último `unblock` MUST oferecer a pesquisa retida, se ainda dentro do prazo e sem
  adiamento pendente, emitindo `placement_available`.
- **FR-009**: Pesquisa retida por bloqueio além do prazo MUST ser descartada sem exibição, sem
  consumir o limite, emitindo `placement_survey_discarded` com `heldMs`.
- **FR-010**: Bloqueio MUST NOT interromper pesquisa já disponível ou apresentada.
- **FR-011**: `<PitacoBlock reason? />` MUST bloquear enquanto montado e desbloquear ao desmontar.
- **FR-012**: `unblock` sem `block` correspondente MUST ser ignorado com aviso único em
  desenvolvimento.

**Adiamento**

- **FR-013**: Com `defer()` pedido, a próxima pesquisa que chegar MUST ficar retida, emitindo
  `placement_deferred`; uma já retida por bloqueio passa a depender também do `release()`; chamar
  `defer()` de novo sem `release()` MUST NOT empilhar.
- **FR-014**: `release()` MUST liberar a retida por adiamento, emitindo `placement_released` com
  `heldMs` e depois `placement_available`, desde que não haja bloqueio ativo.
- **FR-015**: Pesquisa retida por adiamento além do prazo MUST ser descartada sem exibição, sem
  consumir o limite, emitindo `placement_expired` com `heldMs`.
- **FR-016**: `release()` sem `defer()` MUST ser ignorado com aviso único em desenvolvimento.

**Prazo e retenção**

- **FR-017**: O prazo de retenção MUST ser `deferTimeoutMs` (padrão 300000 ms, válido entre 1000 e
  3600000), comum a bloqueio e adiamento, contado a partir da retenção.
- **FR-018**: No máximo uma pesquisa MUST ficar retida; outra que chegar MUST ser ignorada.
- **FR-019**: A causa da retenção (bloqueio ou adiamento) MUST ser a primeira que a reteve, e MUST
  decidir se a liberação emite `placement_released`.
- **FR-020**: `reset()` MUST descartar a retida sem exibição e MUST NOT mexer em bloqueios,
  adiamento pedido ou limite.

**Eventos de posicionamento**

- **FR-021**: O SDK MUST emitir os nove tipos `placement_` (`available`, `suppressed`,
  `session_limited`, `blocked`, `survey_held`, `survey_discarded`, `deferred`, `released`,
  `expired`) só para o `onEvent`, com envelope `type`, `occurredAt`, `surveyId`, `versionId`,
  `triggerEvent` e `data`.
- **FR-022**: Eventos `placement_` MUST NOT entrar na fila nem ser enviados ao servidor.
- **FR-023**: O SDK MUST exportar `PLACEMENT_EVENT_TYPES` e `isPlacementEvent`.

**Diagnóstico e extensão**

- **FR-024**: `diagnostics()` MUST expor `sessionSurveyShown`, `blockedReasons`, `deferred` e `held`,
  além do que a `001-sdk-core` já expõe.
- **FR-025**: Bloqueio e adiamento MUST ser implementados como o portão de exibição padrão do
  runtime; trocar o portão MUST descartar sem exibição a candidata retida pelo anterior.
- **FR-026**: O runtime substituído pelo Provider MUST ser encerrado, sem emitir mais eventos nem
  oferecer pesquisa de elegibilidade tardia.

### Key Entities

- **Sessão de app**: o tempo de vida do runtime; carrega se já houve exibição.
- **Bloqueio**: um motivo com contagem de referências.
- **Adiamento**: o pedido pendente de segurar a próxima pesquisa.
- **Pesquisa retida**: a candidata segurada, com a causa e o instante em que foi retida.
- **Evento de posicionamento**: o que aconteceu antes da exibição, entregue só ao app.
- **Portão de exibição**: a política que decide oferecer, segurar ou descartar cada candidata.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em nenhuma sessão de app há mais de uma pesquisa exibida, e nenhuma consulta de
  elegibilidade sai depois da primeira exibição.
- **SC-002**: Com bloqueio ativo, nenhuma pesquisa nova aparece; ao liberar dentro do prazo, a
  retida aparece sem novo `track`.
- **SC-003**: Pesquisa retida além do prazo nunca abre exibição nem consome o limite.
- **SC-004**: Nenhum evento `placement_` chega ao servidor.
- **SC-005**: Toda oferta, retenção, liberação por adiamento, descarte por prazo e limitação de
  sessão gera exatamente um evento `placement_` para o app.

## Assumptions

- **O controle é de onde e quando, não de se.** Elegibilidade continua no servidor; o limite de
  sessão só evita perguntar de novo.
- **Bloqueio e adiamento são escopo de tela.** Quem chama `block`/`defer` normalmente continua
  montado depois de um logout, por isso eles sobrevivem ao `reset()`.
- **Fila de candidatas retidas fica de fora.** Uma retida por vez cobre os casos pedidos.
- **`simulateAppReopen()` e `diagnostics()` são ferramentas de diagnóstico.** Ficam na API para o
  painel de depuração do app de exemplo, não como recurso de produto.

## Dependencies

- `001-sdk-core`: runtime, `PlacementGate`, `track`, `reset`, configuração e `onEvent`.
- `003-sdk-eventos-interacao`: `survey_presented`, que é o que consome a sessão.
- `002-sdk-ui-apresentacoes`: apresentações que respeitam o portão de graça (só oferecem o que foi
  liberado).
- Consumidor: `006-sdk-app-exemplo` (painel de depuração e "Simular app reaberto").

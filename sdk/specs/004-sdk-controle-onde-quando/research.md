# Research: SDK React Native — controle de onde e quando a pesquisa aparece

**Feature**: `004-sdk-controle-onde-quando` · **Date**: 2026-09-13 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Sessão de app é o tempo de vida do runtime

**Decision**: `sessionSurveyShown` é um campo em memória do `PitacoRuntime`, nunca gravado. Um
runtime novo nasce com o limite zerado. `simulateAppReopen()` zera só esse campo.

**Rationale**: o Provider nasce uma vez por processo; "reabrir o app zera o limite" vira verdade
sem relógio de parede nem storage. `simulateAppReopen()` existe para o painel de depuração simular a
reabertura sem perder fila nem identidade, que dependem do storage.

**Alternatives considered**: persistir com expiração por tempo (inventaria uma definição de sessão
que o app não pediu); zerar no `AppState` `active` (uma ida rápida ao segundo plano liberaria outra
pesquisa).

---

## D-02 — O limite conta exibição, e é checado antes da rede

**Decision**: o limite liga quando a transição emite `survey_presented`; `track()` o checa logo
depois de validar o nome do evento e de checar a chave, antes de `boot()` e da elegibilidade, e
emite `placement_session_limited` com o nome do evento.

**Rationale**: oferta que nunca ficou visível (suprimida, descartada) não foi vista pela pessoa;
não consultar economiza a viagem e o limite por origem.

**Alternatives considered**: contar a oferta (puniria pesquisa que nunca apareceu).

---

## D-03 — Bloqueio por contagem de referências

**Decision**: `Map<motivo, contagem>`. `block` incrementa; `unblock` decrementa e remove em zero.
`placement_blocked` só sai na transição de "nenhum motivo" para "algum", com a lista de motivos.
Motivo ausente, inválido ou acima de 80 caracteres vira `DEFAULT_BLOCK_REASON` (`'default'`), com
aviso quando inválido.

**Rationale**: um conjunto faria dois `<PitacoBlock reason="pagamento" />` simultâneos se anularem
no primeiro a desmontar.

**Alternatives considered**: conjunto de motivos; bloqueio booleano global.

---

## D-04 — Bloqueio e adiamento como portão padrão

**Decision**: o runtime nasce com um `PlacementGate` próprio cujo `consider()` retém (bloqueio
primeiro, depois adiamento) ou oferece. `setPlacementGate()` troca a política inteira e descarta
sem exibição a candidata retida pelo portão anterior.

**Rationale**: reaproveita a interface desenhada na `001-sdk-core` (`consider(candidato, { offer,
discard, notify })`). Como a retenção acontece antes de `offer()` — o único lugar que cria a sessão
e torna `available` verdadeiro —, `usePitacoSurvey`, `PitacoSurfaceHost` e o modo `inline`
respeitam o portão sem mudança.

**Alternatives considered**: checar bloqueio na UI (cada apresentação teria de saber).

---

## D-05 — Um prazo para bloqueio e adiamento, contado da retenção

**Decision**: `deferTimeoutMs` (padrão 5 min, 1 s a 1 h) vale para a retida por bloqueio e por
adiamento. O temporizador começa quando a candidata é retida.

**Rationale**: é o mesmo conceito. `defer()` chamado numa tela vazia, sem pesquisa chegando por
horas, não "vence" sozinho: não há nada para descartar. Cinco minutos dão tempo de trocar de tela
sem a pesquisa perder o contexto que a disparou.

**Alternatives considered**: dois prazos; prazo contado do `defer()`.

---

## D-06 — A causa é fixada na retenção

**Decision**: `held.cause` é `'block'` ou `'defer'`, a primeira condição que segurou a candidata.
Na liberação, só a causa `defer` emite `placement_released`; a causa `block` libera direto com o
`placement_available` de sempre. No vencimento, `block` emite `placement_survey_discarded` e
`defer` emite `placement_expired`, ambos com `heldMs`.

**Rationale**: o sucesso do bloqueio já é visível no `placement_available`; o adiamento pediu
evento próprio de liberação. Fixar a causa evita ambiguidade quando as duas condições se sobrepõem.

**Alternatives considered**: emitir os dois eventos de liberação quando as duas condições existirem.

---

## D-07 — As duas condições precisam estar livres

**Decision**: `tryReleaseHeld()` só oferece quando não há bloqueio ativo **e** não há adiamento
pedido. `release()` com bloqueio mantém retida; `unblock()` com adiamento pendente também.

**Rationale**: liberar com uma das condições ainda valendo mostraria a pesquisa numa tela que pediu
para não mostrar.

---

## D-08 — Catálogo `placement_` separado, só para o app

**Decision**: `src/catalog/placement.ts` define nove tipos com envelope `{ type, occurredAt,
surveyId, versionId, triggerEvent, data }`, sem `displayId` nem `seq`. `placement_blocked` e
`placement_session_limited` levam `surveyId`/`versionId` nulos (não há candidata); o bloqueio leva
também `triggerEvent` nulo. `emitPlacement()` só chama `deliver()`.

**Rationale**: antes de existir exibição não há `displayId` para a chave de idempotência; o
servidor não tem o que fazer com esses fatos. O prefixo evita confundir com o catálogo enviado.

**Alternatives considered**: estender o catálogo de interação (mudaria o contrato com o backend).

---

## D-09 — Uma retida por vez

**Decision**: com uma candidata retida, outra que chegar é ignorada com log de depuração, sem
evento.

**Rationale**: é o mesmo tratamento de "pesquisa em andamento"; uma política de fila de candidatas
não foi pedida.

---

## D-10 — Logout descarta a retida e preserva o resto

**Decision**: `reset()` descarta a candidata retida sem exibição e não toca em bloqueios, adiamento
pedido nem limite de sessão.

**Rationale**: a candidata foi consultada para quem está saindo; bloqueio e adiamento são de tela,
não de pessoa; o limite é do uso do app, não do login.

---

## D-11 — Runtime substituído é encerrado

**Decision**: quando o Provider troca de runtime (mudou `deferTimeoutMs`, `baseUrl`, storage…), o
anterior recebe `dispose()`: para o temporizador de retenção e o de envio, solta o `AppState`, pausa
a fila, descarta a sessão e limpa ouvintes; elegibilidade que chegue depois não vira oferta e
nenhum evento sai pelo ouvinte antigo. O que já estava na fila persistente sai pelo runtime novo,
que usa a mesma chave.

**Rationale**: sem isso, trocar o prazo com uma retida fazia o temporizador antigo emitir
`placement_expired` pelo ouvinte antigo. O teste falha sem a correção (`Received + 1`).

**Alternatives considered**: `dispose()` também no desmonte simples (quebra o StrictMode; ver
Complexity Tracking do plano).

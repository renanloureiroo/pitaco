# Research: Controle de exposição — prioridade, cota, descanso e catálogo observado

**Feature**: `008-exposure-control` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Exposição é da pesquisa, não da versão (FR-001 a FR-004)

**Decision**: `Exposure(int priority, Optional<Integer> responseQuota, boolean ignoresQuietPeriod)`
em `survey/domain/valueobjects`, guardada em `surveys.priority`, `surveys.response_quota` e
`surveys.ignores_quiet_period`, com `check (priority between -100 and 100)` e
`check (response_quota is null or response_quota >= 1)`. Editada pelo `PATCH
/applications/{applicationId}/surveys/{surveyId}` que já existia, sem abrir rascunho.

**Rationale**: mudar quanto a pesquisa aparece não muda o que ela pergunta; colocar na versão
obrigaria uma versão nova (e um grupo de comparabilidade novo) para ajustar uma cota.

**Alternatives considered**: tabela própria de exposição — uma junção a mais no caminho quente
para três colunas que sempre andam com a pesquisa.

---

## D-02 — Desempate determinístico em três níveis (FR-006, FR-007)

**Decision**: `FindEligibleSurveyUseCase.tiebreak()` ordena por `priority` desc, `publishedAt` asc
e `surveyId` asc, e pega o primeiro. `SurveyCandidate` ganhou `priority` e `ignoresQuietPeriod`,
lidos na mesma consulta das candidatas.

**Rationale**: a prioridade explícita pesa primeiro; sem ela, vence quem estava no ar há mais
tempo, preservando o comportamento anterior; o identificador desfaz o empate técnico para que a
mesma consulta devolva sempre a mesma pesquisa.

**Alternatives considered**: sortear entre empatadas — o respondente veria pesquisas diferentes
para o mesmo evento, e os testes deixariam de ser determinísticos.

---

## D-03 — O descanso é do respondente, e só custa quando pode barrar (FR-008 a FR-011)

**Decision**: `QuietPeriod.ofDays(n).isRestingAt(lastOpenedAt, now)` com `now.isBefore(last +
n dias)`. `isResting` só consulta `SurveyDisplayRepository.lastOpenedAt(respondentId)` quando o
respondente é conhecido, alguma candidata não é isenta e a aplicação tem intervalo
(`ApplicationScopeGateway.quietPeriodDaysOf`, fora de `stateOf` para não pesar no caminho quente).
O filtro fica entre o histórico e a segmentação.

**Rationale**: o intervalo protege a pessoa de excesso de pesquisas da mesma aplicação, então conta
a partir da última exibição de qualquer uma delas, qualquer que seja o desfecho. O índice
`idx_survey_displays_respondent_listing` já tem `(respondent_id, opened_at desc)` na frente.

**Alternatives considered**: contar só exibições respondidas — quem dispensou viu a pesquisa e
também foi incomodado; descanso por pesquisa — não protege de uma enxurrada de pesquisas
diferentes.

---

## D-04 — A conclusão que atinge a cota encerra na mesma transação (FR-012, FR-013, FR-018)

**Decision**: em `SubmitSurveyDisplayUseCase` (`@Transactional`), depois de gravar uma conclusão,
`closeOnQuota` lê a cota sem trava (`responseQuotaOf`); só com cota, relê travando a linha
(`lockedResponseQuotaOf`, `select response_quota from surveys where id = :id for update`), conta
as concluídas (`countCompleted`, índice parcial `idx_survey_displays_completed`) e, se alcançou,
chama `SurveyQuotaGateway.endByQuota`.

**Rationale**: a leitura sem trava filtra o caso comum (sem cota) sem serializar nada; a trava põe
as conclusões da mesma pesquisa em fila, e a contagem de uma já inclui a da outra. A validação da
submissão usa a versão exibida, não o estado atual, por isso quem já estava com a pesquisa aberta
conclui normalmente.

**Alternatives considered**: job periódico de encerramento — a pesquisa continuaria entregue
entre a cota e o job; contador desnormalizado na pesquisa — mais uma escrita por conclusão e um
número que pode divergir da tabela de exibições.

---

## D-05 — O encerramento por cota é da autoria, com update condicional (FR-014 a FR-016)

**Decision**: `SurveyQuotaGatewaySurvey` (em `collect/infra/gateways`) chama
`EndSurveyByQuotaUseCase`, que se junta à transação aberta. `QuotaClosure.close` calcula o estado
antes, desiste se já está `ENDED` (inclusive pela janela) ou se `Survey.endByQuota()` recusa
(rascunho, encerrada), executa `endIfLive` (`update … set lifecycle = 'ENDED' where lifecycle in
('PUBLISHED','PAUSED')`) e só grava `SurveyStateTransition` com `TransitionReason.QUOTA_REACHED`
quando o update mudou uma linha. Pesquisa inexistente ou de outra aplicação é ignorada.

**Rationale**: registrar a transição com o motivo certo é regra da autoria; o update condicional
garante que, de duas transações que chegam juntas (duas conclusões, ou pausa + conclusão), só a
que efetivamente mudou o estado grava a transição.

**Alternatives considered**: update direto pela coleta — a coleta passaria a conhecer o ciclo de
vida da pesquisa e o histórico.

---

## D-06 — Cota reduzida até o coletado encerra na hora (FR-017)

**Decision**: `UpdateSurveyUseCase` trava a pesquisa (`SurveyScope.requireLocked`) e, só quando a
cota foi definida nesta chamada e a pesquisa não é rascunho nem encerrada, compara com
`CompletedResponsesGateway.completedResponsesOf` e chama a mesma `QuotaClosure`.

**Rationale**: a regra é "encerra sozinha ao atingir a cota"; esperar a próxima conclusão seria
esperar algo que pode nunca vir. A trava impede que um encerramento por cota simultâneo seja
sobrescrito pela gravação da edição. Editar outro campo não reconfere a cota.

**Alternatives considered**: recusar a redução abaixo do coletado — obrigaria o autor a encerrar
manualmente, com motivo errado no histórico.

---

## D-07 — Progresso da cota mora na coleta (FR-019)

**Decision**: `GET /applications/{applicationId}/surveys/{surveyId}/quota-progress` em
`QuotaProgressController`, com `GetQuotaProgressUseCase` devolvendo `responseQuota` (opcional) e
`completedResponses` de todas as versões.

**Rationale**: contar concluídas é da coleta; a leitura da pesquisa não paga essa contagem em cada
tela que mostra o cabeçalho.

**Alternatives considered**: incluir o progresso no `SurveyResponseDTO` — custo de contagem em toda
listagem de pesquisas.

---

## D-08 — Catálogo de atributos com limite no próprio banco (FR-021 a FR-023)

**Decision**: `application_attributes` (uma linha por nome) e `application_attribute_values` (uma
por valor, `on delete cascade`). `ApplicationAttributeJpaRepository` desmonta o instantâneo inteiro
com `jsonb_each_text(cast(:attributes as jsonb))` — dois comandos por consulta, quantos atributos
vierem —, amortizados como o catálogo de eventos (`REFRESH_INTERVAL` de 5 minutos). Valor novo só
entra enquanto o atributo tem menos de `ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE` (200). A
gravação roda em transação própria em `FindEligibleSurveyUseCase.observe`, separada da de eventos.

**Rationale**: o catálogo é para a autoria escolher valores, não um perfil. Um atributo de alta
cardinalidade viraria log, e provavelmente é dado que nem devia chegar.

**Alternatives considered**: guardar todos os valores — crescimento sem limite com tráfego do app;
um comando por atributo — N idas ao banco por consulta.

---

## D-09 — Leitura dos catálogos é só existência da aplicação (FR-024 a FR-026)

**Decision**: `ListObservedEventsUseCase` e `ListObservedAttributesUseCase` usam
`CollectScope.existingApplicationIdOf` (não exigem aplicação ativa), página padrão 0/20, máximo
100, ordem `last_seen_at desc, name asc`.

**Rationale**: aplicação inativa não recebe eventos novos, mas o que já foi visto continua útil à
autoria.

**Alternatives considered**: recusar com `application.inactive` — esconderia dado legítimo de uma
aplicação que só está pausada.

---

## D-10 — Autor da transição reservado (FR-020)

**Decision**: `SurveyStateTransition.actor()` é `Optional` e sempre vazio; `record(...)` grava
`null`. A coluna `actor` já existe desde a `003`.

**Rationale**: não existe autenticação do painel; inventar um autor seria mentir no histórico.

**Alternatives considered**: gravar o IP ou a chave — dado de infraestrutura, não de pessoa.

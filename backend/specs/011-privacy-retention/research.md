# Research: Privacidade e retenção — descarte, exclusão do titular e auditoria

**Feature**: `011-privacy-retention` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — O padrão é não apagar (FR-002)

**Decision**: `RetentionPolicy.of(app, retentionDays, openTextDays)` com o prazo de texto efetivo
(`openTextDays` ou, na falta, `retentionDays`). `isConfigured()` exige algum dos dois;
`PrivacyApplicationGateway.configuredPolicies()` só devolve as configuradas.

**Rationale**: apagar dado de cliente por padrão seria uma surpresa irreversível. Quem quer
retenção, configura.

**Alternatives considered**: prazo global padrão — decisão que não é do Pitaco.

---

## D-02 — Um lote, uma transação: congelar e apagar juntos (FR-003, FR-004, FR-007)

**Decision**: `ApplyRetentionUseCase.discard` repete, cada iteração em
`transactor.inTransaction`: `store.expiringAnswers(app, corte, batchSize)` →
`RetentionSnapshots.of(lote, corte, agora)` gravados → `store.deleteAnswers(ids)`. Para quando o
lote vem menor que `batchSize`.

**Rationale**: uma queda no meio não perde conclusão nem conta nada duas vezes; rodar de novo é
seguro porque o que já saiu não é encontrado. Uma transação só para tudo seguraria trava por minutos
em aplicações grandes.

**Alternatives considered**: congelar tudo antes e apagar depois — uma queda entre os dois passos
contaria o congelado e o vivo.

---

## D-03 — Agregado aditivo por versão, não blob (FR-004)

**Decision**: `aggregate_snapshots` (pesquisa, versão, `reason = 'RETENTION'`, `discarded_before`,
`responding_displays`, `computed_at`) e `aggregate_snapshot_counts (snapshot_id, question_key,
dimension, value, count)` com `dimension ∈ STATUS, OPTION, NUMBER`. `RetentionSnapshots` acumula por
`(surveyId, versionId)`; exibição só com puladas não entra em `responding_displays`.

**Rationale**: somar ao vivo é um `group by`; a forma não varia. Um agregado por versão porque é por
versão que o resultado é lido e comparado.

**Alternatives considered**: `jsonb` com o resultado pronto — não soma com o vivo sem reprocessar;
congelar por pesquisa — perderia o recorte por versão.

---

## D-04 — Texto vence sozinho (FR-005)

**Decision**: `store.clearTexts(app, corte, batchSize)` executa `update survey_answers set
text_value = null` em lotes, para respostas com texto respondidas antes do corte de texto. A
situação continua `ANSWERED`.

**Rationale**: o texto livre é onde está o dado pessoal; a nota e a escolha não. O prazo mais curto
apaga só o que é sensível.

**Alternatives considered**: apagar a resposta inteira — perderia a contagem de "respondida" sem
motivo.

---

## D-05 — Prévia com o mesmo relógio do job (FR-008)

**Decision**: `RetentionSchedule.nextRunAfter(agora)` implementado por `CronRetentionSchedule` com a
mesma expressão de `pitaco.privacy.retention.cron`; ausente se `enabled = false`. A prévia conta
(`countAnswersBefore`, `countTextsBefore`) com referência na próxima execução e na próxima + 7 dias;
`firstDiscardPending` = não há `lastRunAt`. `retention_runs` só ganha linha quando algo saiu.

**Rationale**: duas fontes para o mesmo horário divergiriam na primeira mudança. "Já houve
descarte?" é o que o aviso do painel precisa.

**Alternatives considered**: prévia sobre "agora" — mostraria menos do que vai sair.

---

## D-06 — Exclusão pela identidade opaca, atômica com o registro (FR-009 a FR-015)

**Decision**: `DELETE /applications/{applicationId}/respondents?reference=…` ou `?deviceId=…`.
`ErasureTarget.of` normaliza e exige exatamente uma. `DeleteRespondentUseCase` (`@Transactional`)
encontra o respondente com as contagens, executa `delete from respondents where id = :id` (o resto
sai por cascata) e grava `DeletionAudit.record(app, exibições, respostas)` com `performed_by` nulo.
Sem alvo: `deleted: false`, sem registro, log sem identidade.

**Rationale**: é a identidade que chega do app hospedeiro; o identificador interno o operador não
conhece. Não existe exclusão sem registro, nem registro sem exclusão.

**Alternatives considered**: apagar tabela a tabela no caso de uso — N comandos e risco de esquecer
uma tabela nova; guardar a referência no registro — o mesmo dado num lugar diferente.

---

## D-07 — Cascata no schema, achada pelo catálogo (FR-017)

**Decision**: a migration percorre pares `(tabela, coluna, referência)` — respondentes, pesquisas,
chaves, exibições, catálogos, uso de SDK, supressões e registros — acha a FK existente em
`pg_constraint` por tabela e coluna, apaga e recria como `fk_<tabela>_<coluna> … on delete cascade`.
`survey_displays.version_id` fica sem cascata. Índice `idx_survey_displays_respondent` para a
cascata do respondente.

**Rationale**: o nome gerado pelo Postgres não é contrato. Apagar uma versão com exibição apontando
continua bloqueado; apagar a pesquisa leva as duas no mesmo comando.

**Alternatives considered**: cascata no JPA — não alcança as tabelas escritas por comando nativo.

---

## D-08 — Job com falha logada uma vez (FR-001)

**Decision**: `RetentionJob` (`@Scheduled(cron, zone = "UTC")`,
`@ConditionalOnProperty(pitaco.privacy.retention.enabled, matchIfMissing = true)`) chama o caso de
uso e loga `warn` com a classe da falha.

**Rationale**: a borda deste caso de uso é o agendador; desligar o descarte é tirar o bean, não
esvaziar a política das aplicações.

**Alternatives considered**: endpoint para disparar o descarte — superfície nova sem caso de uso.

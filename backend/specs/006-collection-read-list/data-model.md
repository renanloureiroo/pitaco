# Data Model: Coleta — leitura das exibições, respostas e respondentes

**Feature**: `006-collection-read-list` · **Date**: 2026-09-09

Esta fatia **não cria nem altera entidade de domínio**. As três que ela lê já existem, já se
validam e continuam imutáveis. O que nasce aqui é: duas projeções de leitura, seis `record` de
saída, dois erros nomeados e uma migration de índice.

---

## 1. O que já existe e é lido como está

### SurveyDisplay — `collect/domain/entities`

Entidade, identidade `DisplayId` (gerado no dispositivo, é a chave de idempotência da fila do
SDK).

| Campo | Tipo | Nota de leitura |
| --- | --- | --- |
| `id` | `DisplayId` | identificador devolvido em toda leitura |
| `applicationId` | `ApplicationId` | escopo; nunca aparece no corpo, já está na rota |
| `respondentId` | `RespondentId` | exposto só na consulta individual (FR-012) |
| `surveyId` | `SurveyId` | exposto na listagem por respondente (FR-024) |
| `versionId` | `SurveyVersionId` | exposto sempre; acompanhado do número (D-03) |
| `comparabilityGroup` | `int` | exposto sempre (FR-002) |
| `outcome` | `DisplayOutcome` | `STARTED`, `COMPLETED` ou `DISMISSED`; `ABANDONED` nunca é gravado |
| `sdkVersion` | `Optional<String>` | ausente quando não informado (FR-013) |
| `attributes` | `AttributeSnapshot` | só na consulta individual (FR-012); é o que impede montar entidade na listagem (D-02) |
| `openedAt` | `Instant` | ordenação padrão, desc (FR-007) |
| `closedAt` | `Optional<Instant>` | presente se e somente se o desfecho é final (FR-003) |

**Invariante já garantida pelo construtor** e que a leitura pode assumir sem checar:
`outcome.isFinal() == closedAt.isPresent()`. É o que torna FR-003 verdade por construção.

### Answer — `collect/domain/entities`

Entidade imutável, identidade `AnswerId`. Chave de negócio `(displayId, questionKey)`, garantida
por constraint única no banco.

| Campo | Tipo | Nota de leitura |
| --- | --- | --- |
| `displayId` | `DisplayId` | agrupador |
| `questionKey` | `QuestionKey` | exposto como chave estável (FR-014) |
| `status` | `AnswerStatus` | `ANSWERED` ou `SKIPPED` — **não muda nesta fatia** |
| `value` | `Optional<AnswerValue>` | presente se e somente se `ANSWERED` |
| `answeredAt` | `Instant` | entrada da decisão de expiração (FR-028, D-07) |

`AnswerValue` é `sealed`: `TextValue(AnswerText)`, `NumericValue(int)`,
`ChoiceValue(List<String>)`. Só `TextValue` é sujeito à supressão por retenção.

### Respondent — `collect/domain/entities`

| Campo | Tipo | Nota de leitura |
| --- | --- | --- |
| `id` | `RespondentId` | identificador |
| `applicationId` | `ApplicationId` | escopo (FR-022) |
| `identity` | `RespondentIdentity` | par opaco `kind` + `value`; **o valor nunca vai para log** (FR-030) |
| `firstSeenAt` | `Instant` | FR-020 |
| `lastSeenAt` | `Instant` | FR-020 e ordenação padrão da listagem (FR-021) |

---

## 2. Projeções de leitura (novas)

Declaradas **na porta**, em `application/repositories`, no mesmo molde do `DisplayHistoryEntry`
que já mora lá. Existem porque a entidade de domínio exige o instantâneo de atributos, cuja carga
por linha seria N+1 (D-02).

### `SurveyDisplayRepository.DisplaySummary`

```
DisplayId id · SurveyVersionId versionId · int versionNumber · int comparabilityGroup
DisplayOutcome outcome · Optional<String> sdkVersion · Instant openedAt · Optional<Instant> closedAt
```

Serve FR-002. `versionNumber` vem da junção com `survey_versions` (D-03).

### `SurveyDisplayRepository.RespondentDisplaySummary`

`DisplaySummary` mais `SurveyId surveyId` — é o que FR-024 acrescenta ao ver pelo eixo do
respondente.

> Duas projeções em vez de uma com `Optional<SurveyId>`: o campo é sempre presente numa e sempre
> ausente na outra, e um `Optional` que a rota determina não é ausência, é ruído.

---

## 3. Outputs (novos)

Todos `record` em `collect/application/outputs`, e é o que os casos de uso devolvem — nunca a
entidade.

| Output | Campos | Fecha |
| --- | --- | --- |
| `DisplaySummaryOutput` | `id`, `versionId`, `versionNumber`, `comparabilityGroup`, `outcome`, `sdkVersion: Optional<String>`, `openedAt`, `closedAt: Optional<Instant>` | FR-002, FR-003 |
| `RespondentDisplaySummaryOutput` | o de cima mais `surveyId` | FR-024 |
| `DisplayDetailOutput` | `DisplaySummaryOutput summary`, `respondentId`, `surveyId`, `attributes: Map<String,String>`, `answers: List<AnswerReadOutput>` | FR-011, FR-012, FR-014 |
| `AnswerReadOutput` | `questionKey`, `status: AnswerReadStatus`, `text: Optional<String>`, `number: Optional<Integer>`, `options: List<String>` | FR-014, FR-016, FR-028 |
| `AnswerReadStatus` | `ANSWERED`, `SKIPPED`, `EXPIRED` | FR-016, FR-028 |
| `RespondentOutput` | `id`, `identityKind`, `identityValue`, `firstSeenAt`, `lastSeenAt` | FR-020 |

`AnswerReadStatus` é **situação de saída**, não de domínio (D-07). `AnswerStatus` continua com
dois valores; `EXPIRED` é decidido no caso de uso e nunca é gravado.

O valor da resposta é decomposto em três campos mutuamente exclusivos em vez de um campo
polimórfico: `AnswerValue` é `sealed`, o `switch` do tradutor é exaustivo em compilação, e o
cliente lê o campo que o tipo da pergunta determina.

---

## 4. Regra de expiração de texto livre (FR-028)

Decidida no caso de uso da consulta individual, sobre dados que já existem:

```
prazo = ApplicationScopeGateway.effectiveOpenTextRetentionDaysOf(applicationId)
        // = Application.openTextRetentionDays().or(retentionDays())

para cada resposta:
  se status == SKIPPED                      → SKIPPED, sem valor
  senão se valor não é TextValue            → ANSWERED, valor presente
  senão se prazo ausente                    → ANSWERED, texto presente
  senão se answeredAt + prazo < agora       → EXPIRED, texto ausente
  senão                                     → ANSWERED, texto presente
```

`Instant` em UTC nos dois lados. Prazo ausente significa sem expiração — a ausência é decisão,
não zero, coerente com FR-012 da fatia 005.

---

## 5. Ordenação das respostas (FR-015)

A ordem é propriedade da versão exibida, não da resposta: `survey_answers` não grava posição.

```
conteúdo = PublishedSurveyCatalog.contentOf(display.versionId)
posição(resposta) = posição da pergunta cuja chave casa
                  | não encontrada → depois de todas, desempate estável pela chave
```

O ramo do "não encontrada" existe para o estado inconsistente que a spec manda apresentar como
está, sem corrigir e sem esconder.

---

## 6. Erros

| Erro | `ErrorType` | `code` | Origem |
| --- | --- | --- | --- |
| `SurveyNotFoundInApplication` | `NOT_FOUND` | `survey.not_found` | novo em `collect`, `code` reaproveitado (D-05) |
| `RespondentNotFound` | `NOT_FOUND` | `respondent.not_found` | novo, `code` novo |
| `DisplayNotFound` | `NOT_FOUND` | `display.not_found` | já existe, reaproveitado |

Status HTTP sai de `ErrorTypeHttpStatus`: `NOT_FOUND` → 404. Recusa de validação de entrada é
400, produzida pelo Bean Validation na borda. Nenhum `ErrorType` novo é necessário.

---

## 7. Schema — migration `V20260909170000__index_collect_reads.sql`

Nenhuma coluna nova, nenhuma tabela nova. Só índice (D-10).

```sql
drop index idx_survey_displays_survey;   -- prefixo redundante do primeiro abaixo

create index idx_survey_displays_survey_listing
    on survey_displays (survey_id, opened_at desc, id desc)
    include (version_id, outcome, closed_at, comparability_group, sdk_version, respondent_id);

create index idx_survey_displays_respondent_listing
    on survey_displays (respondent_id, opened_at desc, id desc)
    include (survey_id, version_id, outcome, closed_at, comparability_group, sdk_version);

create index idx_respondents_application_listing
    on respondents (application_id, last_seen_at desc, id desc);
```

Cobertura, campo por campo:

| Uso | Campo | Onde está coberto |
| --- | --- | --- |
| filtro | `survey_id`, `respondent_id`, `application_id` | coluna líder |
| filtro | `version_id`, `outcome` | payload de `include`, avaliado antes do heap |
| filtro | `opened_at` (período) | segunda coluna, como faixa |
| ordenação | `opened_at desc`, `last_seen_at desc` | no índice, já ordenado |
| desempate | `id desc` | no índice — é o que torna a paginação determinística |
| junção | `survey_versions.id` | chave primária existente |
| agrupamento | `survey_answers.display_id` | `uq_survey_answers_question` já cobre |
| agrupamento | `survey_answer_options.answer_id` | chave primária existente |

`idx_survey_displays_history (respondent_id, survey_id, comparability_group)` permanece: serve o
histórico de elegibilidade do caminho quente, que não ordena por abertura.

---

## 8. O que esta fatia não muda

- Nenhuma entidade de domínio ganha campo, método de escrita ou estado.
- `AnswerStatus` e `DisplayOutcome` permanecem como estão.
- Nenhuma coluna é criada, alterada ou removida.
- Nenhum dado é apagado — inclusive o texto livre expirado, que continua no banco e apenas deixa
  de ser apresentado (D-07).
- `core` não muda.

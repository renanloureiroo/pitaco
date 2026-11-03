# Quickstart — validar `011-privacy-retention`

Contrato em [`contracts/privacy.md`](./contracts/privacy.md).

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
./mvnw test -Dtest='RetentionPolicyTest,RetentionSnapshotsTest,ErasureTargetTest,ApplyRetentionUseCaseTest,GetRetentionPreviewUseCaseTest,DeleteRespondentUseCaseTest'
./mvnw test -Dtest='PrivacyE2ETest,RetentionE2ETest,CascadeDeletionE2ETest,PrivacyOpenApiTest'
```

## 3. A migration aplicou

```sql
select conname from pg_constraint
 where conname like 'fk_%' and confdeltype = 'c'
 order by conname;
```

Esperado: as `fk_<tabela>_<coluna>` listadas em `data-model.md`, todas com `on delete cascade`.

## 4. Roteiro manual

| # | Ação | Esperado |
| --- | --- | --- |
| 1 | `GET /applications/{id}/retention-preview` sem prazo | `configured: false`, sem `nextRunAt` |
| 2 | `PATCH /applications/{id}` com `{"retentionDays":30,"openTextRetentionDays":7}`; recuar `answered_at` de algumas respostas em 40 dias e de textos em 10 | — |
| 3 | `GET …/retention-preview` | `nextRun` com as respostas e textos vencidos; `firstDiscardPending: true` |
| 4 | aguardar o job (ou ajustar o cron no perfil local) | respostas antigas apagadas; textos de 10 dias com `text_value` nulo e status `ANSWERED` |
| 5 | `GET …/surveys/{s}/results` sem recorte | mesmos números de antes, com `retention.snapshotApplied: true` |
| 6 | `DELETE /applications/{id}/respondents?reference=u-8f1c` | `deleted: true` com as contagens |
| 7 | repetir o 6 | `deleted: false`; `GET …/deletion-audits` com um registro só |
| 8 | `DELETE …/respondents?reference=a&deviceId=b` | `400` |
| 9 | `DELETE …/respondents` com `X-Pitaco-Key` | `403` |

## 5. O que precisa estar verde

- [X] Lote congela e apaga junto; rodar de novo não faz nada
- [X] Texto vencido apagado sem apagar a resposta
- [X] Exclusão do titular leva exibições, respostas, opções, atributos e supressões
- [X] Registro sem identidade; log sem identidade
- [X] Apagar aplicação não deixa linha em tabela nenhuma

package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutInput;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RetentionRunOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.AggregateSnapshotRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionRunRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionStore;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.ExpiringAnswer;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionSnapshots;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

// Lote a lote, cada um na própria transação: o agregado do lote é congelado e as respostas dele
// saem juntas, então uma queda no meio não perde conclusão nem conta nada duas vezes. Rodar de
// novo é seguro — o que já saiu não volta a ser encontrado.
@Slf4j
public class ApplyRetentionUseCase
    implements UseCaseWithoutInput<ApplyRetentionUseCase.Output> {

  private final PrivacyApplicationGateway applications;
  private final RetentionStore store;
  private final AggregateSnapshotRepository snapshots;
  private final RetentionRunRepository runs;
  private final Transactor transactor;
  private final int batchSize;

  public ApplyRetentionUseCase(
      PrivacyApplicationGateway applications,
      RetentionStore store,
      AggregateSnapshotRepository snapshots,
      RetentionRunRepository runs,
      Transactor transactor,
      int batchSize) {
    this.applications = applications;
    this.store = store;
    this.snapshots = snapshots;
    this.runs = runs;
    this.transactor = transactor;
    this.batchSize = batchSize;
  }

  public record Output(List<RetentionRunOutput> runs) {}

  @Override
  public Output execute() {
    var now = Instant.now();
    var done = new ArrayList<RetentionRunOutput>();

    for (var policy : applications.configuredPolicies()) {
      var answersDeleted = policy.answersBefore(now).map(cutoff -> discard(policy, cutoff, now)).orElse(0);
      var textsCleared = policy.textsBefore(now).map(cutoff -> clear(policy, cutoff)).orElse(0);

      if (answersDeleted + textsCleared == 0) {
        continue;
      }

      transactor.runInTransaction(
          () -> runs.create(RetentionRun.record(policy.applicationId(), answersDeleted, textsCleared, now)));

      log.info(
          "Retenção aplicada application={} respostas={} textos={}",
          policy.applicationId().value(),
          answersDeleted,
          textsCleared);

      done.add(
          new RetentionRunOutput(policy.applicationId().value(), answersDeleted, textsCleared));
    }

    return new Output(List.copyOf(done));
  }

  private int discard(RetentionPolicy policy, Instant cutoff, Instant now) {
    var total = 0;

    while (true) {
      int deleted =
          transactor.inTransaction(
              () -> {
                var batch = store.expiringAnswers(policy.applicationId(), cutoff, batchSize);
                if (batch.isEmpty()) {
                  return 0;
                }

                RetentionSnapshots.of(batch, cutoff, now).forEach(snapshots::create);
                store.deleteAnswers(batch.stream().map(ExpiringAnswer::answerId).toList());
                return batch.size();
              });

      total += deleted;
      if (deleted < batchSize) {
        return total;
      }
    }
  }

  private int clear(RetentionPolicy policy, Instant cutoff) {
    var total = 0;

    while (true) {
      int cleared =
          transactor.inTransaction(() -> store.clearTexts(policy.applicationId(), cutoff, batchSize));

      total += cleared;
      if (cleared < batchSize) {
        return total;
      }
    }
  }
}

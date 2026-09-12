package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.RetentionSchedule;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RetentionPreviewOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RetentionPreviewOutput.Forecast;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionRunRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionStore;
import com.renanloureiroo.pitaco.modules.privacy.application.services.PrivacyScope;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

// O aviso antes do descarte: quanto sai na próxima execução e na semana seguinte, para dar
// tempo de exportar. Textos contam todos os vencidos pelo prazo de texto, inclusive os que vão
// sair junto com a resposta pelo prazo geral — ambos deixam de estar disponíveis.
@Slf4j
public class GetRetentionPreviewUseCase
    implements UseCase<GetRetentionPreviewUseCase.Input, RetentionPreviewOutput> {

  static final Duration WEEK = Duration.ofDays(7);

  private final PrivacyApplicationGateway applications;
  private final RetentionStore store;
  private final RetentionRunRepository runs;
  private final RetentionSchedule schedule;

  public GetRetentionPreviewUseCase(
      PrivacyApplicationGateway applications,
      RetentionStore store,
      RetentionRunRepository runs,
      RetentionSchedule schedule) {
    this.applications = applications;
    this.store = store;
    this.runs = runs;
    this.schedule = schedule;
  }

  public record Input(String applicationId) {}

  @Override
  public RetentionPreviewOutput execute(Input input) {
    var policy = PrivacyScope.existingApplicationOf(applications, input.applicationId());
    var lastRunAt = runs.lastRunAt(policy.applicationId());

    if (!policy.isConfigured()) {
      return new RetentionPreviewOutput(
          false,
          policy.answerDays(),
          policy.textDays(),
          java.util.Optional.empty(),
          Forecast.NONE,
          Forecast.NONE,
          lastRunAt);
    }

    var now = Instant.now();
    var nextRunAt = schedule.nextRunAfter(now);
    var reference = nextRunAt.orElse(now);

    var output =
        new RetentionPreviewOutput(
            true,
            policy.answerDays(),
            policy.textDays(),
            nextRunAt,
            forecastAt(policy, reference),
            forecastAt(policy, reference.plus(WEEK)),
            lastRunAt);

    log.info(
        "Prévia de retenção consultada application={} proximasRespostas={} proximosTextos={}",
        policy.applicationId().value(),
        output.nextRun().answers(),
        output.nextRun().texts());

    return output;
  }

  private Forecast forecastAt(RetentionPolicy policy, Instant reference) {
    var answers =
        policy
            .answersBefore(reference)
            .map(cutoff -> store.countAnswersBefore(policy.applicationId(), cutoff))
            .orElse(0L);
    var texts =
        policy
            .textsBefore(reference)
            .map(cutoff -> store.countTextsBefore(policy.applicationId(), cutoff))
            .orElse(0L);

    return new Forecast(answers, texts);
  }
}

package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyQuotaGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Mora na coleta porque contar concluídas é dela; a leitura da pesquisa não paga essa conta a
// cada tela que mostra o cabeçalho.
@Slf4j
public class GetQuotaProgressUseCase
    implements UseCase<GetQuotaProgressUseCase.Input, GetQuotaProgressUseCase.Output> {

  private final SurveyScopeGateway surveys;
  private final SurveyQuotaGateway quotas;
  private final SurveyDisplayRepository displays;

  public GetQuotaProgressUseCase(
      SurveyScopeGateway surveys, SurveyQuotaGateway quotas, SurveyDisplayRepository displays) {
    this.surveys = surveys;
    this.quotas = quotas;
    this.displays = displays;
  }

  public record Input(String applicationId, String surveyId) {}

  public record Output(Optional<Integer> responseQuota, long completedResponses) {}

  @Override
  public Output execute(Input input) {
    var applicationId = CollectScope.applicationIdOf(input.applicationId());
    var surveyId = CollectScope.existingSurveyIdOf(surveys, applicationId, input.surveyId());

    var output =
        new Output(quotas.responseQuotaOf(surveyId), displays.countCompleted(surveyId));

    log.info(
        "Progresso da cota consultado survey={} concluidas={}",
        surveyId.value(),
        output.completedResponses());

    return output;
  }
}

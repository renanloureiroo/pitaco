package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListSurveysUseCase
    implements UseCase<ListSurveysUseCase.Input, ListSurveysUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public ListSurveysUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository) {
    this.applications = applications;
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public Output execute(Input input) {
    // Só a existência é consultada: aplicação inativa não cria pesquisa, mas pode ser enxergada.
    var applicationId = SurveyScope.existingApplicationIdOf(applications, input.applicationId());

    var page =
        surveysRepository.findPage(
            new SurveyRepository.ListSurveysQuery(applicationId, input.page(), input.size()));

    // Uma consulta para a página inteira, não uma por linha.
    var windows =
        surveyVersionsRepository.findPublishedWindows(
            page.items().stream().map(Survey::id).toList());
    var now = Instant.now();

    var items =
        page.items().stream()
            .map(
                survey ->
                    SurveyOutput.of(
                        survey, survey.stateAt(now, Optional.ofNullable(windows.get(survey.id())))))
            .toList();

    log.info("Pesquisas listadas application={} total={}", applicationId.value(), page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  public record Input(String applicationId, int page, int size) {}

  public record Output(List<SurveyOutput> items, int page, int size, long total, int totalPages) {}
}

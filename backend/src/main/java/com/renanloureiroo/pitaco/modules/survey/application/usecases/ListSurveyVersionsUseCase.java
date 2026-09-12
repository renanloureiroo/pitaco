package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListSurveyVersionsUseCase
    implements UseCase<ListSurveyVersionsUseCase.Input, ListSurveyVersionsUseCase.Output> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public ListSurveyVersionsUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public Output execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    var page =
        surveyVersionsRepository.findPage(
            new SurveyVersionRepository.ListSurveyVersionsQuery(
                survey.id(), input.page(), input.size()));

    log.info("Versões listadas survey={} total={}", survey.id().value(), page.total());

    return new Output(
        SurveyVersionOutput.ofAll(page.items()),
        input.page(),
        input.size(),
        page.total(),
        page.totalPages(input.size()));
  }

  public record Input(String applicationId, String surveyId, int page, int size) {}

  public record Output(
      List<SurveyVersionOutput> items, int page, int size, long total, int totalPages) {}
}

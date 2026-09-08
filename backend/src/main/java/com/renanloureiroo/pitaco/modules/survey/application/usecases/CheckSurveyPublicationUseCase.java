package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationImpedimentOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CheckSurveyPublicationUseCase
    implements UseCase<CheckSurveyPublicationUseCase.Input, List<PublicationImpedimentOutput>> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public CheckSurveyPublicationUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public List<PublicationImpedimentOutput> execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    // Exatamente o mesmo publicationImpediments() que a publicação usa: "a mesma lista" só se
    // sustenta se for literalmente o mesmo código.
    var impediments =
        surveyVersionsRepository
            .findDraft(survey.id())
            .map(SurveyVersion::publicationImpediments)
            .orElseGet(List::of);

    log.info(
        "Impedimentos de publicação consultados survey={} total={}",
        survey.id().value(),
        impediments.size());

    return PublicationImpedimentOutput.ofAll(impediments);
  }

  public record Input(String applicationId, String surveyId) {}
}

package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyDetailOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.TriggerOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetSurveyUseCase implements UseCase<GetSurveyUseCase.Input, SurveyDetailOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public GetSurveyUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public SurveyDetailOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    var draft = surveyVersionsRepository.findDraft(survey.id());
    var published = surveyVersionsRepository.findPublished(survey.id());

    // O conteúdo devolvido é o do rascunho quando existe, o da publicada caso contrário; o
    // estado, esse, sempre vem da janela da publicada.
    var content =
        draft
            .map(version -> contentOf(version, SurveyDetailOutput.ContentSource.DRAFT))
            .or(
                () ->
                    published.map(
                        version -> contentOf(version, SurveyDetailOutput.ContentSource.PUBLISHED)));

    var state =
        survey.stateAt(
            Instant.now(), published.flatMap(SurveyVersion::trigger).map(Trigger::window));

    log.info("Pesquisa consultada [{}] application={}", survey.id().value(), input.applicationId());

    return new SurveyDetailOutput(SurveyOutput.of(survey, state), content);
  }

  private static SurveyDetailOutput.ContentOutput contentOf(
      SurveyVersion version, SurveyDetailOutput.ContentSource source) {
    return new SurveyDetailOutput.ContentOutput(
        source,
        version.getNumber(),
        QuestionOutput.ofAll(version.getQuestions()),
        version.trigger().map(trigger -> TriggerOutput.of(trigger, version.getRules())));
  }

  public record Input(String applicationId, String surveyId) {}
}

package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddQuestionUseCase implements UseCase<AddQuestionUseCase.Input, QuestionOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public AddQuestionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public QuestionOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);

    var question = version.addQuestion(QuestionDrafts.from(input.draft()));
    surveyVersionsRepository.update(version);

    log.info(
        "Pergunta acrescentada [{}] survey={} version={} position={}",
        question.id().value(),
        survey.id().value(),
        version.getNumber(),
        question.getPosition());

    return QuestionOutput.of(question);
  }

  public record Input(String applicationId, String surveyId, QuestionDrafts.Draft draft) {}
}

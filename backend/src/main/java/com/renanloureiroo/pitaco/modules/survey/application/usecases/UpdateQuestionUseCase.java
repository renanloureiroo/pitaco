package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.QuestionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateQuestionUseCase implements UseCase<UpdateQuestionUseCase.Input, QuestionOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public UpdateQuestionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public QuestionOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);
    var questionId = questionIdOf(input.questionId());
    requireExisting(version, questionId, input.questionId());

    version.updateQuestion(questionId, QuestionDrafts.from(input.draft()));
    surveyVersionsRepository.update(version);

    log.info(
        "Pergunta reescrita [{}] survey={} version={}",
        questionId.value(),
        survey.id().value(),
        version.getNumber());

    return QuestionOutput.of(version.question(questionId).orElseThrow());
  }

  // Pergunta inexistente e identificador malformado recusam do mesmo jeito, e pelo erro
  // nomeado — o domínio guarda o mesmo caso, mas quem conhece o identificador cru é daqui.
  private static void requireExisting(SurveyVersion version, QuestionId id, String raw) {
    if (version.question(id).isEmpty()) {
      throw new QuestionNotFound(raw);
    }
  }

  private static QuestionId questionIdOf(String value) {
    try {
      return QuestionId.of(value);
    } catch (DomainException malformed) {
      throw new QuestionNotFound(value);
    }
  }

  public record Input(
      String applicationId, String surveyId, String questionId, QuestionDrafts.Draft draft) {}
}

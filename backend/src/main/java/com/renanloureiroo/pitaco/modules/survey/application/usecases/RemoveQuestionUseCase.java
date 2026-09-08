package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.survey.application.errors.QuestionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveQuestionUseCase implements UseCaseWithoutOutput<RemoveQuestionUseCase.Input> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public RemoveQuestionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public void execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);
    var questionId = questionIdOf(input.questionId());
    if (version.question(questionId).isEmpty()) {
      throw new QuestionNotFound(input.questionId());
    }

    // A remoção recompacta as posições das irmãs dentro da própria versão, e a versão inteira
    // é gravada em um save só — nenhuma chamada a repositório dentro de laço.
    version.removeQuestion(questionId);
    surveyVersionsRepository.update(version);

    log.info(
        "Pergunta removida [{}] survey={} version={}",
        questionId.value(),
        survey.id().value(),
        version.getNumber());
  }

  private static QuestionId questionIdOf(String value) {
    try {
      return QuestionId.of(value);
    } catch (DomainException malformed) {
      throw new QuestionNotFound(value);
    }
  }

  public record Input(String applicationId, String surveyId, String questionId) {}
}

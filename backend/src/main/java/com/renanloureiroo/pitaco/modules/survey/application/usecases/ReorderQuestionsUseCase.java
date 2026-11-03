package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReorderQuestionsUseCase
    implements UseCase<ReorderQuestionsUseCase.Input, List<QuestionOutput>> {

  private static final String ORDER_INVALID_CODE = "question.order_invalid";

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public ReorderQuestionsUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public List<QuestionOutput> execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);

    version.reorder(orderOf(input.questionIds()));
    surveyVersionsRepository.update(version);

    log.info(
        "Perguntas reordenadas survey={} version={} total={}",
        survey.id().value(),
        version.getNumber(),
        version.getQuestions().size());

    return QuestionOutput.ofAll(version.getQuestions());
  }

  // Identificador malformado na lista é o mesmo erro de ordem inválida: a lista não é uma
  // permutação das perguntas existentes de nenhum jeito.
  private static List<QuestionId> orderOf(List<String> questionIds) {
    try {
      return questionIds.stream().map(QuestionId::of).toList();
    } catch (DomainException malformed) {
      throw new DomainException(
          ErrorType.VALIDATION,
          ORDER_INVALID_CODE,
          "A nova ordem deve conter exatamente as perguntas desta versão, sem repetição");
    }
  }

  public record Input(String applicationId, String surveyId, List<String> questionIds) {}
}

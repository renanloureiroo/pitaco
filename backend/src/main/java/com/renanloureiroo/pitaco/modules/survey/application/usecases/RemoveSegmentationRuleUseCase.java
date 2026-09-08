package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SegmentationRuleNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveSegmentationRuleUseCase
    implements UseCaseWithoutOutput<RemoveSegmentationRuleUseCase.Input> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public RemoveSegmentationRuleUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public void execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);

    var ruleId = ruleIdOf(input.ruleId());
    // Regra inexistente e identificador malformado recusam pelo mesmo erro nomeado; o domínio
    // guarda o mesmo caso, mas quem conhece o identificador cru é daqui.
    if (version.getRules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
      throw new SegmentationRuleNotFound(input.ruleId());
    }

    version.removeRule(ruleId);
    surveyVersionsRepository.update(version);

    log.info(
        "Regra de segmentação removida [{}] survey={} version={}",
        input.ruleId(),
        survey.id().value(),
        version.getNumber());
  }

  private static SegmentationRuleId ruleIdOf(String value) {
    try {
      return SegmentationRuleId.of(value);
    } catch (DomainException malformed) {
      throw new SegmentationRuleNotFound(value);
    }
  }

  public record Input(String applicationId, String surveyId, String ruleId) {}
}

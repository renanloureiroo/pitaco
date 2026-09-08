package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.TriggerNotDefined;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SegmentationRuleOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddSegmentationRuleUseCase
    implements UseCase<AddSegmentationRuleUseCase.Input, SegmentationRuleOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public AddSegmentationRuleUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public SegmentationRuleOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);

    if (version.trigger().isEmpty()) {
      throw new TriggerNotDefined(survey.id());
    }

    var rule = SegmentationRule.create(input.attribute(), input.operation(), input.value());
    version.addRule(rule);
    surveyVersionsRepository.update(version);

    log.info(
        "Regra de segmentação acrescentada [{}] survey={} version={}",
        rule.id().value(),
        survey.id().value(),
        version.getNumber());

    return SegmentationRuleOutput.of(rule);
  }

  public record Input(
      String applicationId,
      String surveyId,
      String attribute,
      RuleOperation operation,
      Optional<String> value) {}
}

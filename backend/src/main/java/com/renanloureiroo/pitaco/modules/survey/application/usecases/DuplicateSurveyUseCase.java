package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Copia o conteúdo que está valendo — a versão publicada, ou o rascunho de quem nunca publicou —
// e nada do histórico: respostas, versões anteriores e estado ficam com a original.
@Slf4j
public class DuplicateSurveyUseCase
    implements UseCase<DuplicateSurveyUseCase.Input, SurveyOutput> {

  private final ApplicationScopeGateway applications;
  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public DuplicateSurveyUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository) {
    this.applications = applications;
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public SurveyOutput execute(Input input) {
    var source = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var targetApplicationId =
        SurveyScope.activeApplicationIdOf(
            applications, input.targetApplicationId().orElse(input.applicationId()));

    var content =
        surveyVersionsRepository
            .findPublished(source.id())
            .or(() -> surveyVersionsRepository.findDraft(source.id()))
            .orElseThrow();

    var now = Instant.now();
    var name = input.name().map(SurveyName::of).orElseGet(() -> source.getName().copy());
    var copy = source.duplicateInto(targetApplicationId, name);
    var version = content.duplicateFor(copy.id(), now);

    surveysRepository.create(copy);
    surveyVersionsRepository.create(version);

    log.info(
        "Pesquisa duplicada [{}] origem={} versaoOrigem={} application={}",
        copy.id().value(),
        source.id().value(),
        content.getNumber(),
        targetApplicationId.value());

    return SurveyOutput.of(copy, copy.stateAt(now, Optional.empty()));
  }

  public record Input(
      String applicationId,
      String surveyId,
      Optional<String> targetApplicationId,
      Optional<String> name) {

    public Input {
      targetApplicationId = targetApplicationId == null ? Optional.empty() : targetApplicationId;
      name = name == null ? Optional.empty() : name;
    }
  }
}

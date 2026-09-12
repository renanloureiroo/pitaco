package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.AttributeCatalogGateway;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.SdkTrafficGateway;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationWarningOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.CompatibilityReach;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.CompetingSurvey;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.KnownAttribute;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationWarning;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.SegmentationReach;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Irmão da consulta de impedimentos, mas sem poder de veto: nada daqui bloqueia a publicação.
// Lê o rascunho quando existe e a versão publicada caso contrário, como a consulta da pesquisa.
@Slf4j
public class CheckPublicationWarningsUseCase
    implements UseCase<CheckPublicationWarningsUseCase.Input, List<PublicationWarningOutput>> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final AttributeCatalogGateway attributes;
  private final SdkTrafficGateway traffic;
  private final Duration recentWindow;

  public CheckPublicationWarningsUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      AttributeCatalogGateway attributes,
      SdkTrafficGateway traffic,
      Duration recentWindow) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.attributes = attributes;
    this.traffic = traffic;
    this.recentWindow = recentWindow;
  }

  public record Input(String applicationId, String surveyId) {}

  @Override
  public List<PublicationWarningOutput> execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    var version =
        surveyVersionsRepository
            .findDraft(survey.id())
            .or(() -> surveyVersionsRepository.findPublished(survey.id()));

    var warnings = new ArrayList<PublicationWarning>();
    version.ifPresent(
        current -> {
          warnings.addAll(segmentationWarnings(survey, current.getRules()));
          current.trigger().flatMap(trigger -> competition(survey, trigger)).ifPresent(warnings::add);
          compatibility(survey, current).ifPresent(warnings::add);
        });

    log.info(
        "Avisos de publicação consultados survey={} total={}",
        survey.id().value(),
        warnings.size());

    return PublicationWarningOutput.ofAll(warnings);
  }

  private List<PublicationWarning> segmentationWarnings(
      Survey survey, List<SegmentationRule> rules) {
    if (rules.isEmpty()) {
      return List.of();
    }

    var names = rules.stream().map(SegmentationRule::attribute).distinct().toList();
    var known =
        attributes.knownIn(survey.getApplicationId(), names).stream()
            .collect(Collectors.toMap(KnownAttribute::name, Function.identity()));

    return SegmentationReach.warningsFor(rules, known);
  }

  private java.util.Optional<PublicationWarning> compatibility(
      Survey survey, SurveyVersion version) {
    var since = LocalDate.ofInstant(Instant.now().minus(recentWindow), ZoneOffset.UTC);

    return CompatibilityReach.warningFor(
        version.requiredSdkVersion(survey.getFreeTextNotice().enabled()),
        traffic.recentTraffic(survey.getApplicationId(), since));
  }

  private java.util.Optional<PublicationWarning> competition(Survey survey, Trigger trigger) {
    var competitors =
        surveysRepository
            .findLiveListeningTo(survey.getApplicationId(), trigger.event(), Instant.now())
            .stream()
            .filter(other -> !other.id().equals(survey.id()))
            .map(
                other ->
                    new CompetingSurvey(
                        other.id(), other.getName().value(), other.getExposure().priority()))
            .toList();

    return competitors.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(PublicationWarning.competing(competitors));
  }
}

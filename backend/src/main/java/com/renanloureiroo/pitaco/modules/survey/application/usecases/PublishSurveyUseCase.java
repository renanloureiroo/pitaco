package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.CosmeticDeclarationRefused;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyAlreadyPublished;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotPublishable;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionHasNoChanges;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishSurveyUseCase
    implements UseCase<PublishSurveyUseCase.Input, SurveyVersionOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final SurveyStateTransitionRepository surveyStateTransitionsRepository;

  public PublishSurveyUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      SurveyStateTransitionRepository surveyStateTransitionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.surveyStateTransitionsRepository = surveyStateTransitionsRepository;
  }

  @Override
  @Transactional
  public SurveyVersionOutput execute(Input input) {
    var survey = SurveyScope.requireLocked(surveysRepository, input.applicationId(), input.surveyId());

    var draft =
        surveyVersionsRepository
            .findDraft(survey.id())
            .orElseThrow(() -> new SurveyAlreadyPublished(survey.id()));

    var impediments = draft.publicationImpediments();
    if (!impediments.isEmpty()) {
      throw new SurveyNotPublishable(impediments);
    }

    var previous = surveyVersionsRepository.findPublished(survey.id());
    previous.ifPresent(published -> verifyAgainst(survey, draft, published, input.changeKind()));

    var now = Instant.now();
    var before = stateOf(survey, previous, now);

    var published = draft.publish(now, previous, input.changeKind(), input.changeSummary());
    survey.markPublished(published.getNumber());

    surveyVersionsRepository.update(published);
    surveysRepository.update(survey);
    surveyStateTransitionsRepository.record(
        SurveyStateTransition.record(
            survey.id(),
            before,
            stateOf(survey, Optional.of(published), now),
            TransitionReason.PUBLICATION,
            now));

    log.info(
        "Pesquisa publicada [{}] version={} group={}",
        survey.id().value(),
        published.getNumber(),
        published.getComparabilityGroup());

    return SurveyVersionOutput.of(published);
  }

  // O rascunho idêntico é recusado antes da classificação: não há mudança a classificar.
  private static void verifyAgainst(
      Survey survey, SurveyVersion draft, SurveyVersion previous, Optional<ChangeKind> declared) {
    if (draft.sameContentAs(previous)) {
      throw new SurveyVersionHasNoChanges(survey.id());
    }

    if (declared.filter(ChangeKind.COSMETIC::equals).isPresent()) {
      var differences = ChangeClassification.between(previous.getQuestions(), draft.getQuestions());
      if (!differences.isEmpty()) {
        throw new CosmeticDeclarationRefused(differences);
      }
    }
  }

  private static com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState stateOf(
      Survey survey, Optional<SurveyVersion> version, Instant now) {
    return survey.stateAt(now, version.flatMap(SurveyVersion::trigger).map(Trigger::window));
  }

  public record Input(
      String applicationId,
      String surveyId,
      Optional<ChangeKind> changeKind,
      Optional<String> changeSummary) {}
}

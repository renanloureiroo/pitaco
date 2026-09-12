package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.CompletedResponsesGateway;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.QuotaClosure;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Exposure;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Nome e exposição são da pesquisa, não da versão: mudam sem abrir rascunho, inclusive com a
// pesquisa no ar, porque nenhum dos dois altera o que se pergunta.
@Slf4j
public class UpdateSurveyUseCase implements UseCase<UpdateSurveyUseCase.Input, SurveyOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final SurveyStateTransitionRepository surveyStateTransitionsRepository;
  private final CompletedResponsesGateway completedResponses;

  public UpdateSurveyUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      SurveyStateTransitionRepository surveyStateTransitionsRepository,
      CompletedResponsesGateway completedResponses) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.surveyStateTransitionsRepository = surveyStateTransitionsRepository;
    this.completedResponses = completedResponses;
  }

  // Ausente não mexe. Cota e texto do aviso são removíveis: sem cota, e texto padrão. Nome,
  // prioridade, isenção e aviso ligado sempre têm valor.
  public record Input(
      String applicationId,
      String surveyId,
      Optional<String> name,
      Optional<Integer> priority,
      Patch<Integer> responseQuota,
      Optional<Boolean> ignoresQuietPeriod,
      Optional<Boolean> freeTextNoticeEnabled,
      Patch<String> freeTextNoticeText) {

    public Input {
      freeTextNoticeEnabled =
          freeTextNoticeEnabled == null ? Optional.empty() : freeTextNoticeEnabled;
      freeTextNoticeText = freeTextNoticeText == null ? Patch.absent() : freeTextNoticeText;
    }
  }

  @Override
  @Transactional
  public SurveyOutput execute(Input input) {
    // Travada: um encerramento por cota que chegue no meio não é sobrescrito por esta escrita.
    var survey =
        SurveyScope.requireLocked(surveysRepository, input.applicationId(), input.surveyId());

    input.name().map(SurveyName::of).ifPresent(survey::rename);
    survey.redefineExposure(exposureFrom(survey.getExposure(), input));
    survey.redefineFreeTextNotice(noticeFrom(survey.getFreeTextNotice(), input));
    surveysRepository.update(survey);

    var now = Instant.now();
    var window =
        surveyVersionsRepository
            .findPublished(survey.id())
            .flatMap(SurveyVersion::trigger)
            .map(Trigger::window);

    if (quotaReachedByThisChange(survey, input)
        && QuotaClosure.close(
            survey, window, now, surveysRepository, surveyStateTransitionsRepository)) {
      log.info(
          "Pesquisa encerrada por cota reduzida [{}] application={}",
          survey.id().value(),
          input.applicationId());
    }

    log.info("Pesquisa atualizada [{}] application={}", survey.id().value(), input.applicationId());

    return SurveyOutput.of(survey, survey.stateAt(now, window));
  }

  // Só a cota definida nesta chamada é conferida, e só em pesquisa já publicada: a regra é que ela
  // encerra sozinha ao atingir a cota, e esperar a próxima conclusão seria esperar algo que pode
  // nunca vir.
  private boolean quotaReachedByThisChange(Survey survey, Input input) {
    var lifecycle = survey.getLifecycle();
    if (!input.responseQuota().present()
        || lifecycle == SurveyLifecycle.DRAFT
        || lifecycle == SurveyLifecycle.ENDED) {
      return false;
    }

    return survey
        .getExposure()
        .responseQuota()
        .filter(quota -> completedResponses.completedResponsesOf(survey.id()) >= quota)
        .isPresent();
  }

  private static FreeTextNotice noticeFrom(FreeTextNotice current, Input input) {
    var enabled = input.freeTextNoticeEnabled().map(current::enabled).orElse(current);

    var text = input.freeTextNoticeText();
    if (!text.present()) {
      return enabled;
    }
    return text.value().map(enabled::withText).orElseGet(enabled::withDefaultText);
  }

  private static Exposure exposureFrom(Exposure current, Input input) {
    var withPriority = input.priority().map(current::withPriority).orElse(current);

    var quota = input.responseQuota();
    var withQuota =
        !quota.present()
            ? withPriority
            : quota
                .value()
                .map(withPriority::withResponseQuota)
                .orElseGet(withPriority::withoutResponseQuota);

    return input.ignoresQuietPeriod().map(withQuota::ignoringQuietPeriod).orElse(withQuota);
  }
}

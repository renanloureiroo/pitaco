package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyDraftVersionAlreadyOpen;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotPublished;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyTransitionNotAllowed;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenSurveyVersionUseCase
    implements UseCase<OpenSurveyVersionUseCase.Input, SurveyVersionOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public OpenSurveyVersionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public SurveyVersionOutput execute(Input input) {
    var survey = SurveyScope.requireLocked(surveysRepository, input.applicationId(), input.surveyId());

    if (!survey.isPublished()) {
      throw new SurveyNotPublished(survey.id());
    }
    if (survey.getLifecycle() == SurveyLifecycle.ENDED) {
      throw new SurveyTransitionNotAllowed(survey.id(), survey.getLifecycle());
    }
    if (survey.hasDraft()) {
      throw new SurveyDraftVersionAlreadyOpen(survey.id());
    }

    var published =
        surveyVersionsRepository
            .findPublished(survey.id())
            .orElseThrow(() -> new SurveyVersionNotFound(survey.id().value()));

    var draft = published.copyAsDraft(published.getNumber() + 1);
    survey.openDraft(draft.getNumber());

    surveyVersionsRepository.create(draft);
    surveysRepository.update(survey);

    log.info(
        "Rascunho de versão aberto survey={} version={}", survey.id().value(), draft.getNumber());

    return SurveyVersionOutput.of(draft);
  }

  public record Input(String applicationId, String surveyId) {}
}

package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscardSurveyVersionUseCase
    implements UseCaseWithoutOutput<DiscardSurveyVersionUseCase.Input> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public DiscardSurveyVersionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public void execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    // Só há rascunho *de versão* a descartar em pesquisa já publicada: na que nunca publicou, o
    // rascunho é a própria pesquisa, e quem a descarta é DiscardSurveyUseCase.
    if (!survey.isPublished()) {
      throw new SurveyVersionNotFound(survey.id().value());
    }

    var draft =
        surveyVersionsRepository
            .findDraft(survey.id())
            .orElseThrow(() -> new SurveyVersionNotFound(survey.id().value()));

    surveyVersionsRepository.delete(draft.id());
    survey.discardDraft();
    surveysRepository.update(survey);

    log.info(
        "Rascunho de versão descartado survey={} version={}",
        survey.id().value(),
        draft.getNumber());
  }

  public record Input(String applicationId, String surveyId) {}
}

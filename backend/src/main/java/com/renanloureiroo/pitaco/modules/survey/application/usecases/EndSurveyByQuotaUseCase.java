package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.QuotaClosure;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

// Chamado pela coleta, dentro da transação da conclusão que atingiu a cota: o encerramento e a
// resposta que o causou entram juntos ou não entram. Não recusa nada — quem chega aqui é um
// respondente, e a pesquisa que já não está no ar simplesmente fica como está.
@Slf4j
public class EndSurveyByQuotaUseCase
    implements UseCaseWithoutOutput<EndSurveyByQuotaUseCase.Input> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final SurveyStateTransitionRepository surveyStateTransitionsRepository;

  public EndSurveyByQuotaUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      SurveyStateTransitionRepository surveyStateTransitionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.surveyStateTransitionsRepository = surveyStateTransitionsRepository;
  }

  public record Input(ApplicationId applicationId, SurveyId surveyId) {}

  @Override
  @Transactional
  public void execute(Input input) {
    var found = surveysRepository.findByIdAndApplicationId(input.surveyId(), input.applicationId());
    if (found.isEmpty()) {
      return;
    }

    var survey = found.get();
    var window =
        surveyVersionsRepository
            .findPublished(survey.id())
            .flatMap(SurveyVersion::trigger)
            .map(Trigger::window);

    if (!QuotaClosure.close(
        survey, window, Instant.now(), surveysRepository, surveyStateTransitionsRepository)) {
      return;
    }

    log.info(
        "Pesquisa encerrada por cota [{}] application={}",
        survey.id().value(),
        input.applicationId().value());
  }
}

package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import java.time.Instant;
import java.util.Optional;

// Os comandos sobre a pesquisa devolvem exatamente esta fotografia — criar, duplicar, editar,
// pausar, retomar e encerrar —, e a borda os apresenta com um presenter só. Um record aninhado
// em cada um seriam tipos idênticos e traduções idênticas.
public record SurveyOutput(
    String id,
    String applicationId,
    String name,
    SurveyState state,
    Optional<Integer> publishedVersionNumber,
    Optional<Integer> draftVersionNumber,
    int priority,
    Optional<Integer> responseQuota,
    boolean ignoresQuietPeriod,
    Optional<SurveyTemplate> template,
    FreeTextNotice freeTextNotice,
    Instant createdAt) {

  public static SurveyOutput of(Survey survey, SurveyState state) {
    return new SurveyOutput(
        survey.id().value(),
        survey.getApplicationId().value(),
        survey.getName().value(),
        state,
        survey.publishedVersionNumber(),
        survey.draftVersionNumber(),
        survey.getExposure().priority(),
        survey.getExposure().responseQuota(),
        survey.getExposure().ignoresQuietPeriod(),
        survey.template(),
        survey.getFreeTextNotice(),
        survey.getCreatedAt());
  }
}

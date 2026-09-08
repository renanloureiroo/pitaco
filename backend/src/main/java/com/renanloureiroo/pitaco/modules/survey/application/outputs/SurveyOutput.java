package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import java.time.Instant;
import java.util.Optional;

// Cinco comandos devolvem exatamente esta fotografia — criar, renomear, pausar, retomar e
// encerrar —, e a borda os apresenta com um presenter só. Um record aninhado em cada um
// seriam cinco tipos idênticos e cinco traduções idênticas.
public record SurveyOutput(
    String id,
    String applicationId,
    String name,
    SurveyState state,
    Optional<Integer> publishedVersionNumber,
    Optional<Integer> draftVersionNumber,
    Instant createdAt) {

  public static SurveyOutput of(Survey survey, SurveyState state) {
    return new SurveyOutput(
        survey.id().value(),
        survey.getApplicationId().value(),
        survey.getName().value(),
        state,
        survey.publishedVersionNumber(),
        survey.draftVersionNumber(),
        survey.getCreatedAt());
  }
}

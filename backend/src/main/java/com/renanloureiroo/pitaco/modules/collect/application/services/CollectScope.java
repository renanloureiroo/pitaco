package com.renanloureiroo.pitaco.modules.collect.application.services;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.errors.RespondentNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyNotFoundInApplication;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;

// Mesmo papel do SurveyScope na autoria: identificador malformado, recurso inexistente e recurso
// de outra aplicação são o mesmo 404, e é aqui que o erro nomeado é lançado (FR-034).
public final class CollectScope {

  private CollectScope() {}

  // Mesmo molde do SurveyScope na autoria: identificador malformado é o mesmo 404 da aplicação
  // que não existe.
  public static ApplicationId applicationIdOf(String rawId) {
    try {
      return ApplicationId.of(rawId);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(rawId);
    }
  }

  public static ApplicationId existingApplicationIdOf(
      ApplicationScopeGateway applications, String rawId) {
    var applicationId = applicationIdOf(rawId);

    if (applications.stateOf(applicationId).isEmpty()) {
      throw new ApplicationNotFound(rawId);
    }

    return applicationId;
  }

  public static SurveyId existingSurveyIdOf(
      SurveyScopeGateway surveys, ApplicationId applicationId, String rawId) {
    SurveyId surveyId;
    try {
      surveyId = SurveyId.of(rawId);
    } catch (DomainException malformed) {
      throw new SurveyNotFoundInApplication(rawId);
    }

    if (!surveys.existsInApplication(surveyId, applicationId)) {
      throw new SurveyNotFoundInApplication(rawId);
    }

    return surveyId;
  }

  public static Respondent existingRespondentOf(
      RespondentRepository respondents, ApplicationId applicationId, String rawId) {
    RespondentId respondentId;
    try {
      respondentId = RespondentId.of(rawId);
    } catch (DomainException malformed) {
      throw new RespondentNotFound(rawId);
    }

    return respondents
        .findById(respondentId, applicationId)
        .orElseThrow(() -> new RespondentNotFound(rawId));
  }
}

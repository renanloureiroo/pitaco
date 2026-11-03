package com.renanloureiroo.pitaco.modules.results.application.services;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.results.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway.SurveyScope;

// Mesmo papel do CollectScope: identificador malformado, pesquisa inexistente e pesquisa de
// outra aplicação são o mesmo 404.
public final class ResultsScope {

  private ResultsScope() {}

  public record Resolved(ApplicationId applicationId, SurveyId surveyId, SurveyScope scope) {}

  public static Resolved resolve(
      SurveyScopeGateway surveys, String rawApplicationId, String rawSurveyId) {
    ApplicationId applicationId;
    try {
      applicationId = ApplicationId.of(rawApplicationId);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(rawApplicationId);
    }

    SurveyId surveyId;
    try {
      surveyId = SurveyId.of(rawSurveyId);
    } catch (DomainException malformed) {
      throw new SurveyNotFound(rawSurveyId);
    }

    var scope =
        surveys
            .scopeOf(applicationId, surveyId)
            .orElseThrow(() -> new SurveyNotFound(rawSurveyId));

    return new Resolved(applicationId, surveyId, scope);
  }
}

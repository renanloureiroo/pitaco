package com.renanloureiroo.pitaco.modules.survey.application.services;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyContentFrozen;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;

// A mesma resolução de escopo aparece em quinze casos de uso desta fatia: identificador
// malformado, pesquisa inexistente e pesquisa de outra aplicação são o mesmo 404, e a escrita
// de conteúdo sempre vai para a única versão em rascunho.
public final class SurveyScope {

  private SurveyScope() {}

  public static ApplicationId applicationIdOf(String value) {
    try {
      return ApplicationId.of(value);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(value);
    }
  }

  public static ApplicationId activeApplicationIdOf(
      ApplicationScopeGateway applications, String value) {
    var applicationId = applicationIdOf(value);

    var state =
        applications.stateOf(applicationId).orElseThrow(() -> new ApplicationNotFound(value));
    if (state == ApplicationScopeState.INACTIVE) {
      throw new ApplicationIsInactive(applicationId);
    }

    return applicationId;
  }

  public static ApplicationId existingApplicationIdOf(
      ApplicationScopeGateway applications, String value) {
    var applicationId = applicationIdOf(value);

    if (applications.stateOf(applicationId).isEmpty()) {
      throw new ApplicationNotFound(value);
    }

    return applicationId;
  }

  public static Survey require(
      SurveyRepository surveysRepository, String applicationId, String surveyId) {
    ApplicationId scopedApplicationId;
    SurveyId id;
    try {
      scopedApplicationId = ApplicationId.of(applicationId);
      id = SurveyId.of(surveyId);
    } catch (DomainException malformed) {
      throw new SurveyNotFound(surveyId);
    }

    return surveysRepository
        .findByIdAndApplicationId(id, scopedApplicationId)
        .orElseThrow(() -> new SurveyNotFound(surveyId));
  }

  // A versão editável é a única em DRAFT; sem ela, o conteúdo está congelado (D-17).
  public static SurveyVersion requireEditableVersion(
      SurveyVersionRepository surveyVersionsRepository, Survey survey) {
    return surveyVersionsRepository
        .findDraft(survey.id())
        .orElseThrow(() -> new SurveyContentFrozen(survey.id()));
  }
}

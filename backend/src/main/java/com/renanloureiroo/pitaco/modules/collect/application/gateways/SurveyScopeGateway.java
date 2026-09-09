package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

// A travessia até a autoria só para existência: a pesquisa que nunca publicou existe, não tem
// exibição, e por isso não pode ser confundida com pesquisa inexistente (D-04).
public interface SurveyScopeGateway {

  boolean existsInApplication(SurveyId surveyId, ApplicationId applicationId);
}

package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import org.springframework.stereotype.Component;

@Component
public class SurveyScopeGatewaySurvey implements SurveyScopeGateway {

  private final SurveyScopeJpaRepository surveys;

  public SurveyScopeGatewaySurvey(SurveyScopeJpaRepository surveys) {
    this.surveys = surveys;
  }

  @Override
  public boolean existsInApplication(SurveyId surveyId, ApplicationId applicationId) {
    return surveys.existsByIdAndApplicationId(surveyId.value(), applicationId.value());
  }
}

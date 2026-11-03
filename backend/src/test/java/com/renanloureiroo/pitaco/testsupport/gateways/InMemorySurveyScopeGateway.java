package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import java.util.HashSet;
import java.util.Set;

public class InMemorySurveyScopeGateway implements SurveyScopeGateway {

  private record Scope(SurveyId surveyId, ApplicationId applicationId) {}

  private final Set<Scope> known = new HashSet<>();

  @Override
  public boolean existsInApplication(SurveyId surveyId, ApplicationId applicationId) {
    return known.contains(new Scope(surveyId, applicationId));
  }

  public InMemorySurveyScopeGateway with(SurveyId surveyId, ApplicationId applicationId) {
    known.add(new Scope(surveyId, applicationId));
    return this;
  }

  public SurveyId aSurveyIn(ApplicationId applicationId) {
    var surveyId = SurveyId.generate();
    with(surveyId, applicationId);
    return surveyId;
  }
}

package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryResultsSurveyScopeGateway implements SurveyScopeGateway {

  private record Scope(SurveyId surveyId, ApplicationId applicationId) {}

  private final Map<Scope, SurveyScope> known = new HashMap<>();

  @Override
  public Optional<SurveyScope> scopeOf(ApplicationId applicationId, SurveyId surveyId) {
    return Optional.ofNullable(known.get(new Scope(surveyId, applicationId)));
  }

  public SurveyId aPublishedSurveyIn(ApplicationId applicationId) {
    return with(applicationId, true, Optional.empty());
  }

  public SurveyId aDraftSurveyIn(ApplicationId applicationId) {
    return with(applicationId, false, Optional.empty());
  }

  public SurveyId aPublishedSurveyIn(ApplicationId applicationId, int openTextRetentionDays) {
    return with(applicationId, true, Optional.of(openTextRetentionDays));
  }

  public SurveyId aPublishedNpsSurveyIn(ApplicationId applicationId) {
    var surveyId = SurveyId.generate();
    known.put(
        new Scope(surveyId, applicationId),
        new SurveyScope(true, Optional.empty(), Optional.of(SurveyScopeGateway.NPS_TEMPLATE)));
    return surveyId;
  }

  private SurveyId with(
      ApplicationId applicationId, boolean everPublished, Optional<Integer> retentionDays) {
    var surveyId = SurveyId.generate();
    known.put(new Scope(surveyId, applicationId), new SurveyScope(everPublished, retentionDays));
    return surveyId;
  }
}

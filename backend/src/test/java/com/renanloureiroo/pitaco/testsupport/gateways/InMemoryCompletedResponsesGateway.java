package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.CompletedResponsesGateway;
import java.util.HashMap;
import java.util.Map;

public class InMemoryCompletedResponsesGateway implements CompletedResponsesGateway {

  private final Map<SurveyId, Long> completed = new HashMap<>();

  @Override
  public long completedResponsesOf(SurveyId surveyId) {
    return completed.getOrDefault(surveyId, 0L);
  }

  public InMemoryCompletedResponsesGateway withCompleted(SurveyId surveyId, long count) {
    completed.put(surveyId, count);
    return this;
  }
}

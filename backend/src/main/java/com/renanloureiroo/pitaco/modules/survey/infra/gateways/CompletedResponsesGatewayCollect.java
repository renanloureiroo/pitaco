package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.CompletedResponsesGateway;
import org.springframework.stereotype.Component;

@Component
public class CompletedResponsesGatewayCollect implements CompletedResponsesGateway {

  private final SurveyCompletedResponsesJpaRepository repository;

  public CompletedResponsesGatewayCollect(SurveyCompletedResponsesJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public long completedResponsesOf(SurveyId surveyId) {
    return repository.countCompleted(surveyId.value());
  }
}

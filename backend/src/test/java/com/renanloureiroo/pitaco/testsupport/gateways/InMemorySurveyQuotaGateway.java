package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyQuotaGateway;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySurveyQuotaGateway implements SurveyQuotaGateway {

  private final Map<SurveyId, Integer> quotas = new HashMap<>();
  private final List<SurveyId> endings = new ArrayList<>();
  private final List<SurveyId> locks = new ArrayList<>();

  @Override
  public Optional<Integer> responseQuotaOf(SurveyId surveyId) {
    return Optional.ofNullable(quotas.get(surveyId));
  }

  @Override
  public Optional<Integer> lockedResponseQuotaOf(SurveyId surveyId) {
    locks.add(surveyId);
    return responseQuotaOf(surveyId);
  }

  @Override
  public void endByQuota(ApplicationId applicationId, SurveyId surveyId) {
    endings.add(surveyId);
  }

  public InMemorySurveyQuotaGateway withQuota(SurveyId surveyId, int quota) {
    quotas.put(surveyId, quota);
    return this;
  }

  // Cada pedido de encerramento, inclusive os repetidos: quem os torna idempotentes é a autoria.
  public List<SurveyId> locks() {
    return List.copyOf(locks);
  }

  public List<SurveyId> endings() {
    return List.copyOf(endings);
  }
}

package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPrivacyApplicationGateway implements PrivacyApplicationGateway {

  private final Map<ApplicationId, RetentionPolicy> policies = new LinkedHashMap<>();

  public ApplicationId anApplication() {
    var applicationId = ApplicationId.generate();
    policies.put(applicationId, RetentionPolicy.of(applicationId, Optional.empty(), Optional.empty()));
    return applicationId;
  }

  public ApplicationId anApplicationRetaining(Integer answerDays, Integer textDays) {
    var applicationId = ApplicationId.generate();
    policies.put(
        applicationId,
        RetentionPolicy.of(applicationId, Optional.ofNullable(answerDays), Optional.ofNullable(textDays)));
    return applicationId;
  }

  @Override
  public Optional<RetentionPolicy> policyOf(ApplicationId applicationId) {
    return Optional.ofNullable(policies.get(applicationId));
  }

  @Override
  public List<RetentionPolicy> configuredPolicies() {
    return policies.values().stream().filter(RetentionPolicy::isConfigured).toList();
  }
}

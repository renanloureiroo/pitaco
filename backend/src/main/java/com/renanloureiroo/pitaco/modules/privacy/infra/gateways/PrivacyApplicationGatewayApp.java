package com.renanloureiroo.pitaco.modules.privacy.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories.PrivacyApplicationJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PrivacyApplicationGatewayApp implements PrivacyApplicationGateway {

  private final PrivacyApplicationJpaRepository repository;

  public PrivacyApplicationGatewayApp(PrivacyApplicationJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<RetentionPolicy> policyOf(ApplicationId applicationId) {
    return repository.policy(applicationId.value()).stream()
        .findFirst()
        .map(PrivacyApplicationGatewayApp::policyOf);
  }

  @Override
  public List<RetentionPolicy> configuredPolicies() {
    return repository.configuredPolicies().stream()
        .map(PrivacyApplicationGatewayApp::policyOf)
        .toList();
  }

  private static RetentionPolicy policyOf(Object[] row) {
    return RetentionPolicy.of(
        ApplicationId.of((String) row[0]), days(row[1]), days(row[2]));
  }

  private static Optional<Integer> days(Object value) {
    return Optional.ofNullable(value).map(days -> ((Number) days).intValue());
  }
}

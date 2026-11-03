package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryApplicationScopeGateway implements ApplicationScopeGateway {

  private final Map<ApplicationId, ApplicationScopeState> states = new HashMap<>();

  @Override
  public Optional<ApplicationScopeState> stateOf(ApplicationId applicationId) {
    return Optional.ofNullable(states.get(applicationId));
  }

  public InMemoryApplicationScopeGateway withActive(ApplicationId applicationId) {
    states.put(applicationId, ApplicationScopeState.ACTIVE);
    return this;
  }

  public InMemoryApplicationScopeGateway withInactive(ApplicationId applicationId) {
    states.put(applicationId, ApplicationScopeState.INACTIVE);
    return this;
  }

  public ApplicationId anActiveApplication() {
    var applicationId = ApplicationId.generate();
    withActive(applicationId);
    return applicationId;
  }

  public ApplicationId anInactiveApplication() {
    var applicationId = ApplicationId.generate();
    withInactive(applicationId);
    return applicationId;
  }

  public ApplicationId anUnknownApplication() {
    return ApplicationId.generate();
  }
}

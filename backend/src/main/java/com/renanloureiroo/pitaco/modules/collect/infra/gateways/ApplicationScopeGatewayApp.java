package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component("collectApplicationScopeGatewayApp")
public class ApplicationScopeGatewayApp implements ApplicationScopeGateway {

  private final ApplicationRepository applications;

  public ApplicationScopeGatewayApp(ApplicationRepository applications) {
    this.applications = applications;
  }

  @Override
  public Optional<ApplicationScopeState> stateOf(ApplicationId applicationId) {
    return applications
        .findById(applicationId)
        .map(
            application ->
                application.isActive()
                    ? ApplicationScopeState.ACTIVE
                    : ApplicationScopeState.INACTIVE);
  }

  @Override
  public Optional<Integer> effectiveOpenTextRetentionDaysOf(ApplicationId applicationId) {
    return applications
        .findById(applicationId)
        .flatMap(application -> application.effectiveOpenTextRetentionDays());
  }
}

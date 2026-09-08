package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeState;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
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
}

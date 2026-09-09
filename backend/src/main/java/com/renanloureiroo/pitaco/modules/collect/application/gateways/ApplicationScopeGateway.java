package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.util.Optional;

// Idêntica em forma à que `survey` declara, e deliberadamente não compartilhada: a porta pertence
// a quem a declara, e dois módulos nunca se conhecem pelo domínio.
public interface ApplicationScopeGateway {

  Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);
}

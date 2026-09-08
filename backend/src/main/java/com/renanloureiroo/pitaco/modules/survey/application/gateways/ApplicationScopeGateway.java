package com.renanloureiroo.pitaco.modules.survey.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.util.Optional;

// Exatamente o que a autoria precisa saber sobre a aplicação dona: ela existe? está ativa?
// Ausente quando não existe. A travessia até modules/app acontece só no adaptador.
public interface ApplicationScopeGateway {

  Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);
}

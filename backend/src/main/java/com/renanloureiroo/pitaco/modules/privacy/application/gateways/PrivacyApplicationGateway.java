package com.renanloureiroo.pitaco.modules.privacy.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;
import java.util.List;
import java.util.Optional;

// A travessia até a aplicação: existência e prazos de retenção. A política só existe para
// aplicação que existe; ausente significa aplicação desconhecida.
public interface PrivacyApplicationGateway {

  Optional<RetentionPolicy> policyOf(ApplicationId applicationId);

  // Só as aplicações com algum prazo configurado: sem prazo, não há o que descartar.
  List<RetentionPolicy> configuredPolicies();
}

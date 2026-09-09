package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.util.Optional;

// Idêntica em forma à que `survey` declara, e deliberadamente não compartilhada: a porta pertence
// a quem a declara, e dois módulos nunca se conhecem pelo domínio.
public interface ApplicationScopeGateway {

  Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);

  // Prazo efetivo de retenção de texto livre: o específico, ou o geral quando ele não
  // existe. Ausente significa sem expiração (D-07). Fora de stateOf, que é caminho quente
  // da elegibilidade (D-08).
  Optional<Integer> effectiveOpenTextRetentionDaysOf(ApplicationId applicationId);
}

package com.renanloureiroo.pitaco.modules.privacy.application.services;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.RetentionPolicy;

// Mesmo papel do CollectScope: identificador malformado e aplicação inexistente são o mesmo 404.
// Aplicação inativa continua passando: a exclusão e a retenção não dependem de ela coletar.
public final class PrivacyScope {

  private PrivacyScope() {}

  public static RetentionPolicy existingApplicationOf(
      PrivacyApplicationGateway applications, String rawId) {
    ApplicationId applicationId;
    try {
      applicationId = ApplicationId.of(rawId);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(rawId);
    }

    return applications
        .policyOf(applicationId)
        .orElseThrow(() -> new ApplicationNotFound(rawId));
  }
}

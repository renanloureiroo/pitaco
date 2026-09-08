package com.renanloureiroo.pitaco.modules.app.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

// O segredo em claro não é campo daqui: sai de issue dentro de Issued e segue por fora até a
// resposta da emissão, de modo que não há getter para vazá-lo nem coluna para persistí-lo.
@Getter
public final class ApiKey extends Entity<ApiKeyId> {

  private static final String APPLICATION_REQUIRED_CODE = "api_key.application_required";
  private static final String ALREADY_REVOKED_CODE = "api_key.already_revoked";
  private static final String REVOKED_BEFORE_CREATED_CODE = "api_key.revoked_before_created";

  private final ApplicationId applicationId;
  private final ApiKeyLabel label;
  private final ApiKeySecret secret;
  private final Instant createdAt;

  private Instant revokedAt;

  private ApiKey(
      ApiKeyId id,
      ApplicationId applicationId,
      ApiKeyLabel label,
      ApiKeySecret secret,
      Instant createdAt,
      Instant revokedAt) {
    super(id);

    if (applicationId == null) {
      throw new DomainException(
          ErrorType.VALIDATION,
          APPLICATION_REQUIRED_CODE,
          "Chave de API precisa pertencer a uma aplicação");
    }
    if (revokedAt != null && revokedAt.isBefore(createdAt)) {
      throw new DomainException(
          ErrorType.VALIDATION,
          REVOKED_BEFORE_CREATED_CODE,
          "Revogação não pode ser anterior à criação da chave");
    }

    this.applicationId = applicationId;
    this.label = label;
    this.secret = secret;
    this.createdAt = createdAt;
    this.revokedAt = revokedAt;
  }

  public record Issued(ApiKey apiKey, String plainSecret) {}

  public static Issued issue(ApplicationId applicationId, ApiKeyLabel label) {
    var generated = ApiKeySecret.generate();
    var apiKey =
        new ApiKey(
            ApiKeyId.generate(), applicationId, label, generated.secret(), Instant.now(), null);

    return new Issued(apiKey, generated.plainText());
  }

  public static ApiKey restore(
      ApiKeyId id,
      ApplicationId applicationId,
      ApiKeyLabel label,
      ApiKeySecret secret,
      Instant createdAt,
      Instant revokedAt) {
    return new ApiKey(id, applicationId, label, secret, createdAt, revokedAt);
  }

  public void revoke() {
    if (revokedAt != null) {
      throw new DomainException(
          ErrorType.CONFLICT, ALREADY_REVOKED_CODE, "Chave de API já foi revogada");
    }
    this.revokedAt = Instant.now();
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public ApiKeyStatus status() {
    return isRevoked() ? ApiKeyStatus.REVOKED : ApiKeyStatus.ACTIVE;
  }

  public Optional<Instant> revokedAt() {
    return Optional.ofNullable(revokedAt);
  }
}

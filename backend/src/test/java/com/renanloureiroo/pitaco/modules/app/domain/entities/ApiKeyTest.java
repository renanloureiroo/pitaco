package com.renanloureiroo.pitaco.modules.app.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKey")
class ApiKeyTest {

  private static final ApiKeyLabel LABEL = ApiKeyLabel.of("app iOS");

  @Test
  void nasce_valida_e_entrega_o_segredo_em_claro_uma_vez() {
    var issued = ApiKey.issue(ApplicationId.generate(), LABEL);
    var apiKey = issued.apiKey();

    assertThat(ApiKeySecret.hashOf(issued.plainSecret()))
        .isEqualTo(apiKey.getSecret().hash());
    assertThat(issued.plainSecret()).startsWith(apiKey.getSecret().prefix() + "_");
    assertThat(apiKey.getCreatedAt()).isNotNull();
    assertThat(apiKey.revokedAt()).isEmpty();
    assertThat(apiKey.isRevoked()).isFalse();
  }

  @Test
  void nao_guarda_o_segredo_em_claro() {
    var issued = ApiKey.issue(ApplicationId.generate(), LABEL);

    assertThat(
            Arrays.stream(ApiKey.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType().getName()))
        .doesNotContain(String.class.getName());
    assertThat(issued.apiKey().toString()).doesNotContain(issued.plainSecret());
  }

  @Test
  void revogar_marca_o_instante() {
    var apiKey = ApiKey.issue(ApplicationId.generate(), LABEL).apiKey();

    apiKey.revoke();

    assertThat(apiKey.isRevoked()).isTrue();
    assertThat(apiKey.revokedAt()).isPresent();
    assertThat(apiKey.revokedAt().orElseThrow()).isAfterOrEqualTo(apiKey.getCreatedAt());
  }

  @Test
  void revogar_de_novo_e_conflito_e_preserva_o_instante_gravado() {
    var apiKey = ApiKey.issue(ApplicationId.generate(), LABEL).apiKey();
    apiKey.revoke();
    var revokedAt = apiKey.revokedAt().orElseThrow();

    assertThatThrownBy(apiKey::revoke)
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.CONFLICT);
              assertThat(domainError.code()).isEqualTo("api_key.already_revoked");
            });

    assertThat(apiKey.revokedAt()).contains(revokedAt);
  }

  @Test
  void nasce_com_estado_ativo_e_passa_a_revogada_ao_revogar() {
    var apiKey = ApiKey.issue(ApplicationId.generate(), LABEL).apiKey();

    assertThat(apiKey.status()).isEqualTo(ApiKeyStatus.ACTIVE);

    apiKey.revoke();

    assertThat(apiKey.status()).isEqualTo(ApiKeyStatus.REVOKED);
  }

  @Test
  void volta_do_banco_revogada_quando_o_instante_de_revogacao_estava_gravado() {
    var createdAt = Instant.now().minus(2, ChronoUnit.DAYS);

    var revoked =
        ApiKey.restore(
            ApiKeyId.generate(),
            ApplicationId.generate(),
            LABEL,
            ApiKeySecret.generate().secret(),
            createdAt,
            createdAt.plus(1, ChronoUnit.DAYS));
    var active =
        ApiKey.restore(
            ApiKeyId.generate(),
            ApplicationId.generate(),
            LABEL,
            ApiKeySecret.generate().secret(),
            createdAt,
            null);

    assertThat(revoked.status()).isEqualTo(ApiKeyStatus.REVOKED);
    assertThat(active.status()).isEqualTo(ApiKeyStatus.ACTIVE);
  }

  @Test
  void nao_existe_caminho_de_volta_ao_estado_valido() {
    assertThat(Arrays.stream(ApiKey.class.getMethods()).map(Method::getName))
        .doesNotContain("reactivate", "restoreValidity", "unrevoke");
  }

  @Test
  void volta_do_banco_com_o_estado_que_estava_gravado() {
    var id = ApiKeyId.generate();
    var applicationId = ApplicationId.generate();
    var secret = ApiKeySecret.generate().secret();
    var createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
    var revokedAt = createdAt.plus(1, ChronoUnit.DAYS);

    var apiKey = ApiKey.restore(id, applicationId, LABEL, secret, createdAt, revokedAt);

    assertThat(apiKey.id()).isEqualTo(id);
    assertThat(apiKey.getApplicationId()).isEqualTo(applicationId);
    assertThat(apiKey.getLabel()).isEqualTo(LABEL);
    assertThat(apiKey.getSecret()).isEqualTo(secret);
    assertThat(apiKey.getCreatedAt()).isEqualTo(createdAt);
    assertThat(apiKey.revokedAt()).contains(revokedAt);
  }

  @Test
  void rejeita_revogacao_anterior_a_criacao() {
    var createdAt = Instant.now();

    assertThatThrownBy(
            () ->
                ApiKey.restore(
                    ApiKeyId.generate(),
                    ApplicationId.generate(),
                    LABEL,
                    ApiKeySecret.generate().secret(),
                    createdAt,
                    createdAt.minusSeconds(1)))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("api_key.revoked_before_created");
            });
  }

  @Test
  void rejeita_chave_sem_aplicacao() {
    assertThatThrownBy(() -> ApiKey.issue(null, LABEL))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("api_key.application_required");
            });
  }
}

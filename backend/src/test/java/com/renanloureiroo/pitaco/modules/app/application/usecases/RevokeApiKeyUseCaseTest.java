package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyNotFound;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RevokeApiKeyUseCase")
class RevokeApiKeyUseCaseTest {

  private InMemoryApiKeyRepository apiKeys;
  private RevokeApiKeyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    apiKeys = new InMemoryApiKeyRepository();
    useCase = new RevokeApiKeyUseCase(apiKeys);
    applicationId = ApplicationId.generate();
  }

  private ApiKey anApiKey() {
    return ApiKeyFactory.anApiKey().forApplication(applicationId).buildSavedIn(apiKeys);
  }

  private RevokeApiKeyUseCase.Input inputFor(ApiKey apiKey) {
    return new RevokeApiKeyUseCase.Input(applicationId.value(), apiKey.id().value());
  }

  private ApiKey reload(ApiKey apiKey) {
    return apiKeys.findByIdAndApplicationId(apiKey.id(), apiKey.getApplicationId()).orElseThrow();
  }

  @Test
  void revoga_a_chave() {
    var apiKey = anApiKey();

    useCase.execute(inputFor(apiKey));

    var reloaded = reload(apiKey);
    assertThat(reloaded.isRevoked()).isTrue();
    assertThat(reloaded.revokedAt()).isPresent();
  }

  @Test
  void recusa_chave_inexistente() {
    var input = new RevokeApiKeyUseCase.Input(applicationId.value(), UUID.randomUUID().toString());

    assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(ApiKeyNotFound.class);
  }

  @Test
  void identificador_de_chave_malformado_e_indistinguivel_de_inexistente() {
    var input = new RevokeApiKeyUseCase.Input(applicationId.value(), "nao-e-um-id");

    assertThatThrownBy(() -> useCase.execute(input))
        .isInstanceOf(ApiKeyNotFound.class)
        .satisfies(error -> assertThat(((ApiKeyNotFound) error).code())
            .isEqualTo("api_key.not_found"));
  }

  @Test
  void identificador_de_aplicacao_malformado_tambem_vira_chave_nao_encontrada() {
    var apiKey = anApiKey();
    var input = new RevokeApiKeyUseCase.Input("nao-e-um-id", apiKey.id().value());

    assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(ApiKeyNotFound.class);

    assertThat(reload(apiKey).isRevoked()).isFalse();
  }

  @Test
  void chave_de_outra_aplicacao_e_chave_nao_encontrada() {
    var apiKey = anApiKey();
    var input =
        new RevokeApiKeyUseCase.Input(
            ApplicationId.generate().value(), apiKey.id().value());

    assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(ApiKeyNotFound.class);

    assertThat(reload(apiKey).isRevoked()).isFalse();
  }

  @Test
  void recusa_chave_ja_revogada_preservando_o_instante() {
    var apiKey = anApiKey();
    useCase.execute(inputFor(apiKey));
    var revokedAt = reload(apiKey).revokedAt().orElseThrow();

    assertThatThrownBy(() -> useCase.execute(inputFor(apiKey)))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.CONFLICT);
              assertThat(domainError.code()).isEqualTo("api_key.already_revoked");
            });

    assertThat(reload(apiKey).revokedAt()).contains(revokedAt);
  }

  @Test
  void perder_a_corrida_vira_conflito_e_nao_sobrescreve_o_instante() {
    var apiKey = anApiKey();
    apiKeys.loseNextRevoke();

    assertThatThrownBy(() -> useCase.execute(inputFor(apiKey)))
        .isInstanceOf(DomainException.class)
        .satisfies(error -> assertThat(((DomainException) error).code())
            .isEqualTo("api_key.already_revoked"));

    assertThat(reload(apiKey).isRevoked()).isTrue();
  }

  @Test
  void nao_toca_nas_demais_chaves_da_aplicacao() {
    var revoked = anApiKey();
    var untouched = anApiKey();

    useCase.execute(inputFor(revoked));

    assertThat(reload(untouched).isRevoked()).isFalse();
  }
}

package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyNotFound;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetApiKeyUseCase")
class GetApiKeyUseCaseTest {

  private InMemoryApplicationRepository applications;
  private InMemoryApiKeyRepository apiKeys;
  private GetApiKeyUseCase useCase;
  private Application application;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    apiKeys = new InMemoryApiKeyRepository();
    useCase = new GetApiKeyUseCase(applications, apiKeys);
    application = ApplicationFactory.anApplication().buildSavedIn(applications);
  }

  private GetApiKeyUseCase.Output get(String apiKeyId) {
    return useCase.execute(new GetApiKeyUseCase.Input(application.id().value(), apiKeyId));
  }

  @Test
  @DisplayName("Devolve os dados públicos da chave, sem nenhum segredo")
  void encontra_a_chave() {
    var apiKey =
        ApiKeyFactory.anApiKey()
            .forApplication(application.id())
            .withLabel("app Android")
            .buildSavedIn(apiKeys);

    var output = get(apiKey.id().value());

    assertThat(output.id()).isEqualTo(apiKey.id().value());
    assertThat(output.applicationId()).isEqualTo(application.id().value());
    assertThat(output.label()).isEqualTo("app Android");
    assertThat(output.prefix()).isEqualTo(apiKey.getSecret().prefix());
    assertThat(output.status()).isEqualTo(ApiKeyStatus.ACTIVE);
    assertThat(output.createdAt()).isEqualTo(apiKey.getCreatedAt());
    assertThat(output.revokedAt()).isEmpty();
  }

  @Test
  @DisplayName("Encontra a chave revogada, com o instante da revogação (FR-017)")
  void encontra_a_chave_revogada() {
    var apiKey =
        ApiKeyFactory.anApiKey().forApplication(application.id()).revoked().buildSavedIn(apiKeys);

    var output = get(apiKey.id().value());

    assertThat(output.status()).isEqualTo(ApiKeyStatus.REVOKED);
    assertThat(output.revokedAt()).contains(apiKey.revokedAt().orElseThrow());
  }

  @Test
  @DisplayName("Chave inexistente, de outra aplicação ou malformada recusam do mesmo jeito")
  void recusa_o_que_nao_e_chave_desta_aplicacao() {
    var other = ApplicationFactory.anApplication().withSlug("outra-app").buildSavedIn(applications);
    var theirs = ApiKeyFactory.anApiKey().forApplication(other.id()).buildSavedIn(apiKeys);

    expectApiKeyNotFound(() -> get(UUID.randomUUID().toString()));
    expectApiKeyNotFound(() -> get(theirs.id().value()));
    expectApiKeyNotFound(() -> get("nao-e-um-id"));
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado recusam como aplicação")
  void recusa_aplicacao_que_nao_existe() {
    var apiKey = ApiKeyFactory.anApiKey().forApplication(application.id()).buildSavedIn(apiKeys);

    expectApplicationNotFound(
        () ->
            useCase.execute(
                new GetApiKeyUseCase.Input(
                    UUID.randomUUID().toString(), apiKey.id().value())));
    expectApplicationNotFound(
        () -> useCase.execute(new GetApiKeyUseCase.Input("nao-e-um-id", apiKey.id().value())));
  }

  private void expectApiKeyNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(ApiKeyNotFound.class)
        .satisfies(
            error ->
                assertThat(((ApplicationException) error).code()).isEqualTo("api_key.not_found"));
  }

  private void expectApplicationNotFound(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(
            error ->
                assertThat(((ApplicationException) error).code())
                    .isEqualTo("application.not_found"));
  }
}

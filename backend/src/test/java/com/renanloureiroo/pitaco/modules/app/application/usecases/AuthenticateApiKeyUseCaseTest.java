package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyInvalid;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticateApiKeyUseCase")
class AuthenticateApiKeyUseCaseTest {

  private final InMemoryApiKeyRepository apiKeys = new InMemoryApiKeyRepository();
  private final InMemoryApplicationRepository applications = new InMemoryApplicationRepository();
  private final DirectTransactor transactor = new DirectTransactor();

  private AuthenticateApiKeyUseCase useCase;
  private Application application;

  @BeforeEach
  void setUp() {
    useCase = new AuthenticateApiKeyUseCase(apiKeys, applications, transactor);
    application = ApplicationFactory.anApplication().buildSavedIn(applications);
  }

  @Test
  @DisplayName("Chave válida devolve a aplicação e o estado dela")
  void chave_valida_devolve_a_aplicacao() {
    var issued = issuedKey();

    var output = useCase.execute(new AuthenticateApiKeyUseCase.Input(issued.plainSecret()));

    assertThat(output.applicationId()).isEqualTo(application.id().value());
    assertThat(output.applicationActive()).isTrue();
  }

  @Test
  @DisplayName("A chave de uma aplicação inativa autentica, e o estado vai junto")
  void aplicacao_inativa_autentica_com_o_estado() {
    var inactive = ApplicationFactory.anApplication().inactive().buildSavedIn(applications);
    var issued = ApiKey.issue(inactive.id(), issuedKeyLabel());
    apiKeys.create(issued.apiKey());

    var output = useCase.execute(new AuthenticateApiKeyUseCase.Input(issued.plainSecret()));

    assertThat(output.applicationId()).isEqualTo(inactive.id().value());
    assertThat(output.applicationActive()).isFalse();
  }

  @Test
  @DisplayName("Chave revogada e chave desconhecida dão exatamente o mesmo erro")
  void revogada_e_desconhecida_sao_indistinguiveis() {
    var revoked = ApiKey.issue(application.id(), issuedKeyLabel());
    revoked.apiKey().revoke();
    apiKeys.create(revoked.apiKey());

    var doRevogada =
        assertThatThrownBy(
            () -> useCase.execute(new AuthenticateApiKeyUseCase.Input(revoked.plainSecret())));
    var doDesconhecida =
        assertThatThrownBy(
            () -> useCase.execute(new AuthenticateApiKeyUseCase.Input("pit_0000_desconhecida")));

    doRevogada.isInstanceOf(ApiKeyInvalid.class);
    doDesconhecida.isInstanceOf(ApiKeyInvalid.class);
    assertThat(new ApiKeyInvalid().code()).isEqualTo("api_key.invalid");
  }

  @Test
  @DisplayName("O primeiro uso registra o último uso")
  void primeiro_uso_registra() {
    var issued = issuedKey();

    useCase.execute(new AuthenticateApiKeyUseCase.Input(issued.plainSecret()));

    assertThat(apiKeys.touches()).isEqualTo(1);
    assertThat(transactor.executions()).isEqualTo(1);
  }

  @Test
  @DisplayName("Uso registrado há menos de um minuto não volta a gravar")
  void uso_recente_nao_grava_de_novo() {
    var recent = Instant.now().minus(Duration.ofSeconds(10));
    var issued = issuedKeyLastUsedAt(recent);

    useCase.execute(new AuthenticateApiKeyUseCase.Input(issued.plainSecret()));

    assertThat(apiKeys.touches()).isZero();
    assertThat(transactor.executions())
        .describedAs("o caminho quente não abre transação quando nada é gravado")
        .isZero();
  }

  @Test
  @DisplayName("Uso registrado há mais de um minuto volta a gravar")
  void uso_antigo_grava_de_novo() {
    var old = Instant.now().minus(Duration.ofMinutes(5));
    var issued = issuedKeyLastUsedAt(old);

    useCase.execute(new AuthenticateApiKeyUseCase.Input(issued.plainSecret()));

    assertThat(apiKeys.touches()).isEqualTo(1);
  }

  @Test
  @DisplayName("Chave de aplicação que sumiu é tratada como chave inválida")
  void aplicacao_inexistente_e_chave_invalida() {
    var orphan = ApiKey.issue(ApplicationFactory.anApplication().build().id(), issuedKeyLabel());
    apiKeys.create(orphan.apiKey());

    assertThatThrownBy(
            () -> useCase.execute(new AuthenticateApiKeyUseCase.Input(orphan.plainSecret())))
        .isInstanceOf(ApiKeyInvalid.class);
  }

  private ApiKey.Issued issuedKey() {
    var issued = ApiKey.issue(application.id(), issuedKeyLabel());
    apiKeys.create(issued.apiKey());
    return issued;
  }

  private ApiKey.Issued issuedKeyLastUsedAt(Instant lastUsedAt) {
    var issued = ApiKey.issue(application.id(), issuedKeyLabel());
    var used =
        ApiKey.restore(
            issued.apiKey().id(),
            issued.apiKey().getApplicationId(),
            issued.apiKey().getLabel(),
            issued.apiKey().getSecret(),
            issued.apiKey().getCreatedAt(),
            null,
            lastUsedAt);
    apiKeys.create(used);
    return issued;
  }

  private static ApiKeyLabel issuedKeyLabel() {
    return ApiKeyLabel.of(ApiKeyFactory.DEFAULT_LABEL);
  }
}

package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssueApiKeyUseCase")
class IssueApiKeyUseCaseTest {

  private InMemoryApplicationRepository applications;
  private InMemoryApiKeyRepository apiKeys;
  private IssueApiKeyUseCase useCase;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    apiKeys = new InMemoryApiKeyRepository();
    useCase = new IssueApiKeyUseCase(applications, apiKeys);
  }

  @Test
  void emite_a_chave_e_devolve_o_segredo_em_claro() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);

    var output =
        useCase.execute(new IssueApiKeyUseCase.Input(application.id().value(), "app iOS"));

    var saved = apiKeys.findAll();
    assertThat(saved).hasSize(1);

    var apiKey = saved.getFirst();
    assertThat(output.id()).isEqualTo(apiKey.id().value());
    assertThat(output.applicationId()).isEqualTo(application.id().value());
    assertThat(output.label()).isEqualTo("app iOS");
    assertThat(output.prefix()).isEqualTo(apiKey.getSecret().prefix());
    assertThat(output.createdAt()).isEqualTo(apiKey.getCreatedAt());
    assertThat(ApiKeySecret.hashOf(output.plainSecret())).isEqualTo(apiKey.getSecret().hash());
    assertThat(apiKey.revokedAt()).isEmpty();
  }

  @Test
  void duas_emissoes_coexistem_com_identificadores_e_segredos_distintos() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);
    var input = new IssueApiKeyUseCase.Input(application.id().value(), "app iOS");

    var first = useCase.execute(input);
    var second = useCase.execute(input);

    assertThat(first.id()).isNotEqualTo(second.id());
    assertThat(first.plainSecret()).isNotEqualTo(second.plainSecret());
    assertThat(apiKeys.findAll()).hasSize(2);
  }

  @Test
  void recusa_aplicacao_inexistente() {
    var input = new IssueApiKeyUseCase.Input(UUID.randomUUID().toString(), "app iOS");

    assertThatThrownBy(() -> useCase.execute(input))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(error -> assertThat(((ApplicationNotFound) error).code())
            .isEqualTo("application.not_found"));

    assertThat(apiKeys.isEmpty()).isTrue();
  }

  @Test
  void identificador_de_aplicacao_malformado_e_indistinguivel_de_inexistente() {
    var input = new IssueApiKeyUseCase.Input("nao-e-um-id", "app iOS");

    assertThatThrownBy(() -> useCase.execute(input))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(error -> assertThat(((ApplicationNotFound) error).code())
            .isEqualTo("application.not_found"));

    assertThat(apiKeys.isEmpty()).isTrue();
  }

  @Test
  void recusa_aplicacao_inativa() {
    var application = ApplicationFactory.anApplication().inactive().buildSavedIn(applications);
    var input = new IssueApiKeyUseCase.Input(application.id().value(), "app iOS");

    assertThatThrownBy(() -> useCase.execute(input))
        .isInstanceOf(ApplicationIsInactive.class)
        .satisfies(
            error -> {
              var inactive = (ApplicationIsInactive) error;
              assertThat(inactive.code()).isEqualTo("application.inactive");
              assertThat(inactive.applicationId()).isEqualTo(application.id());
            });

    assertThat(apiKeys.isEmpty()).isTrue();
  }

  @Test
  void recusa_rotulo_invalido() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);
    var input = new IssueApiKeyUseCase.Input(application.id().value(), "  ");

    assertThatThrownBy(() -> useCase.execute(input))
        .isInstanceOf(DomainException.class)
        .satisfies(error -> assertThat(((DomainException) error).code())
            .isEqualTo("api_key.label_invalid"));

    assertThat(apiKeys.isEmpty()).isTrue();
  }

  @Test
  void a_emissao_nao_altera_a_aplicacao() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);
    var updatedAt = application.getUpdatedAt();

    useCase.execute(new IssueApiKeyUseCase.Input(application.id().value(), "app iOS"));

    assertThat(applications.findById(ApplicationId.of(application.id().value())).orElseThrow())
        .satisfies(reloaded -> assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt));
  }
}

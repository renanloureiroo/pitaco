package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Desativar e reativar aplicação")
class ApplicationStatusUseCasesTest {

  private InMemoryApplicationRepository applications;
  private DeactivateApplicationUseCase deactivate;
  private ActivateApplicationUseCase activate;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    deactivate = new DeactivateApplicationUseCase(applications);
    activate = new ActivateApplicationUseCase(applications);
  }

  @Nested
  @DisplayName("DeactivateApplicationUseCase")
  class Deactivate {

    @Test
    @DisplayName("Desativa a aplicação ativa e grava")
    void desativa() {
      var application = ApplicationFactory.anApplication().buildSavedIn(applications);

      var output =
          deactivate.execute(new DeactivateApplicationUseCase.Input(application.id().value()));

      assertThat(output.status()).isEqualTo(Status.INACTIVE);
      assertThat(applications.findById(application.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("Já inativa: responde o estado sem alterar o updatedAt")
    void e_idempotente() {
      var application = ApplicationFactory.anApplication().inactive().buildSavedIn(applications);
      var updatedAt = application.getUpdatedAt();

      var output =
          deactivate.execute(new DeactivateApplicationUseCase.Input(application.id().value()));

      assertThat(output.status()).isEqualTo(Status.INACTIVE);
      assertThat(output.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Desativar não apaga as políticas")
    void preserva_as_politicas() {
      var application =
          ApplicationFactory.anApplicationWithPolicies().buildSavedIn(applications);

      var output =
          deactivate.execute(new DeactivateApplicationUseCase.Input(application.id().value()));

      assertThat(output.quietPeriodDays()).contains(15);
      assertThat(output.retentionDays()).contains(180);
      assertThat(output.openTextRetentionDays()).contains(30);
    }

    @Test
    @DisplayName("Identificador inexistente e malformado recusam do mesmo jeito")
    void recusa_inexistente_e_malformado() {
      assertThatThrownBy(
              () ->
                  deactivate.execute(
                      new DeactivateApplicationUseCase.Input(UUID.randomUUID().toString())))
          .isInstanceOf(ApplicationNotFound.class);
      assertThatThrownBy(
              () -> deactivate.execute(new DeactivateApplicationUseCase.Input("nao-e-um-id")))
          .isInstanceOf(ApplicationNotFound.class);
    }
  }

  @Nested
  @DisplayName("ActivateApplicationUseCase")
  class Activate {

    @Test
    @DisplayName("Reativa a aplicação inativa e grava")
    void reativa() {
      var application = ApplicationFactory.anApplication().inactive().buildSavedIn(applications);

      var output =
          activate.execute(new ActivateApplicationUseCase.Input(application.id().value()));

      assertThat(output.status()).isEqualTo(Status.ACTIVE);
      assertThat(applications.findById(application.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("Já ativa: responde o estado sem alterar o updatedAt")
    void e_idempotente() {
      var application = ApplicationFactory.anApplication().buildSavedIn(applications);
      var updatedAt = application.getUpdatedAt();

      var output =
          activate.execute(new ActivateApplicationUseCase.Input(application.id().value()));

      assertThat(output.status()).isEqualTo(Status.ACTIVE);
      assertThat(output.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Identificador inexistente e malformado recusam do mesmo jeito")
    void recusa_inexistente_e_malformado() {
      assertThatThrownBy(
              () ->
                  activate.execute(
                      new ActivateApplicationUseCase.Input(UUID.randomUUID().toString())))
          .isInstanceOf(ApplicationNotFound.class);
      assertThatThrownBy(
              () -> activate.execute(new ActivateApplicationUseCase.Input("nao-e-um-id")))
          .isInstanceOf(ApplicationNotFound.class);
    }
  }
}

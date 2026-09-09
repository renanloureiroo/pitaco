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
import org.junit.jupiter.api.Test;

@DisplayName("GetApplicationUseCase")
class GetApplicationUseCaseTest {

  private InMemoryApplicationRepository applications;
  private GetApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    useCase = new GetApplicationUseCase(applications);
  }

  @Test
  @DisplayName("Devolve a aplicação inteira, com os prazos configurados")
  void devolve_a_aplicacao_inteira() {
    var application =
        ApplicationFactory.anApplicationWithPolicies().buildSavedIn(applications);

    var output = useCase.execute(new GetApplicationUseCase.Input(application.id().value()));

    assertThat(output.id()).isEqualTo(application.id().value());
    assertThat(output.slug()).isEqualTo("acme-app");
    assertThat(output.name()).isEqualTo("Acme App");
    assertThat(output.status()).isEqualTo(Status.ACTIVE);
    assertThat(output.quietPeriodDays()).contains(15);
    assertThat(output.retentionDays()).contains(180);
    assertThat(output.openTextRetentionDays()).contains(30);
    assertThat(output.createdAt()).isEqualTo(application.getCreatedAt());
    assertThat(output.updatedAt()).isEqualTo(application.getUpdatedAt());
  }

  @Test
  @DisplayName("Prazo não configurado é ausência, nunca zero")
  void prazo_nao_configurado_e_ausencia() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);

    var output = useCase.execute(new GetApplicationUseCase.Input(application.id().value()));

    assertThat(output.quietPeriodDays()).isEmpty();
    assertThat(output.retentionDays()).isEmpty();
    assertThat(output.openTextRetentionDays()).isEmpty();
  }

  @Test
  @DisplayName("Prazo de texto livre é o configurado, não o herdado do prazo geral")
  void nao_expoe_o_prazo_derivado() {
    var application =
        ApplicationFactory.anApplication().withRetention(180).buildSavedIn(applications);

    var output = useCase.execute(new GetApplicationUseCase.Input(application.id().value()));

    assertThat(output.retentionDays()).contains(180);
    assertThat(output.openTextRetentionDays()).isEmpty();
    assertThat(application.effectiveOpenTextRetentionDays()).contains(180);
  }

  @Test
  @DisplayName("Aplicação inativa é encontrada normalmente")
  void aplicacao_inativa_e_encontrada() {
    var application =
        ApplicationFactory.anApplication().inactive().buildSavedIn(applications);

    var output = useCase.execute(new GetApplicationUseCase.Input(application.id().value()));

    assertThat(output.status()).isEqualTo(Status.INACTIVE);
  }

  @Test
  @DisplayName("Identificador inexistente e malformado recusam do mesmo jeito")
  void recusa_inexistente_e_malformado() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetApplicationUseCase.Input(UUID.randomUUID().toString())))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(
            error ->
                assertThat(((ApplicationNotFound) error).code()).isEqualTo("application.not_found"));

    assertThatThrownBy(() -> useCase.execute(new GetApplicationUseCase.Input("nao-e-um-id")))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(
            error ->
                assertThat(((ApplicationNotFound) error).code()).isEqualTo("application.not_found"));
  }
}

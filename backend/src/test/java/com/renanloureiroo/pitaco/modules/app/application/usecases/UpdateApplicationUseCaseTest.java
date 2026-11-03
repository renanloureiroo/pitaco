package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateApplicationUseCase")
class UpdateApplicationUseCaseTest {

  private InMemoryApplicationRepository applications;
  private UpdateApplicationUseCase sut;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    sut = new UpdateApplicationUseCase(applications);
  }

  private static UpdateApplicationUseCase.Input untouched(String applicationId) {
    return new UpdateApplicationUseCase.Input(
        applicationId, Optional.empty(), Patch.absent(), Patch.absent(), Patch.absent());
  }

  @Test
  @DisplayName("Campo ausente não mexe em nada, nem no updatedAt")
  void campo_ausente_nao_mexe() {
    var application =
        ApplicationFactory.anApplicationWithPolicies().buildSavedIn(applications);
    var updatedAt = application.getUpdatedAt();

    var output = sut.execute(untouched(application.id().value()));

    assertThat(output.name()).isEqualTo("Acme App");
    assertThat(output.quietPeriodDays()).contains(15);
    assertThat(output.retentionDays()).contains(180);
    assertThat(output.openTextRetentionDays()).contains(30);
    assertThat(output.updatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("Renomeia sem tocar no slug")
  void renomeia() {
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);

    var output =
        sut.execute(
            new UpdateApplicationUseCase.Input(
                application.id().value(),
                Optional.of("Acme Brasil"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()));

    assertThat(output.name()).isEqualTo("Acme Brasil");
    assertThat(output.slug()).isEqualTo("acme-app");
    assertThat(applications.findById(application.id()).orElseThrow().getName().value())
        .isEqualTo("Acme Brasil");
  }

  @Test
  @DisplayName("Valor define e nulo remove cada prazo, de forma independente")
  void define_e_remove_prazos() {
    var application =
        ApplicationFactory.anApplicationWithPolicies().buildSavedIn(applications);

    var output =
        sut.execute(
            new UpdateApplicationUseCase.Input(
                application.id().value(),
                Optional.empty(),
                Patch.clear(),
                Patch.set(365),
                Patch.absent()));

    assertThat(output.quietPeriodDays()).isEmpty();
    assertThat(output.retentionDays()).contains(365);
    assertThat(output.openTextRetentionDays()).contains(30);
  }

  @Test
  @DisplayName("A retenção geral é aplicada antes da de texto livre: as duas sobem juntas")
  void aplica_a_retencao_geral_primeiro() {
    var application =
        ApplicationFactory.anApplication().withRetention(30).buildSavedIn(applications);

    var output =
        sut.execute(
            new UpdateApplicationUseCase.Input(
                application.id().value(),
                Optional.empty(),
                Patch.absent(),
                Patch.set(365),
                Patch.set(90)));

    assertThat(output.retentionDays()).contains(365);
    assertThat(output.openTextRetentionDays()).contains(90);
  }

  @Test
  @DisplayName("Texto livre acima do prazo geral é recusado pelo domínio e nada é gravado")
  void recusa_texto_livre_acima_do_geral() {
    var application =
        ApplicationFactory.anApplication().withRetention(30).buildSavedIn(applications);

    assertThatThrownBy(
            () ->
                sut.execute(
                    new UpdateApplicationUseCase.Input(
                        application.id().value(),
                        Optional.of("Outro nome"),
                        Patch.absent(),
                        Patch.absent(),
                        Patch.set(90))))
        .isInstanceOf(DomainException.class);

    var stored = applications.findById(application.id()).orElseThrow();
    assertThat(stored.openTextRetentionDays()).isEmpty();
  }

  @Test
  @DisplayName("Identificador inexistente e malformado recusam do mesmo jeito")
  void recusa_inexistente_e_malformado() {
    assertThatThrownBy(() -> sut.execute(untouched(UUID.randomUUID().toString())))
        .isInstanceOf(ApplicationNotFound.class);
    assertThatThrownBy(() -> sut.execute(untouched("nao-e-um-id")))
        .isInstanceOf(ApplicationNotFound.class);
  }
}

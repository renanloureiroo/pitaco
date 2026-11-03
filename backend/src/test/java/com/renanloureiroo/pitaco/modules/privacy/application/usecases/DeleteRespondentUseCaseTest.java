package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.DeletionAuditOutput;
import com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects.ErasureTarget;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPrivacyApplicationGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryDeletionAuditRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentErasureRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeleteRespondentUseCase e ListDeletionAuditsUseCase")
class DeleteRespondentUseCaseTest {

  private InMemoryPrivacyApplicationGateway applications;
  private InMemoryRespondentErasureRepository respondents;
  private InMemoryDeletionAuditRepository audits;
  private DeleteRespondentUseCase delete;
  private ListDeletionAuditsUseCase list;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applications = new InMemoryPrivacyApplicationGateway();
    respondents = new InMemoryRespondentErasureRepository();
    audits = new InMemoryDeletionAuditRepository();
    delete = new DeleteRespondentUseCase(applications, respondents, audits);
    list = new ListDeletionAuditsUseCase(applications, audits);
    applicationId = applications.anApplication();
  }

  private DeleteRespondentUseCase.Input byReference(String reference) {
    return new DeleteRespondentUseCase.Input(
        applicationId.value(), Optional.of(reference), Optional.empty());
  }

  @Test
  @DisplayName("Apaga o respondente e registra quanto saiu, sem guardar quem")
  void apaga_e_registra() {
    var respondentId =
        respondents.withRespondent(
            applicationId, new ErasureTarget(ErasureTarget.Kind.APP_REFERENCE, "u-1"), 3, 7);

    var output = delete.execute(byReference("u-1"));

    assertThat(output.deleted()).isTrue();
    assertThat(output.displaysDeleted()).isEqualTo(3);
    assertThat(output.answersDeleted()).isEqualTo(7);
    assertThat(respondents.erased()).containsExactly(respondentId);

    var audit = audits.findAll().getFirst();
    assertThat(audits.findAll()).hasSize(1);
    assertThat(audit.getApplicationId()).isEqualTo(applicationId);
    assertThat(audit.getDisplaysDeleted()).isEqualTo(3);
    assertThat(audit.getAnswersDeleted()).isEqualTo(7);
    assertThat(audit.performedBy()).isEmpty();
  }

  @Test
  @DisplayName("Respondente sem referência é apagado pelo dispositivo")
  void apaga_pelo_dispositivo() {
    respondents.withRespondent(
        applicationId, new ErasureTarget(ErasureTarget.Kind.DEVICE, "device-1"), 1, 2);

    var output =
        delete.execute(
            new DeleteRespondentUseCase.Input(
                applicationId.value(), Optional.empty(), Optional.of("device-1")));

    assertThat(output.deleted()).isTrue();
    assertThat(output.answersDeleted()).isEqualTo(2);
  }

  @Test
  @DisplayName("Pedido repetido não apaga nada, não cria registro novo e não é erro")
  void pedido_repetido() {
    respondents.withRespondent(
        applicationId, new ErasureTarget(ErasureTarget.Kind.APP_REFERENCE, "u-1"), 1, 1);
    delete.execute(byReference("u-1"));

    var again = delete.execute(byReference("u-1"));

    assertThat(again.deleted()).isFalse();
    assertThat(again.displaysDeleted()).isZero();
    assertThat(again.answersDeleted()).isZero();
    assertThat(audits.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Respondente de outra aplicação não é alcançado")
  void outra_aplicacao() {
    var other = applications.anApplication();
    respondents.withRespondent(
        other, new ErasureTarget(ErasureTarget.Kind.APP_REFERENCE, "u-1"), 1, 1);

    assertThat(delete.execute(byReference("u-1")).deleted()).isFalse();
    assertThat(respondents.erased()).isEmpty();
    assertThat(audits.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Aplicação inexistente ou identificador malformado é o mesmo 404")
  void aplicacao_inexistente() {
    assertThatThrownBy(
            () ->
                delete.execute(
                    new DeleteRespondentUseCase.Input(
                        UUID.randomUUID().toString(), Optional.of("u-1"), Optional.empty())))
        .isInstanceOf(ApplicationNotFound.class);
    assertThatThrownBy(
            () ->
                delete.execute(
                    new DeleteRespondentUseCase.Input("nao-e-uuid", Optional.of("u-1"), Optional.empty())))
        .isInstanceOf(ApplicationNotFound.class);
  }

  @Test
  @DisplayName("Sem identidade, recusa antes de procurar")
  void sem_identidade() {
    assertThatThrownBy(
            () ->
                delete.execute(
                    new DeleteRespondentUseCase.Input(
                        applicationId.value(), Optional.empty(), Optional.empty())))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("respondent.identity_required");
    assertThat(audits.findAll()).isEmpty();
  }

  @Test
  @DisplayName("A listagem traz os registros da aplicação, do mais recente para o mais antigo")
  void lista_os_registros() {
    for (var index = 0; index < 3; index++) {
      respondents.withRespondent(
          applicationId, new ErasureTarget(ErasureTarget.Kind.APP_REFERENCE, "u-" + index), index, index);
      delete.execute(byReference("u-" + index));
    }

    var page = list.execute(new ListDeletionAuditsUseCase.Input(applicationId.value(), 0, 2));

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items())
        .extracting(DeletionAuditOutput::performedAt)
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }
}

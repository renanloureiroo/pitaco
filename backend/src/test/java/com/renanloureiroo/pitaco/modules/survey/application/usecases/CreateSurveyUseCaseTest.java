package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateSurveyUseCase")
class CreateSurveyUseCaseTest {

  private InMemoryApplicationScopeGateway applications;
  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private CreateSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationScopeGateway();
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new CreateSurveyUseCase(applications, surveys, versions);
    applicationId = applications.anActiveApplication();
  }

  private CreateSurveyUseCase.Input input(String applicationId, String name) {
    return new CreateSurveyUseCase.Input(applicationId, name);
  }

  @Test
  @DisplayName("Cria a pesquisa em rascunho e a versão 1 junto, na mesma transação")
  void cria_pesquisa_e_versao_um() {
    var output = useCase.execute(input(applicationId.value(), "NPS pós-checkout"));

    assertThat(output.id()).isNotBlank();
    assertThat(output.applicationId()).isEqualTo(applicationId.value());
    assertThat(output.name()).isEqualTo("NPS pós-checkout");
    assertThat(output.state()).isEqualTo(SurveyState.DRAFT);
    assertThat(output.publishedVersionNumber()).isEmpty();
    assertThat(output.draftVersionNumber()).contains(1);
    assertThat(output.createdAt()).isNotNull();

    var survey = surveys.findAll().getFirst();
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.DRAFT);

    var version = versions.findAll().getFirst();
    assertThat(version.getSurveyId()).isEqualTo(survey.id());
    assertThat(version.getNumber()).isEqualTo(1);
    assertThat(version.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(version.getQuestions()).isEmpty();
    assertThat(version.trigger()).isEmpty();
  }

  @Test
  void aplica_o_strip_do_value_object_no_nome() {
    var output = useCase.execute(input(applicationId.value(), "  NPS pós-checkout  "));

    assertThat(output.name()).isEqualTo("NPS pós-checkout");
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado recusam do mesmo jeito")
  void recusa_aplicacao_que_nao_existe() {
    var desconhecida = applications.anUnknownApplication().value();

    assertThatThrownBy(() -> useCase.execute(input(desconhecida, "NPS")))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(
            erro ->
                assertThat(((ApplicationNotFound) erro).code()).isEqualTo("application.not_found"));

    assertThatThrownBy(() -> useCase.execute(input("nao-e-um-id", "NPS")))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(
            erro ->
                assertThat(((ApplicationNotFound) erro).code()).isEqualTo("application.not_found"));

    assertThat(surveys.isEmpty()).isTrue();
    assertThat(versions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Aplicação inativa não cria pesquisa")
  void recusa_aplicacao_inativa() {
    var inativa = applications.anInactiveApplication();

    assertThatThrownBy(() -> useCase.execute(input(inativa.value(), "NPS")))
        .isInstanceOf(ApplicationIsInactive.class)
        .satisfies(
            erro ->
                assertThat(((ApplicationIsInactive) erro).code())
                    .isEqualTo("application.inactive"));

    assertThat(surveys.isEmpty()).isTrue();
    assertThat(versions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Nome inválido é recusado pelo value object, e nada é gravado")
  void recusa_nome_invalido() {
    assertThatThrownBy(() -> useCase.execute(input(applicationId.value(), "  ")))
        .satisfies(
            erro ->
                assertThat(erro)
                    .isInstanceOf(com.renanloureiroo.pitaco.core.error.DomainException.class));

    assertThat(surveys.isEmpty()).isTrue();
    assertThat(versions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Duas pesquisas com o mesmo nome na mesma aplicação são aceitas")
  void aceita_nome_repetido() {
    useCase.execute(input(applicationId.value(), "NPS"));
    useCase.execute(input(applicationId.value(), "NPS"));

    assertThat(surveys.findAll()).hasSize(2);
    assertThat(versions.findAll()).hasSize(2);
  }
}

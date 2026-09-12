package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCompletedResponsesGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyStateTransitionRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Exposure;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateSurveyUseCase")
class UpdateSurveyUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private InMemorySurveyStateTransitionRepository transitions;
  private InMemoryCompletedResponsesGateway completed;
  private UpdateSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    transitions = new InMemorySurveyStateTransitionRepository();
    completed = new InMemoryCompletedResponsesGateway();
    useCase = new UpdateSurveyUseCase(surveys, versions, transitions, completed);
    applicationId = ApplicationId.generate();
  }

  private UpdateSurveyUseCase.Input rename(String surveyId, String name) {
    return new UpdateSurveyUseCase.Input(
        applicationId.value(),
        surveyId,
        Optional.of(name),
        Optional.empty(),
        Patch.absent(),
        Optional.empty(),
        Optional.empty(),
        Patch.absent());
  }

  private UpdateSurveyUseCase.Input exposure(
      String surveyId, Optional<Integer> priority, Patch<Integer> quota, Optional<Boolean> ignores) {
    return new UpdateSurveyUseCase.Input(
        applicationId.value(),
        surveyId,
        Optional.empty(),
        priority,
        quota,
        ignores,
        Optional.empty(),
        Patch.absent());
  }

  @Test
  @DisplayName("Renomeia o rascunho e grava o novo nome, sem tocar na exposição")
  void renomeia_o_rascunho() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withName("Nome antigo")
            .withPriority(7)
            .buildSavedIn(surveys);

    var output = useCase.execute(rename(survey.id().value(), "Nome novo"));

    assertThat(output.name()).isEqualTo("Nome novo");
    assertThat(output.state()).isEqualTo(SurveyState.DRAFT);
    assertThat(output.priority()).isEqualTo(7);
    assertThat(surveys.findAll().getFirst().getName().value()).isEqualTo("Nome novo");
  }

  @Test
  @DisplayName("Renomear não muda o ciclo de vida nem o estado derivado")
  void renomear_nao_muda_o_estado() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);

    var output = useCase.execute(rename(survey.id().value(), "Outro nome"));

    assertThat(output.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }

  @Test
  @DisplayName("Nome inválido é recusado pelo value object, e o nome anterior permanece")
  void recusa_nome_invalido() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withName("Nome antigo")
            .buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(rename(survey.id().value(), "   ")))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> useCase.execute(rename(survey.id().value(), "a".repeat(121))))
        .isInstanceOf(DomainException.class);

    assertThat(surveys.findAll().getFirst().getName().value()).isEqualTo("Nome antigo");
  }

  @Test
  @DisplayName("Fora do escopo da aplicação é não encontrada")
  void recusa_fora_do_escopo() {
    var deOutra =
        SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(rename(deOutra.id().value(), "Nome novo")))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(() -> useCase.execute(rename("nao-e-um-id", "Nome novo")))
        .isInstanceOf(SurveyNotFound.class);

    assertThat(surveys.findAll().getFirst().getName().value())
        .isEqualTo(SurveyFactory.DEFAULT_NAME);
  }

  @Test
  @DisplayName("Define prioridade, cota e isenção de uma vez, com a pesquisa no ar")
  void define_a_exposicao() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);

    var output =
        useCase.execute(
            exposure(
                survey.id().value(), Optional.of(20), Patch.set(100), Optional.of(true)));

    assertThat(output.priority()).isEqualTo(20);
    assertThat(output.responseQuota()).contains(100);
    assertThat(output.ignoresQuietPeriod()).isTrue();
    assertThat(output.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(surveys.findAll().getFirst().getExposure())
        .isEqualTo(new Exposure(20, Optional.of(100), true));
    assertThat(versions.findAll())
        .describedAs("exposição é da pesquisa: nenhuma versão é aberta")
        .hasSize(1);
  }

  @Test
  @DisplayName("Cota nula remove; campo ausente não mexe")
  void cota_nula_remove_e_ausente_preserva() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withResponseQuota(50)
            .withPriority(3)
            .ignoringQuietPeriod()
            .buildSavedIn(surveys);

    useCase.execute(
        exposure(survey.id().value(), Optional.empty(), Patch.absent(), Optional.empty()));
    assertThat(surveys.findAll().getFirst().getExposure())
        .isEqualTo(new Exposure(3, Optional.of(50), true));

    useCase.execute(
        exposure(survey.id().value(), Optional.empty(), Patch.clear(), Optional.empty()));
    assertThat(surveys.findAll().getFirst().getExposure())
        .isEqualTo(new Exposure(3, Optional.empty(), true));
  }

  @Test
  @DisplayName("Prioridade fora da faixa ou cota abaixo de um são recusadas, e nada muda")
  void recusa_exposicao_invalida() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    exposure(
                        survey.id().value(), Optional.of(101), Patch.absent(), Optional.empty())))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    exposure(survey.id().value(), Optional.empty(), Patch.set(0), Optional.empty())))
        .isInstanceOf(DomainException.class);

    assertThat(surveys.findAll().getFirst().getExposure()).isEqualTo(Exposure.standard());
  }

  @Test
  @DisplayName("Cota reduzida até o total já concluído encerra na hora, com o motivo da cota")
  void cota_reduzida_encerra_na_hora() {
    var survey =
        SurveyFactory.aPublishedSurvey()
            .forApplication(applicationId)
            .withResponseQuota(100)
            .buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);
    completed.withCompleted(survey.id(), 30);

    var output =
        useCase.execute(
            exposure(survey.id().value(), Optional.empty(), Patch.set(30), Optional.empty()));

    assertThat(output.state()).isEqualTo(SurveyState.ENDED);
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.ENDED);
    assertThat(transitions.findAll())
        .singleElement()
        .satisfies(
            transition -> {
              assertThat(transition.getReason()).isEqualTo(TransitionReason.QUOTA_REACHED);
              assertThat(transition.getFrom()).isEqualTo(SurveyState.ACTIVE);
            });
  }

  @Test
  @DisplayName("Cota acima do total concluído só é gravada, e a pesquisa segue no ar")
  void cota_acima_do_total_nao_encerra() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);
    completed.withCompleted(survey.id(), 29);

    var output =
        useCase.execute(
            exposure(survey.id().value(), Optional.empty(), Patch.set(30), Optional.empty()));

    assertThat(output.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(transitions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Rascunho com cota já alcançável não encerra: ainda não foi publicado")
  void rascunho_nao_encerra_por_cota() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    completed.withCompleted(survey.id(), 5);

    var output =
        useCase.execute(
            exposure(survey.id().value(), Optional.empty(), Patch.set(1), Optional.empty()));

    assertThat(output.state()).isEqualTo(SurveyState.DRAFT);
    assertThat(transitions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Editar outro campo não reconfere a cota")
  void outro_campo_nao_reconfere_a_cota() {
    var survey =
        SurveyFactory.aPublishedSurvey()
            .forApplication(applicationId)
            .withResponseQuota(10)
            .buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);
    completed.withCompleted(survey.id(), 10);

    var output = useCase.execute(rename(survey.id().value(), "Outro nome"));

    assertThat(output.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(transitions.findAll()).isEmpty();
  }
}

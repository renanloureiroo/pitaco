package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EndSurveyByQuotaUseCase")
class EndSurveyByQuotaUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private InMemorySurveyStateTransitionRepository transitions;
  private EndSurveyByQuotaUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    transitions = new InMemorySurveyStateTransitionRepository();
    useCase = new EndSurveyByQuotaUseCase(surveys, versions, transitions);
    applicationId = ApplicationId.generate();
  }

  private Survey surveyWith(SurveyFactory factory, TriggerFactory trigger) {
    var survey = factory.forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(trigger)
        .buildPublishedSavedIn(versions);
    return survey;
  }

  private void endByQuota(Survey survey) {
    useCase.execute(new EndSurveyByQuotaUseCase.Input(applicationId, survey.id()));
  }

  @Test
  @DisplayName("Encerra a pesquisa no ar e registra o motivo distinto do manual")
  void encerra_e_registra_o_motivo() {
    var survey = surveyWith(SurveyFactory.aPublishedSurvey(), TriggerFactory.anOpenTrigger());

    endByQuota(survey);

    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.ENDED);
    assertThat(transitions.findAll())
        .singleElement()
        .satisfies(
            transition -> {
              assertThat(transition.getReason()).isEqualTo(TransitionReason.QUOTA_REACHED);
              assertThat(transition.getFrom()).isEqualTo(SurveyState.ACTIVE);
              assertThat(transition.getTo()).isEqualTo(SurveyState.ENDED);
            });
  }

  @Test
  @DisplayName("Pausada também encerra: a sessão aberta antes da pausa pode atingir a cota")
  void pausada_tambem_encerra() {
    var survey = surveyWith(SurveyFactory.aPausedSurvey(), TriggerFactory.anOpenTrigger());

    endByQuota(survey);

    assertThat(transitions.findAll())
        .singleElement()
        .satisfies(transition -> assertThat(transition.getFrom()).isEqualTo(SurveyState.PAUSED));
  }

  @Test
  @DisplayName("Chamada de novo, com a pesquisa já encerrada, não registra nada")
  void repetida_nao_registra_de_novo() {
    var survey = surveyWith(SurveyFactory.aPublishedSurvey(), TriggerFactory.anOpenTrigger());

    endByQuota(survey);
    endByQuota(survey);

    assertThat(transitions.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Janela já fechada encerrou a pesquisa antes da cota, e nada é registrado")
  void janela_fechada_nao_registra() {
    var survey = surveyWith(SurveyFactory.aPublishedSurvey(), TriggerFactory.aClosedTrigger());

    endByQuota(survey);

    assertThat(transitions.isEmpty()).isTrue();
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação ou inexistente é ignorada em silêncio")
  void fora_do_escopo_e_ignorada() {
    var deOutra =
        SurveyFactory.aPublishedSurvey()
            .forApplication(ApplicationId.generate())
            .buildSavedIn(surveys);

    endByQuota(deOutra);
    useCase.execute(new EndSurveyByQuotaUseCase.Input(applicationId, SurveyId.generate()));

    assertThat(transitions.isEmpty()).isTrue();
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }
}

package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.StateTransitionOutput;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Casos de uso de ciclo de vida")
class SurveyLifecycleUseCasesTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private InMemorySurveyStateTransitionRepository transitions;
  private ApplicationId applicationId;

  private PauseSurveyUseCase pause;
  private ResumeSurveyUseCase resume;
  private EndSurveyUseCase end;
  private ListStateTransitionsUseCase history;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    transitions = new InMemorySurveyStateTransitionRepository();
    applicationId = ApplicationId.generate();

    pause = new PauseSurveyUseCase(surveys, versions, transitions);
    resume = new ResumeSurveyUseCase(surveys, versions, transitions);
    end = new EndSurveyUseCase(surveys, versions, transitions);
    history = new ListStateTransitionsUseCase(surveys, versions, transitions);
  }

  private Survey surveyIn(SurveyLifecycle lifecycle, TriggerFactory trigger) {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(lifecycle)
            .buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(trigger)
        .buildPublishedSavedIn(versions);
    return survey;
  }

  private static String codeOf(Throwable error) {
    return ((ApplicationException) error).code();
  }

  @Test
  @DisplayName("Pausar muda o ciclo de vida e grava a transição manual")
  void pausa() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.anOpenTrigger());

    var output =
        pause.execute(new PauseSurveyUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output.state()).isEqualTo(SurveyState.PAUSED);
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.PAUSED);
    assertThat(transitions.findAll())
        .singleElement()
        .satisfies(
            transition -> {
              assertThat(transition.getReason()).isEqualTo(TransitionReason.MANUAL_PAUSE);
              assertThat(transition.getFrom()).isEqualTo(SurveyState.ACTIVE);
              assertThat(transition.getTo()).isEqualTo(SurveyState.PAUSED);
              assertThat(transition.getOccurredAt()).isNotNull();
              assertThat(transition.actor()).isEmpty();
            });
  }

  @Test
  @DisplayName("Retomar devolve ao ar, e a janela decide entre ativa e agendada")
  void retoma() {
    var ativa = surveyIn(SurveyLifecycle.PAUSED, TriggerFactory.anOpenTrigger());
    var agendada = surveyIn(SurveyLifecycle.PAUSED, TriggerFactory.aScheduledTrigger());

    var comJanelaAberta =
        resume.execute(new ResumeSurveyUseCase.Input(applicationId.value(), ativa.id().value()));
    var comJanelaFutura =
        resume.execute(new ResumeSurveyUseCase.Input(applicationId.value(), agendada.id().value()));

    assertThat(comJanelaAberta.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(comJanelaFutura.state()).isEqualTo(SurveyState.SCHEDULED);
    assertThat(transitions.findAll())
        .extracting(SurveyStateTransition::getReason)
        .containsOnly(TransitionReason.MANUAL_RESUME);
  }

  @Test
  @DisplayName("Encerrar é definitivo e grava a transição manual")
  void encerra() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.anOpenTrigger());

    var output =
        end.execute(new EndSurveyUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output.state()).isEqualTo(SurveyState.ENDED);
    assertThat(transitions.findAll().getFirst().getReason()).isEqualTo(TransitionReason.MANUAL_END);
  }

  @Test
  @DisplayName("Cada recusa vem com o seu próprio code")
  void recusas() {
    var rascunho = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var encerrada = surveyIn(SurveyLifecycle.ENDED, TriggerFactory.anOpenTrigger());
    var pausada = surveyIn(SurveyLifecycle.PAUSED, TriggerFactory.anOpenTrigger());
    var publicada = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.anOpenTrigger());

    assertThatThrownBy(
            () ->
                pause.execute(
                    new PauseSurveyUseCase.Input(applicationId.value(), rascunho.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.not_published"));
    assertThatThrownBy(
            () ->
                end.execute(
                    new EndSurveyUseCase.Input(applicationId.value(), rascunho.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.not_published"));
    assertThatThrownBy(
            () ->
                pause.execute(
                    new PauseSurveyUseCase.Input(applicationId.value(), encerrada.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.transition_not_allowed"));
    assertThatThrownBy(
            () ->
                resume.execute(
                    new ResumeSurveyUseCase.Input(applicationId.value(), encerrada.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.transition_not_allowed"));
    assertThatThrownBy(
            () ->
                end.execute(
                    new EndSurveyUseCase.Input(applicationId.value(), encerrada.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.transition_not_allowed"));
    assertThatThrownBy(
            () ->
                pause.execute(
                    new PauseSurveyUseCase.Input(applicationId.value(), pausada.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.transition_not_allowed"));
    assertThatThrownBy(
            () ->
                resume.execute(
                    new ResumeSurveyUseCase.Input(applicationId.value(), publicada.id().value())))
        .satisfies(erro -> assertThat(codeOf(erro)).isEqualTo("survey.transition_not_allowed"));

    assertThat(transitions.isEmpty()).isTrue();
  }

  @Test
  void recusa_fora_do_escopo() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.anOpenTrigger());
    var input = new PauseSurveyUseCase.Input(ApplicationId.generate().value(), survey.id().value());

    assertThatThrownBy(() -> pause.execute(input)).isInstanceOf(SurveyNotFound.class);
  }

  @Test
  @DisplayName("O histórico soma as comandadas às derivadas da janela, ordenadas por instante")
  void historico_soma_comandadas_e_derivadas() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.aClosedTrigger());
    pause.execute(new PauseSurveyUseCase.Input(applicationId.value(), survey.id().value()));

    var output =
        history.execute(
            new ListStateTransitionsUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output)
        .extracting(StateTransitionOutput::reason)
        .contains(
            TransitionReason.MANUAL_PAUSE,
            TransitionReason.WINDOW_OPENED,
            TransitionReason.WINDOW_CLOSED);
    assertThat(output)
        .isSortedAccordingTo(java.util.Comparator.comparing(StateTransitionOutput::occurredAt));
  }

  @Test
  @DisplayName("As transições de janela trazem o instante do limite, não o da leitura")
  void a_transicao_de_janela_usa_o_instante_do_limite() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.aClosedTrigger());
    var window = versions.findPublished(survey.id()).orElseThrow().trigger().orElseThrow().window();

    var output =
        history.execute(
            new ListStateTransitionsUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output)
        .filteredOn(item -> item.reason() == TransitionReason.WINDOW_OPENED)
        .extracting(StateTransitionOutput::occurredAt)
        .containsExactly(window.start());
    assertThat(output)
        .filteredOn(item -> item.reason() == TransitionReason.WINDOW_CLOSED)
        .extracting(StateTransitionOutput::occurredAt)
        .containsExactly(window.end().orElseThrow());
  }

  @Test
  @DisplayName("Ler o histórico não grava nenhuma linha de janela")
  void ler_o_historico_nao_escreve() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.aClosedTrigger());
    var input = new ListStateTransitionsUseCase.Input(applicationId.value(), survey.id().value());

    history.execute(input);
    history.execute(input);

    assertThat(transitions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Janela ainda não aberta não produz transição derivada nenhuma")
  void janela_futura_nao_deriva_transicao() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.aScheduledTrigger());

    var output =
        history.execute(
            new ListStateTransitionsUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output).isEmpty();
  }

  @Test
  void o_instante_gravado_na_transicao_manual_e_o_do_comando() {
    var antes = Instant.now();
    var survey = surveyIn(SurveyLifecycle.PUBLISHED, TriggerFactory.anOpenTrigger());

    pause.execute(new PauseSurveyUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(transitions.findAll().getFirst().getOccurredAt()).isAfterOrEqualTo(antes);
  }
}

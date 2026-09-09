package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("Survey — pausar, retomar e encerrar")
class SurveyTransitionsTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private static Survey surveyIn(SurveyLifecycle lifecycle) {
    return Survey.restore(
        SurveyId.generate(),
        ApplicationId.generate(),
        SurveyName.of("NPS pós-checkout"),
        lifecycle,
        lifecycle == SurveyLifecycle.DRAFT ? null : 1,
        lifecycle == SurveyLifecycle.DRAFT ? 1 : null,
        NOW);
  }

  @Test
  void pausa_uma_pesquisa_publicada() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    survey.pause();

    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PAUSED);
    assertThat(survey.stateAt(NOW, Optional.empty())).isEqualTo(SurveyState.PAUSED);
  }

  @Test
  @DisplayName("Pausar em rascunho é recusado: não há o que pausar")
  void recusa_pausar_rascunho() {
    var survey = surveyIn(SurveyLifecycle.DRAFT);

    assertThatThrownBy(survey::pause).satisfies(erro -> assertCode(erro, "survey.not_published"));
  }

  @Test
  void recusa_pausar_encerrada() {
    var survey = surveyIn(SurveyLifecycle.ENDED);

    assertThatThrownBy(survey::pause)
        .satisfies(erro -> assertCode(erro, "survey.transition_not_allowed"));
  }

  @Test
  void recusa_pausar_o_que_ja_esta_pausado() {
    var survey = surveyIn(SurveyLifecycle.PAUSED);

    assertThatThrownBy(survey::pause)
        .satisfies(erro -> assertCode(erro, "survey.transition_not_allowed"));
  }

  @Test
  @DisplayName("Retomar devolve a pesquisa ao ar; a janela decide entre agendada e ativa")
  void retoma_uma_pesquisa_pausada() {
    var survey = surveyIn(SurveyLifecycle.PAUSED);

    survey.resume();

    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
    assertThat(survey.stateAt(NOW, Optional.of(TriggerWindow.of(NOW.minusSeconds(60), null))))
        .isEqualTo(SurveyState.ACTIVE);
    assertThat(survey.stateAt(NOW, Optional.of(TriggerWindow.of(NOW.plusSeconds(60), null))))
        .isEqualTo(SurveyState.SCHEDULED);
  }

  @Test
  void recusa_retomar_encerrada() {
    var survey = surveyIn(SurveyLifecycle.ENDED);

    assertThatThrownBy(survey::resume)
        .satisfies(erro -> assertCode(erro, "survey.transition_not_allowed"));
  }

  @Test
  @DisplayName("Retomar o que já está no ar é recusado")
  void recusa_retomar_publicada() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThatThrownBy(survey::resume)
        .satisfies(erro -> assertCode(erro, "survey.transition_not_allowed"));
  }

  @Test
  void recusa_retomar_rascunho() {
    var survey = surveyIn(SurveyLifecycle.DRAFT);

    assertThatThrownBy(survey::resume).satisfies(erro -> assertCode(erro, "survey.not_published"));
  }

  @Test
  void encerra_a_partir_de_publicada_e_de_pausada() {
    var published = surveyIn(SurveyLifecycle.PUBLISHED);
    var paused = surveyIn(SurveyLifecycle.PAUSED);

    published.end();
    paused.end();

    assertThat(published.getLifecycle()).isEqualTo(SurveyLifecycle.ENDED);
    assertThat(paused.getLifecycle()).isEqualTo(SurveyLifecycle.ENDED);
  }

  @Test
  void recusa_encerrar_rascunho() {
    var survey = surveyIn(SurveyLifecycle.DRAFT);

    assertThatThrownBy(survey::end).satisfies(erro -> assertCode(erro, "survey.not_published"));
  }

  @Test
  @DisplayName("Encerramento é definitivo")
  void recusa_encerrar_o_que_ja_foi_encerrado() {
    var survey = surveyIn(SurveyLifecycle.ENDED);

    assertThatThrownBy(survey::end)
        .satisfies(erro -> assertCode(erro, "survey.transition_not_allowed"));
  }

  private static void assertCode(Throwable error, String code) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.BUSINESS_RULE);
    assertThat(domainError.code()).isEqualTo(code);
  }
}

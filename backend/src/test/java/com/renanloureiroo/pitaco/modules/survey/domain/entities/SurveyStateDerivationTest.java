package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("Derivação do estado exposto")
class SurveyStateDerivationTest {

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

  private static Optional<TriggerWindow> window(Instant start, Instant end) {
    return Optional.of(TriggerWindow.of(start, end));
  }

  @ParameterizedTest
  @EnumSource(SurveyLifecycle.class)
  @DisplayName("Rascunho é draft; pausada é paused; encerrada é ended — a janela não conta")
  void as_linhas_que_ignoram_a_janela(SurveyLifecycle lifecycle) {
    var survey = surveyIn(lifecycle);
    var aberta = window(NOW.minusSeconds(60), null);
    var fechada = window(NOW.minusSeconds(120), NOW.minusSeconds(60));
    var futura = window(NOW.plusSeconds(60), null);

    var esperado =
        switch (lifecycle) {
          case DRAFT -> SurveyState.DRAFT;
          case PAUSED -> SurveyState.PAUSED;
          case ENDED -> SurveyState.ENDED;
          case PUBLISHED -> null;
        };

    if (esperado == null) {
      return;
    }

    assertThat(survey.stateAt(NOW, aberta)).isEqualTo(esperado);
    assertThat(survey.stateAt(NOW, fechada)).isEqualTo(esperado);
    assertThat(survey.stateAt(NOW, futura)).isEqualTo(esperado);
    assertThat(survey.stateAt(NOW, Optional.empty())).isEqualTo(esperado);
  }

  @Test
  @DisplayName("Publicada com janela que ainda não abriu é scheduled")
  void publicada_com_janela_futura_e_scheduled() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThat(survey.stateAt(NOW, window(NOW.plusSeconds(1), null)))
        .isEqualTo(SurveyState.SCHEDULED);
  }

  @Test
  @DisplayName("Publicar no instante exato da abertura já é active: o início é inclusivo")
  void o_inicio_da_janela_e_inclusivo() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThat(survey.stateAt(NOW, window(NOW, null))).isEqualTo(SurveyState.ACTIVE);
  }

  @Test
  @DisplayName("Publicada com janela aberta é active")
  void publicada_com_janela_aberta_e_active() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThat(survey.stateAt(NOW, window(NOW.minusSeconds(60), NOW.plusSeconds(60))))
        .isEqualTo(SurveyState.ACTIVE);
  }

  @Test
  @DisplayName("Publicada com janela fechada é ended, sem ninguém ter comandado nada")
  void publicada_com_janela_fechada_e_ended() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThat(survey.stateAt(NOW, window(NOW.minusSeconds(120), NOW)))
        .isEqualTo(SurveyState.ENDED);
    assertThat(survey.stateAt(NOW, window(NOW.minusSeconds(120), NOW.minusSeconds(60))))
        .isEqualTo(SurveyState.ENDED);
  }

  @Test
  @DisplayName("Janela sem fim mantém a pesquisa ativa por tempo indeterminado")
  void janela_sem_fim_nunca_encerra() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    assertThat(survey.stateAt(NOW.plusSeconds(10_000_000), window(NOW, null)))
        .isEqualTo(SurveyState.ACTIVE);
  }

  @Test
  @DisplayName("Pausada com janela já fechada continua pausada")
  void pausada_com_janela_fechada_continua_pausada() {
    var survey = surveyIn(SurveyLifecycle.PAUSED);

    assertThat(survey.stateAt(NOW, window(NOW.minusSeconds(120), NOW.minusSeconds(60))))
        .isEqualTo(SurveyState.PAUSED);
  }

  @Test
  @DisplayName("Derivar o estado não altera o ciclo de vida gravado")
  void derivar_nao_escreve() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    survey.stateAt(NOW, window(NOW.minusSeconds(120), NOW.minusSeconds(60)));

    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }
}

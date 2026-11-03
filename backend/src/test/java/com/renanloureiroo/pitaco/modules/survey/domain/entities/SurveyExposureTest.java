package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Exposure;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Survey — exposição e encerramento por cota")
class SurveyExposureTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private static Survey surveyIn(SurveyLifecycle lifecycle) {
    return Survey.restore(
        SurveyId.generate(),
        ApplicationId.generate(),
        SurveyName.of("NPS pós-checkout"),
        lifecycle,
        lifecycle == SurveyLifecycle.DRAFT ? null : 1,
        lifecycle == SurveyLifecycle.DRAFT ? 1 : null,
        Exposure.standard(),
        NOW);
  }

  @Test
  @DisplayName("Nasce com a exposição padrão")
  void nasce_com_a_exposicao_padrao() {
    var survey = Survey.create(ApplicationId.generate(), SurveyName.of("Nova"));

    assertThat(survey.getExposure()).isEqualTo(Exposure.standard());
  }

  @Test
  @DisplayName("Redefinir a exposição não muda o ciclo de vida")
  void redefinir_exposicao_nao_muda_o_ciclo() {
    var survey = surveyIn(SurveyLifecycle.PUBLISHED);

    survey.redefineExposure(Exposure.standard().withResponseQuota(10).withPriority(5));

    assertThat(survey.getExposure().responseQuota()).contains(10);
    assertThat(survey.getExposure().priority()).isEqualTo(5);
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }

  @ParameterizedTest
  @EnumSource(
      value = SurveyLifecycle.class,
      names = {"PUBLISHED", "PAUSED"})
  @DisplayName("No ar ou pausada, a cota encerra a pesquisa")
  void cota_encerra_no_ar_ou_pausada(SurveyLifecycle lifecycle) {
    var survey = surveyIn(lifecycle);

    assertThat(survey.endByQuota()).isTrue();
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.ENDED);
  }

  @ParameterizedTest
  @EnumSource(
      value = SurveyLifecycle.class,
      names = {"DRAFT", "ENDED"})
  @DisplayName("Rascunho ou já encerrada fica como está, sem erro")
  void cota_e_idempotente(SurveyLifecycle lifecycle) {
    var survey = surveyIn(lifecycle);

    assertThat(survey.endByQuota()).isFalse();
    assertThat(survey.getLifecycle()).isEqualTo(lifecycle);
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Survey")
class SurveyTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applicationId = ApplicationId.generate();
  }

  private Survey aDraft() {
    return Survey.create(applicationId, SurveyName.of("NPS pós-checkout"));
  }

  @Test
  @DisplayName("Nasce em rascunho, com a versão 1 aberta e nenhuma publicada")
  void nasce_em_rascunho() {
    var survey = aDraft();

    assertThat(survey.id()).isNotNull();
    assertThat(survey.getApplicationId()).isEqualTo(applicationId);
    assertThat(survey.getName()).isEqualTo(SurveyName.of("NPS pós-checkout"));
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.DRAFT);
    assertThat(survey.draftVersionNumber()).contains(1);
    assertThat(survey.publishedVersionNumber()).isEmpty();
    assertThat(survey.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("restore devolve exatamente o que veio do banco, sem revalidar como criação")
  void restore_volta_do_banco() {
    var id = SurveyId.generate();

    var survey =
        Survey.restore(
            id, applicationId, SurveyName.of("Antiga"), SurveyLifecycle.PAUSED, 2, null, NOW);

    assertThat(survey.id()).isEqualTo(id);
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PAUSED);
    assertThat(survey.publishedVersionNumber()).contains(2);
    assertThat(survey.draftVersionNumber()).isEmpty();
    assertThat(survey.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void renomeia_a_pesquisa() {
    var survey = aDraft();

    survey.rename(SurveyName.of("NPS pós-entrega"));

    assertThat(survey.getName()).isEqualTo(SurveyName.of("NPS pós-entrega"));
    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.DRAFT);
  }

  @Test
  @DisplayName("Recusa abrir um segundo rascunho: existe no máximo um por pesquisa")
  void recusa_segundo_rascunho() {
    var survey = aDraft();

    assertThatThrownBy(() -> survey.openDraft(2))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.CONFLICT);
              assertThat(domainError.code()).isEqualTo("survey_version.draft_already_open");
            });
  }

  @Test
  void abre_rascunho_de_versao_quando_nao_ha_nenhum() {
    var survey = aDraft();
    survey.markPublished(1);

    survey.openDraft(2);

    assertThat(survey.draftVersionNumber()).contains(2);
    assertThat(survey.publishedVersionNumber()).contains(1);
  }

  @Test
  void descartar_o_rascunho_limpa_o_ponteiro() {
    var survey = aDraft();

    survey.discardDraft();

    assertThat(survey.draftVersionNumber()).isEmpty();
    assertThat(survey.hasDraft()).isFalse();
  }

  @Test
  @DisplayName("Publicar aponta a versão publicada e fecha o rascunho")
  void marca_publicada() {
    var survey = aDraft();

    survey.markPublished(1);

    assertThat(survey.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
    assertThat(survey.publishedVersionNumber()).contains(1);
    assertThat(survey.draftVersionNumber()).isEmpty();
  }

  @Test
  @DisplayName("Em rascunho o estado é draft, qualquer que seja a janela")
  void estado_de_rascunho_ignora_a_janela() {
    var survey = aDraft();

    assertThat(survey.stateAt(NOW, Optional.empty())).isEqualTo(SurveyState.DRAFT);
    assertThat(survey.stateAt(NOW, Optional.of(TriggerWindow.of(NOW.minusSeconds(60), null))))
        .isEqualTo(SurveyState.DRAFT);
  }

  @Test
  @DisplayName("Pausada continua pausada mesmo com a janela já fechada")
  void estado_de_pausada_ignora_a_janela() {
    var survey =
        Survey.restore(
            SurveyId.generate(),
            applicationId,
            SurveyName.of("Pausada"),
            SurveyLifecycle.PAUSED,
            1,
            null,
            NOW);
    var closed = TriggerWindow.of(NOW.minusSeconds(120), NOW.minusSeconds(60));

    assertThat(survey.stateAt(NOW, Optional.of(closed))).isEqualTo(SurveyState.PAUSED);
  }

  @Test
  @DisplayName("Encerrada continua encerrada mesmo com a janela aberta")
  void estado_de_encerrada_ignora_a_janela() {
    var survey =
        Survey.restore(
            SurveyId.generate(),
            applicationId,
            SurveyName.of("Encerrada"),
            SurveyLifecycle.ENDED,
            1,
            null,
            NOW);
    var open = TriggerWindow.of(NOW.minusSeconds(60), null);

    assertThat(survey.stateAt(NOW, Optional.of(open))).isEqualTo(SurveyState.ENDED);
  }
}

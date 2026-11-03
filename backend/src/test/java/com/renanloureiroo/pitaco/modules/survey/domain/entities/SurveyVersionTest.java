package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("SurveyVersion")
class SurveyVersionTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  @Test
  @DisplayName("A versão 1 nasce em rascunho e completamente vazia")
  void nasce_vazia_em_rascunho() {
    var surveyId = SurveyId.generate();

    var version = SurveyVersion.create(surveyId, 1);

    assertThat(version.id()).isNotNull();
    assertThat(version.getSurveyId()).isEqualTo(surveyId);
    assertThat(version.getNumber()).isEqualTo(1);
    assertThat(version.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(version.getQuestions()).isEmpty();
    assertThat(version.trigger()).isEmpty();
    assertThat(version.getRules()).isEmpty();
    assertThat(version.changeKind()).isEmpty();
    assertThat(version.changeSummary()).isEmpty();
    assertThat(version.publishedAt()).isEmpty();
    assertThat(version.isEditable()).isTrue();
  }

  @Test
  @DisplayName("restore devolve o que veio do banco, publicada e com o grupo gravado")
  void restore_preserva_o_que_veio_do_banco() {
    var id = SurveyVersionId.generate();
    var surveyId = SurveyId.generate();
    var trigger =
        new Trigger(
            EventName.of("checkout.completed"), TriggerWindow.of(NOW, null), SamplingRate.of(0.25));

    var version =
        SurveyVersion.restore(
            id,
            surveyId,
            2,
            SurveyVersionStatus.PUBLISHED,
            List.of(),
            Optional.of(trigger),
            List.of(),
            Optional.of(ChangeKind.COSMETIC),
            Optional.of("Correção de acentuação"),
            3,
            Optional.of(NOW));

    assertThat(version.id()).isEqualTo(id);
    assertThat(version.getNumber()).isEqualTo(2);
    assertThat(version.getStatus()).isEqualTo(SurveyVersionStatus.PUBLISHED);
    assertThat(version.trigger()).contains(trigger);
    assertThat(version.changeKind()).contains(ChangeKind.COSMETIC);
    assertThat(version.changeSummary()).contains("Correção de acentuação");
    assertThat(version.getComparabilityGroup()).isEqualTo(3);
    assertThat(version.publishedAt()).contains(NOW);
    assertThat(version.isEditable()).isFalse();
  }
}

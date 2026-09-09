package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("Grupo de comparabilidade")
class ComparabilityGroupTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private static final SurveyId SURVEY_ID = SurveyId.generate();

  private static SurveyVersion publishedFirstVersion() {
    var version = SurveyVersion.create(SURVEY_ID, 1);
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("O que achou?"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));
    version.defineTrigger(
        new Trigger(
            EventName.of("checkout.completed"),
            TriggerWindow.of(NOW, null),
            SamplingRate.of(0.25)));
    return version.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static SurveyVersion publishNext(
      SurveyVersion previous, int number, ChangeKind kind, boolean structural) {
    var draft = previous.copyAsDraft(number);
    if (structural) {
      draft.addQuestion(
          new Question.Draft(
              QuestionStatement.of("Pergunta nova " + number),
              QuestionType.FREE_TEXT,
              true,
              List.of(),
              Optional.empty()));
    } else {
      var target = draft.getQuestions().getFirst();
      draft.updateQuestion(
          target.id(),
          new Question.Draft(
              QuestionStatement.of("Enunciado revisado " + number),
              target.getType(),
              target.isRequired(),
              target.getOptions(),
              target.range()));
    }
    return draft.publish(NOW, Optional.of(previous), Optional.of(kind), Optional.of("resumo"));
  }

  @Test
  @DisplayName("A versão 1 abre o grupo 1")
  void a_versao_um_abre_o_grupo_um() {
    assertThat(publishedFirstVersion().getComparabilityGroup()).isEqualTo(1);
  }

  @Test
  @DisplayName("Versão cosmética herda o grupo da anterior")
  void cosmetica_herda_o_grupo() {
    var v1 = publishedFirstVersion();

    var v2 = publishNext(v1, 2, ChangeKind.COSMETIC, false);

    assertThat(v2.getComparabilityGroup()).isEqualTo(v1.getComparabilityGroup());
    assertThat(v2.changeKind()).contains(ChangeKind.COSMETIC);
    assertThat(v2.changeSummary()).contains("resumo");
  }

  @Test
  @DisplayName("Versão semântica abre o grupo seguinte")
  void semantica_incrementa_o_grupo() {
    var v1 = publishedFirstVersion();

    var v2 = publishNext(v1, 2, ChangeKind.SEMANTIC, true);

    assertThat(v2.getComparabilityGroup()).isEqualTo(v1.getComparabilityGroup() + 1);
  }

  @Test
  @DisplayName("v1 → v2 cosmética → v3 semântica → v4 cosmética produz {v1,v2} e {v3,v4}")
  void a_transitividade_cai_da_regra_de_incremento() {
    var v1 = publishedFirstVersion();
    var v2 = publishNext(v1, 2, ChangeKind.COSMETIC, false);
    var v3 = publishNext(v2, 3, ChangeKind.SEMANTIC, true);
    var v4 = publishNext(v3, 4, ChangeKind.COSMETIC, false);

    assertThat(v1.getComparabilityGroup()).isEqualTo(v2.getComparabilityGroup());
    assertThat(v3.getComparabilityGroup()).isEqualTo(v4.getComparabilityGroup());
    assertThat(v3.getComparabilityGroup()).isNotEqualTo(v1.getComparabilityGroup());
    assertThat(List.of(v1, v2, v3, v4))
        .extracting(SurveyVersion::getComparabilityGroup)
        .containsExactly(1, 1, 2, 2);
  }

  @Test
  @DisplayName("Semântica é sempre aceita, mesmo sem nenhuma diferença estrutural")
  void semantica_e_sempre_aceita() {
    var v1 = publishedFirstVersion();

    var v2 = publishNext(v1, 2, ChangeKind.SEMANTIC, false);

    assertThat(v2.getComparabilityGroup()).isEqualTo(2);
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyVersion — duplicação para outra pesquisa")
class SurveyVersionDuplicateTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  private SurveyVersion source;
  private Question choice;
  private Question followUp;

  @BeforeEach
  void setUp() {
    source = SurveyVersion.create(SurveyId.generate(), 3);
    choice =
        source.addQuestion(
            new Question.Draft(
                QuestionStatement.of("Recomendaria?"),
                QuestionType.SINGLE_CHOICE,
                true,
                List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
                Optional.empty()));
    followUp =
        source.addQuestion(
            new Question.Draft(
                QuestionStatement.of("Por quê?"),
                QuestionType.FREE_TEXT,
                false,
                List.of(),
                Optional.empty(),
                ScaleLabels.none(),
                Optional.of(
                    DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("no")))));
  }

  private static Trigger trigger(Instant start, Instant end) {
    return new Trigger(
        EventName.of("checkout.completed"), TriggerWindow.of(start, end), SamplingRate.of(0.5));
  }

  @Test
  @DisplayName("Nasce rascunho número 1, no primeiro grupo, apontando para a pesquisa nova")
  void nasce_rascunho_um() {
    var target = SurveyId.generate();

    var copy = source.duplicateFor(target, NOW);

    assertThat(copy.id()).isNotEqualTo(source.id());
    assertThat(copy.getSurveyId()).isEqualTo(target);
    assertThat(copy.getNumber()).isEqualTo(1);
    assertThat(copy.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(copy.getComparabilityGroup()).isEqualTo(SurveyVersion.FIRST_COMPARABILITY_GROUP);
    assertThat(copy.publishedAt()).isEmpty();
    assertThat(copy.changeKind()).isEmpty();
  }

  @Test
  @DisplayName("Perguntas copiadas com chave e identidade novas, e a condição reapontada")
  void perguntas_com_chaves_novas() {
    var copy = source.duplicateFor(SurveyId.generate(), NOW);

    var copiedChoice = copy.getQuestions().get(0);
    var copiedFollowUp = copy.getQuestions().get(1);

    assertThat(copiedChoice.getKey()).isNotEqualTo(choice.getKey());
    assertThat(copiedChoice.id()).isNotEqualTo(choice.id());
    assertThat(copiedChoice.getStatement()).isEqualTo(choice.getStatement());
    assertThat(copiedChoice.getOptions()).isEqualTo(choice.getOptions());
    assertThat(copiedFollowUp.getKey()).isNotEqualTo(followUp.getKey());
    assertThat(copiedFollowUp.isRequired()).isFalse();
    assertThat(copiedFollowUp.condition())
        .hasValueSatisfying(
            condition -> {
              assertThat(condition.sourceKey()).isEqualTo(copiedChoice.getKey());
              assertThat(condition.values()).containsExactly("no");
            });
  }

  @Test
  @DisplayName("Janela que ainda vale é copiada como está")
  void janela_aberta_e_copiada() {
    var window = trigger(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"));
    source.defineTrigger(window);

    var copy = source.duplicateFor(SurveyId.generate(), NOW);

    assertThat(copy.trigger()).contains(window);
  }

  @Test
  @DisplayName("Janela já encerrada vira aberta a partir de agora e sem fim; evento e proporção ficam")
  void janela_encerrada_e_reaberta() {
    source.defineTrigger(
        trigger(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z")));

    var copy = source.duplicateFor(SurveyId.generate(), NOW);

    var copied = copy.trigger().orElseThrow();
    assertThat(copied.event()).isEqualTo(EventName.of("checkout.completed"));
    assertThat(copied.rate()).isEqualTo(SamplingRate.of(0.5));
    assertThat(copied.window().start()).isEqualTo(NOW);
    assertThat(copied.window().end()).isEmpty();
  }

  @Test
  @DisplayName("Regras de segmentação copiadas com identidade nova")
  void regras_copiadas() {
    source.defineTrigger(trigger(Instant.parse("2026-09-01T00:00:00Z"), null));
    var rule =
        new SegmentationRule(
            SegmentationRuleId.generate(), "plano", RuleOperation.EQUALS, Optional.of("pro"));
    source.addRule(rule);

    var copy = source.duplicateFor(SurveyId.generate(), NOW);

    assertThat(copy.getRules())
        .singleElement()
        .satisfies(
            copied -> {
              assertThat(copied.id()).isNotEqualTo(rule.id());
              assertThat(copied.attribute()).isEqualTo("plano");
              assertThat(copied.value()).contains("pro");
            });
  }

  @Test
  void sem_disparo_a_copia_tambem_nao_tem() {
    assertThat(source.duplicateFor(SurveyId.generate(), NOW).trigger()).isEmpty();
  }
}

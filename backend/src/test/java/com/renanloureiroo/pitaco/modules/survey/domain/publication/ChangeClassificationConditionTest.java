package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification.Difference;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification.DifferenceKind;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Classificação da mudança — condição, faixa e rótulo")
class ChangeClassificationConditionTest {

  private SurveyVersion previous;
  private Question choice;
  private Question rating;
  private Question text;

  private static Question.Draft draft(
      String statement,
      QuestionType type,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      ScaleLabels labels,
      Optional<DisplayCondition> condition) {
    return new Question.Draft(
        QuestionStatement.of(statement), type, true, options, range, labels, condition);
  }

  @BeforeEach
  void setUp() {
    previous = SurveyVersion.create(SurveyId.generate(), 1);
    choice =
        previous.addQuestion(
            draft(
                "Recomendaria?",
                QuestionType.SINGLE_CHOICE,
                List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
                Optional.empty(),
                ScaleLabels.none(),
                Optional.empty()));
    rating =
        previous.addQuestion(
            draft(
                "Avalie",
                QuestionType.RATING,
                List.of(),
                Optional.of(new ScaleRange(1, 5)),
                ScaleLabels.of("Péssimo", "Ótimo"),
                Optional.empty()));
    text =
        previous.addQuestion(
            draft(
                "Por quê?",
                QuestionType.FREE_TEXT,
                List.of(),
                Optional.empty(),
                ScaleLabels.none(),
                Optional.of(
                    DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("no")))));
    previous.defineTrigger(TriggerFactory.anOpenTrigger().build());
  }

  private SurveyVersion nextDraft() {
    return previous.copyAsDraft(2);
  }

  private static Question byKey(SurveyVersion version, Question original) {
    return version.getQuestions().stream()
        .filter(question -> question.getKey().equals(original.getKey()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("Trocar a condição é mudança de sentido, na pergunta condicionada")
  void condicao_trocada() {
    var next = nextDraft();
    next.updateQuestion(
        byKey(next, text).id(),
        draft(
            "Por quê?",
            QuestionType.FREE_TEXT,
            List.of(),
            Optional.empty(),
            ScaleLabels.none(),
            Optional.of(
                DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("yes")))));

    assertThat(ChangeClassification.between(previous.getQuestions(), next.getQuestions()))
        .containsExactly(new Difference(text.getKey(), DifferenceKind.CONDITION_CHANGED));
  }

  @Test
  void remover_a_condicao_tambem_muda_o_sentido() {
    var next = nextDraft();
    next.updateQuestion(
        byKey(next, text).id(),
        draft(
            "Por quê?",
            QuestionType.FREE_TEXT,
            List.of(),
            Optional.empty(),
            ScaleLabels.none(),
            Optional.empty()));

    assertThat(ChangeClassification.between(previous.getQuestions(), next.getQuestions()))
        .extracting(Difference::kind)
        .containsExactly(DifferenceKind.CONDITION_CHANGED);
  }

  @Test
  @DisplayName("Mudar a faixa é mudança de sentido; mudar só o rótulo não é")
  void faixa_e_rotulo() {
    var faixa = nextDraft();
    faixa.updateQuestion(
        byKey(faixa, rating).id(),
        draft(
            "Avalie",
            QuestionType.RATING,
            List.of(),
            Optional.of(new ScaleRange(1, 7)),
            ScaleLabels.of("Péssimo", "Ótimo"),
            Optional.empty()));
    assertThat(ChangeClassification.between(previous.getQuestions(), faixa.getQuestions()))
        .containsExactly(new Difference(rating.getKey(), DifferenceKind.RANGE_CHANGED));

    var rotulo = nextDraft();
    rotulo.updateQuestion(
        byKey(rotulo, rating).id(),
        draft(
            "Avalie",
            QuestionType.RATING,
            List.of(),
            Optional.of(new ScaleRange(1, 5)),
            ScaleLabels.of("Muito ruim", "Muito bom"),
            Optional.empty()));
    assertThat(ChangeClassification.between(previous.getQuestions(), rotulo.getQuestions()))
        .isEmpty();
  }

  @Test
  @DisplayName("Declarar como cosmética uma condição trocada é recusado na publicação")
  void cosmetica_com_condicao_trocada_e_recusada() {
    var next = nextDraft();
    next.updateQuestion(
        byKey(next, text).id(),
        draft(
            "Por quê?",
            QuestionType.FREE_TEXT,
            List.of(),
            Optional.empty(),
            ScaleLabels.none(),
            Optional.empty()));

    assertThatThrownBy(
            () ->
                next.publish(
                    Instant.now(),
                    Optional.of(previous),
                    Optional.of(ChangeKind.COSMETIC),
                    Optional.empty()))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> assertThat(error.code()).isEqualTo("survey_version.cosmetic_refused"));
  }
}

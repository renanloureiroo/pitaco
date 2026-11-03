package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionRejected;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyVersion — condição de exibição")
class SurveyVersionConditionsTest {

  private SurveyVersion version;
  private Question choice;
  private Question nps;
  private Question text;

  private static Question.Draft draft(
      String statement,
      QuestionType type,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      Optional<DisplayCondition> condition) {
    return new Question.Draft(
        QuestionStatement.of(statement),
        type,
        true,
        options,
        range,
        ScaleLabels.none(),
        condition);
  }

  private static List<QuestionOption> options(String... values) {
    var built = new java.util.ArrayList<QuestionOption>();
    for (var index = 0; index < values.length; index++) {
      built.add(new QuestionOption("Rótulo " + values[index], values[index], index + 1));
    }
    return List.copyOf(built);
  }

  private static Question.Draft conditioned(DisplayCondition condition) {
    return draft(
        "Por quê?", QuestionType.FREE_TEXT, List.of(), Optional.empty(), Optional.of(condition));
  }

  private static void assertRejected(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ConditionRejected.class,
            rejected -> {
              assertThat(rejected.code()).isEqualTo(code);
              assertThat(rejected.type()).isEqualTo(ErrorType.BUSINESS_RULE);
            });
  }

  @BeforeEach
  void setUp() {
    version = SurveyVersion.create(SurveyId.generate(), 1);
    choice =
        version.addQuestion(
            draft(
                "Recomendaria?",
                QuestionType.SINGLE_CHOICE,
                options("yes", "no"),
                Optional.empty(),
                Optional.empty()));
    nps =
        version.addQuestion(
            draft(
                "De 0 a 10?",
                QuestionType.NPS,
                List.of(),
                Optional.of(ScaleRange.NPS),
                Optional.empty()));
    text =
        version.addQuestion(
            draft("Comente", QuestionType.FREE_TEXT, List.of(), Optional.empty(), Optional.empty()));
  }

  @Test
  @DisplayName("Aceita condição sobre escolha anterior e faixa sobre NPS anterior")
  void aceita_condicao_valida() {
    var sobreEscolha =
        version.addQuestion(
            conditioned(
                DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("no"))));
    var sobreNps = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    assertThat(sobreEscolha.condition()).map(DisplayCondition::sourceKey).contains(choice.getKey());
    assertThat(sobreNps.condition()).map(DisplayCondition::operator).contains(ConditionOperator.BETWEEN);
    assertThat(version.getQuestions()).hasSize(5);
  }

  @Test
  @DisplayName("Os extremos da escala valem como valor da condição")
  void extremos_da_escala_valem() {
    version.addQuestion(
        conditioned(DisplayCondition.of(nps.getKey(), ConditionOperator.IN, List.of("0", "10"))));
    version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 10)));

    assertThat(version.getQuestions()).hasSize(5);
  }

  @Test
  void origem_inexistente_e_recusada_sem_mudar_a_versao() {
    assertRejected(
        () ->
            version.addQuestion(
                conditioned(
                    DisplayCondition.of(
                        QuestionKey.generate(), ConditionOperator.EQUALS, List.of("yes")))),
        ConditionRejected.SOURCE_INVALID);

    assertThat(version.getQuestions()).hasSize(3);
  }

  @Test
  @DisplayName("A origem precisa vir antes: condicionar a primeira pergunta à segunda é recusado")
  void origem_posterior_e_recusada() {
    var paraFrente =
        draft(
            "Recomendaria?",
            QuestionType.SINGLE_CHOICE,
            options("yes", "no"),
            Optional.empty(),
            Optional.of(DisplayCondition.between(nps.getKey(), 0, 6)));

    assertRejected(
        () -> version.updateQuestion(choice.id(), paraFrente), ConditionRejected.SOURCE_INVALID);

    assertThat(version.question(choice.id()).orElseThrow().condition()).isEmpty();
  }

  @Test
  @DisplayName("Uma pergunta não pode ser a própria origem")
  void propria_origem_e_recusada() {
    var sobreSi =
        draft(
            "De 0 a 10?",
            QuestionType.NPS,
            List.of(),
            Optional.of(ScaleRange.NPS),
            Optional.of(DisplayCondition.between(nps.getKey(), 0, 6)));

    assertRejected(() -> version.updateQuestion(nps.id(), sobreSi), ConditionRejected.SOURCE_INVALID);
  }

  @Test
  void texto_livre_nao_e_origem() {
    assertRejected(
        () ->
            version.addQuestion(
                conditioned(
                    DisplayCondition.of(text.getKey(), ConditionOperator.EQUALS, List.of("x")))),
        ConditionRejected.SOURCE_INVALID);
  }

  @Test
  void faixa_sobre_escolha_e_recusada() {
    assertRejected(
        () -> version.addQuestion(conditioned(DisplayCondition.between(choice.getKey(), 0, 1))),
        ConditionRejected.OPERATOR_INVALID);
  }

  @Test
  void opcao_que_nao_existe_na_origem_e_recusada() {
    assertRejected(
        () ->
            version.addQuestion(
                conditioned(
                    DisplayCondition.of(choice.getKey(), ConditionOperator.IN, List.of("yes", "maybe")))),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("Na escala, valor fora da faixa ou que não é número é recusado")
  void valor_fora_da_escala_e_recusado() {
    assertRejected(
        () ->
            version.addQuestion(
                conditioned(
                    DisplayCondition.of(nps.getKey(), ConditionOperator.EQUALS, List.of("11")))),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () ->
            version.addQuestion(
                conditioned(
                    DisplayCondition.of(nps.getKey(), ConditionOperator.EQUALS, List.of("dez")))),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () -> version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 11))),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () -> version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), -1, 6))),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("Remover a origem é recusado apontando a dependente; a versão fica como estava")
  void remover_origem_e_recusado() {
    var dependente = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    assertThatThrownBy(() -> version.removeQuestion(nps.id()))
        .isInstanceOfSatisfying(
            ConditionRejected.class,
            rejected -> {
              assertThat(rejected.code()).isEqualTo(ConditionRejected.SOURCE_IN_USE);
              assertThat(rejected.dependent()).contains(dependente.getKey());
              assertThat(rejected.extensions())
                  .containsEntry("questionKey", dependente.getKey().value());
            });

    assertThat(version.getQuestions()).hasSize(4);
  }

  @Test
  void remover_a_dependente_e_permitido() {
    var dependente = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    version.removeQuestion(dependente.id());

    assertThat(version.getQuestions()).hasSize(3);
  }

  @Test
  @DisplayName("Mudar a origem de um jeito que invalida a condição é recusado")
  void mudanca_que_quebra_a_dependente_e_recusada() {
    version.addQuestion(
        conditioned(DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("no"))));
    var semOpcaoNo =
        draft(
            "Recomendaria?",
            QuestionType.SINGLE_CHOICE,
            options("yes", "maybe"),
            Optional.empty(),
            Optional.empty());

    assertRejected(
        () -> version.updateQuestion(choice.id(), semOpcaoNo), ConditionRejected.SOURCE_IN_USE);

    assertThat(version.question(choice.id()).orElseThrow().getOptions())
        .extracting(QuestionOption::value)
        .containsExactly("yes", "no");
  }

  @Test
  void mudanca_que_preserva_a_condicao_e_aceita() {
    version.addQuestion(
        conditioned(DisplayCondition.of(choice.getKey(), ConditionOperator.EQUALS, List.of("no"))));
    var reescrita =
        draft(
            "Você recomendaria?",
            QuestionType.SINGLE_CHOICE,
            options("yes", "no", "maybe"),
            Optional.empty(),
            Optional.empty());

    version.updateQuestion(choice.id(), reescrita);

    assertThat(version.question(choice.id()).orElseThrow().getStatement().value())
        .isEqualTo("Você recomendaria?");
  }

  @Test
  @DisplayName("Reordenar pondo a condicionada antes da origem é recusado; a ordem fica como estava")
  void reordenar_antes_da_origem_e_recusado() {
    var dependente = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    assertRejected(
        () -> version.reorder(List.of(dependente.id(), choice.id(), nps.id(), text.id())),
        ConditionRejected.ORDER_INVALID);

    assertThat(version.getQuestions())
        .extracting(Question::id)
        .containsExactly(choice.id(), nps.id(), text.id(), dependente.id());
  }

  @Test
  void reordenar_mantendo_a_origem_antes_e_permitido() {
    var dependente = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    version.reorder(List.of(nps.id(), dependente.id(), choice.id(), text.id()));

    assertThat(version.getQuestions())
        .extracting(Question::getPosition)
        .containsExactly(1, 2, 3, 4);
    assertThat(version.question(dependente.id()).orElseThrow().getPosition()).isEqualTo(2);
  }

  @Test
  void reescrever_sem_condicao_remove_a_condicao() {
    var dependente = version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    version.updateQuestion(
        dependente.id(),
        draft("Por quê?", QuestionType.FREE_TEXT, List.of(), Optional.empty(), Optional.empty()));

    assertThat(version.question(dependente.id()).orElseThrow().condition()).isEmpty();
  }

  @Test
  @DisplayName("A cópia para a versão seguinte mantém a condição, que aponta pela chave estável")
  void copia_mantem_a_condicao() {
    version.addQuestion(conditioned(DisplayCondition.between(nps.getKey(), 0, 6)));

    var next = version.copyAsDraft(2);

    assertThat(next.getQuestions().get(3).condition())
        .map(DisplayCondition::sourceKey)
        .contains(nps.getKey());
  }

  @Test
  @DisplayName("Rótulos da escala só em pergunta com faixa")
  void rotulos_so_com_faixa() {
    var comRotulos =
        version.addQuestion(
            new Question.Draft(
                QuestionStatement.of("Avalie"),
                QuestionType.RATING,
                true,
                List.of(),
                Optional.of(new ScaleRange(1, 5)),
                ScaleLabels.of("Péssimo", "Ótimo"),
                Optional.empty()));
    assertThat(comRotulos.getLabels().min()).contains("Péssimo");

    assertThatThrownBy(
            () ->
                version.addQuestion(
                    new Question.Draft(
                        QuestionStatement.of("Comente"),
                        QuestionType.FREE_TEXT,
                        true,
                        List.of(),
                        Optional.empty(),
                        ScaleLabels.of("a", null),
                        Optional.empty())))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> {
              assertThat(error.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(error.code()).isEqualTo("question.scale_labels_not_allowed");
            });
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DisplayCondition")
class DisplayConditionTest {

  private static final QuestionKey SOURCE = QuestionKey.generate();

  private static void assertRejected(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ConditionRejected.class,
            rejected -> {
              assertThat(rejected.code()).isEqualTo(code);
              assertThat(rejected.type()).isEqualTo(ErrorType.BUSINESS_RULE);
            });
  }

  private static List<String> values(int amount) {
    return IntStream.rangeClosed(1, amount).mapToObj(index -> "v" + index).toList();
  }

  @Test
  @DisplayName("Igual e diferente comparam com exatamente um valor, já sem espaço nas pontas")
  void igual_e_diferente_pedem_um_valor() {
    assertThat(DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, List.of("sim")).values())
        .containsExactly("sim");
    assertThat(DisplayCondition.of(SOURCE, ConditionOperator.NOT_EQUALS, List.of(" sim ")).values())
        .containsExactly("sim");

    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, List.of()),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.NOT_EQUALS, List.of("a", "b")),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("Dentro de um conjunto pede de 1 a 50 valores")
  void conjunto_pede_de_um_a_cinquenta() {
    assertThat(DisplayCondition.of(SOURCE, ConditionOperator.IN, values(1)).values()).hasSize(1);
    assertThat(DisplayCondition.of(SOURCE, ConditionOperator.IN, values(50)).values()).hasSize(50);

    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.IN, List.of()),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.IN, values(51)),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("Faixa pede mínimo e máximo, aceita os dois iguais e recusa mínimo acima do máximo")
  void faixa_pede_minimo_e_maximo() {
    var faixa = DisplayCondition.between(SOURCE, 0, 6);
    assertThat(faixa.min()).contains(0);
    assertThat(faixa.max()).contains(6);
    assertThat(DisplayCondition.between(SOURCE, 6, 6).min()).contains(6);

    assertRejected(() -> DisplayCondition.between(SOURCE, 7, 6), ConditionRejected.VALUE_INVALID);
    assertRejected(
        () ->
            new DisplayCondition(
                SOURCE, ConditionOperator.BETWEEN, List.of(), Optional.of(0), Optional.empty()),
        ConditionRejected.VALUE_INVALID);
    assertRejected(
        () ->
            new DisplayCondition(
                SOURCE, ConditionOperator.BETWEEN, List.of("3"), Optional.of(0), Optional.of(6)),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("Faixa só acompanha o operador de faixa")
  void minimo_e_maximo_so_na_faixa() {
    assertRejected(
        () ->
            new DisplayCondition(
                SOURCE, ConditionOperator.EQUALS, List.of("3"), Optional.of(0), Optional.of(6)),
        ConditionRejected.VALUE_INVALID);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void valor_ausente_e_recusado(String value) {
    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, Arrays.asList(value)),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  void valor_repetido_e_recusado() {
    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.IN, List.of("a", " a")),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  @DisplayName("O valor tem até 120 caracteres")
  void limite_do_valor() {
    assertThat(
            DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, List.of("x".repeat(120)))
                .values())
        .hasSize(1);

    assertRejected(
        () -> DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, List.of("x".repeat(121))),
        ConditionRejected.VALUE_INVALID);
  }

  @Test
  void origem_e_operador_sao_obrigatorios() {
    assertRejected(
        () -> DisplayCondition.of(null, ConditionOperator.EQUALS, List.of("a")),
        ConditionRejected.SOURCE_INVALID);
    assertRejected(
        () -> DisplayCondition.of(SOURCE, null, List.of("a")), ConditionRejected.OPERATOR_INVALID);
  }

  @Test
  @DisplayName("A recusa diz o campo que a causou")
  void recusa_diz_o_campo() {
    assertThatThrownBy(() -> DisplayCondition.between(SOURCE, 7, 6))
        .isInstanceOfSatisfying(
            ConditionRejected.class,
            rejected -> {
              assertThat(rejected.field()).isEqualTo("condition.values");
              assertThat(rejected.extensions()).containsEntry("field", "condition.values");
              assertThat(rejected.dependent()).isEmpty();
            });
  }

  @Test
  void apontar_para_outra_origem_preserva_o_resto() {
    var original = DisplayCondition.of(SOURCE, ConditionOperator.IN, List.of("a", "b"));
    var other = QuestionKey.generate();

    var moved = original.pointingTo(other);

    assertThat(moved.sourceKey()).isEqualTo(other);
    assertThat(moved.operator()).isEqualTo(ConditionOperator.IN);
    assertThat(moved.values()).containsExactly("a", "b");
  }

  @Test
  @DisplayName("A impressão digital ignora a ordem dos valores, e distingue operador e origem")
  void impressao_digital() {
    var ab = DisplayCondition.of(SOURCE, ConditionOperator.IN, List.of("a", "b"));
    var ba = DisplayCondition.of(SOURCE, ConditionOperator.IN, List.of("b", "a"));

    assertThat(ab.sameMeaningAs(ba)).isTrue();
    assertThat(ab.sameMeaningAs(DisplayCondition.of(SOURCE, ConditionOperator.EQUALS, List.of("a"))))
        .isFalse();
    assertThat(ab.sameMeaningAs(ab.pointingTo(QuestionKey.generate()))).isFalse();
  }
}

package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentationCriterion")
class SegmentationCriterionTest {

  private static SegmentationCriterion criterion(
      String attribute, RuleOperation operation, String value) {
    return new SegmentationCriterion(attribute, operation, Optional.ofNullable(value));
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"EQUALS", "NOT_EQUALS"})
  @DisplayName("Igualdade e diferença exigem valor de comparação")
  void comparacao_com_valor_e_aceita(RuleOperation operation) {
    var created = criterion("plan", operation, "pro");

    assertThat(created.attribute()).isEqualTo("plan");
    assertThat(created.operation()).isEqualTo(operation);
    assertThat(created.value()).contains("pro");
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"EQUALS", "NOT_EQUALS"})
  void recusa_comparacao_sem_valor(RuleOperation operation) {
    assertThatThrownBy(() -> criterion("plan", operation, null))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"PRESENT", "ABSENT"})
  @DisplayName("Presença e ausência não admitem valor")
  void presenca_sem_valor_e_aceita(RuleOperation operation) {
    assertThat(criterion("plan", operation, null).value()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"PRESENT", "ABSENT"})
  void recusa_presenca_com_valor(RuleOperation operation) {
    assertThatThrownBy(() -> criterion("plan", operation, "pro"))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void recusa_atributo_vazio(String invalid) {
    assertThatThrownBy(() -> criterion(invalid, RuleOperation.PRESENT, null))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  @Test
  void recusa_atributo_longo_demais() {
    assertThatThrownBy(() -> criterion("a".repeat(81), RuleOperation.PRESENT, null))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  @Test
  void recusa_operacao_ausente() {
    assertThatThrownBy(() -> criterion("plan", null, null))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  @Test
  void descarta_o_espaco_em_volta_do_atributo() {
    assertThat(criterion("  plan  ", RuleOperation.PRESENT, null).attribute()).isEqualTo("plan");
  }

  @Test
  void recusa_valor_longo_demais() {
    assertThatThrownBy(() -> criterion("plan", RuleOperation.EQUALS, "v".repeat(201)))
        .satisfies(SegmentationCriterionTest::criterioInvalido);
  }

  private static void criterioInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("segmentation_criterion.invalid");
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentationRule")
class SegmentationRuleTest {

  private static SegmentationRule rule(String attribute, RuleOperation operation, String value) {
    return new SegmentationRule(
        SegmentationRuleId.generate(), attribute, operation, Optional.ofNullable(value));
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"EQUALS", "NOT_EQUALS"})
  @DisplayName("Igualdade e diferença exigem valor")
  void comparacao_com_valor_e_aceita(RuleOperation operation) {
    var created = rule("plan", operation, "pro");

    assertThat(created.attribute()).isEqualTo("plan");
    assertThat(created.operation()).isEqualTo(operation);
    assertThat(created.value()).contains("pro");
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"EQUALS", "NOT_EQUALS"})
  void recusa_comparacao_sem_valor(RuleOperation operation) {
    assertThatThrownBy(() -> rule("plan", operation, null))
        .satisfies(SegmentationRuleTest::regraInvalida);
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"PRESENT", "ABSENT"})
  @DisplayName("Presença e ausência não admitem valor")
  void presenca_sem_valor_e_aceita(RuleOperation operation) {
    assertThat(rule("plan", operation, null).value()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = RuleOperation.class,
      names = {"PRESENT", "ABSENT"})
  void recusa_presenca_com_valor(RuleOperation operation) {
    assertThatThrownBy(() -> rule("plan", operation, "pro"))
        .satisfies(SegmentationRuleTest::regraInvalida);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void recusa_atributo_vazio(String invalid) {
    assertThatThrownBy(() -> rule(invalid, RuleOperation.PRESENT, null))
        .satisfies(SegmentationRuleTest::regraInvalida);
  }

  @Test
  void recusa_atributo_longo_demais() {
    assertThatThrownBy(() -> rule("a".repeat(81), RuleOperation.PRESENT, null))
        .satisfies(SegmentationRuleTest::regraInvalida);
  }

  @Test
  void descarta_o_espaco_em_volta_do_atributo() {
    assertThat(rule("  plan  ", RuleOperation.PRESENT, null).attribute()).isEqualTo("plan");
  }

  @Test
  @DisplayName("Expõe o critério sem o identificador, que é assunto da autoria")
  void expoe_o_criterio() {
    var criterion = rule("  plan  ", RuleOperation.EQUALS, "  pro  ").criterion();

    assertThat(criterion.attribute()).isEqualTo("plan");
    assertThat(criterion.operation()).isEqualTo(RuleOperation.EQUALS);
    assertThat(criterion.value()).contains("pro");
  }

  private static void regraInvalida(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("segmentation_rule.invalid");
  }
}

package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentationEvaluation")
class SegmentationEvaluationTest {

  private static final AttributeSnapshot PREMIUM =
      AttributeSnapshot.of(Map.of("plano", "premium", "pais", "BR"));

  private static SegmentationCriterion criterion(String attribute, RuleOperation op, String value) {
    return new SegmentationCriterion(attribute, op, Optional.ofNullable(value));
  }

  @Test
  @DisplayName("Sem critério nenhum, todo respondente casa")
  void sem_criterio_todos_casam() {
    assertThat(SegmentationEvaluation.satisfies(List.of(), AttributeSnapshot.empty())).isTrue();
  }

  @Test
  @DisplayName("Todos os critérios precisam ser satisfeitos: é conjunção")
  void e_conjuncao_de_todos_os_criterios() {
    var todos =
        List.of(
            criterion("plano", RuleOperation.EQUALS, "premium"),
            criterion("pais", RuleOperation.EQUALS, "BR"));

    assertThat(SegmentationEvaluation.satisfies(todos, PREMIUM)).isTrue();
  }

  @Test
  @DisplayName("Uma satisfeita e outra violada não casa")
  void uma_violada_derruba_o_conjunto() {
    var criteria =
        List.of(
            criterion("plano", RuleOperation.EQUALS, "premium"),
            criterion("pais", RuleOperation.EQUALS, "PT"));

    assertThat(SegmentationEvaluation.satisfies(criteria, PREMIUM)).isFalse();
  }

  @Test
  @DisplayName("Igualdade com atributo ausente ou vazio não casa")
  void igualdade_com_atributo_ausente_nao_casa() {
    var criteria = List.of(criterion("plano", RuleOperation.EQUALS, "premium"));

    assertThat(SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.empty())).isFalse();
    assertThat(
            SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.of(Map.of("plano", ""))))
        .isFalse();
  }

  @Test
  @DisplayName("Diferença sobre atributo ausente não casa: falha fechado (FR-016)")
  void diferenca_sobre_ausente_falha_fechado() {
    var criteria = List.of(criterion("plano", RuleOperation.NOT_EQUALS, "free"));

    assertThat(SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.empty())).isFalse();
    assertThat(
            SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.of(Map.of("plano", ""))))
        .isFalse();
    assertThat(SegmentationEvaluation.satisfies(criteria, PREMIUM)).isTrue();
  }

  @Test
  @DisplayName("Presença casa com atributo informado e não vazio")
  void presenca() {
    var criteria = List.of(criterion("plano", RuleOperation.PRESENT, null));

    assertThat(SegmentationEvaluation.satisfies(criteria, PREMIUM)).isTrue();
    assertThat(SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.empty())).isFalse();
    assertThat(
            SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.of(Map.of("plano", ""))))
        .isFalse();
  }

  @Test
  @DisplayName("Ausência casa com atributo que falta ou chegou vazio")
  void ausencia() {
    var criteria = List.of(criterion("plano", RuleOperation.ABSENT, null));

    assertThat(SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.empty())).isTrue();
    assertThat(
            SegmentationEvaluation.satisfies(criteria, AttributeSnapshot.of(Map.of("plano", ""))))
        .isTrue();
    assertThat(SegmentationEvaluation.satisfies(criteria, PREMIUM)).isFalse();
  }

  @Test
  @DisplayName("A comparação de valor é exata")
  void comparacao_de_valor_e_exata() {
    var criteria = List.of(criterion("plano", RuleOperation.EQUALS, "premium"));

    assertThat(
            SegmentationEvaluation.satisfies(
                criteria, AttributeSnapshot.of(Map.of("plano", "Premium"))))
        .isFalse();
  }
}

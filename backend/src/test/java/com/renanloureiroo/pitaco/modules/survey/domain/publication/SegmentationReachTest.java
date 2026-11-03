package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentationReach")
class SegmentationReachTest {

  private static final Map<String, KnownAttribute> PLANO =
      Map.of("plano", new KnownAttribute("plano", Set.of("free", "pro"), false));

  private static SegmentationRule rule(String attribute, RuleOperation operation, String value) {
    return SegmentationRule.create(attribute, operation, Optional.ofNullable(value));
  }

  @Test
  @DisplayName("Sem regra, nada a avisar")
  void sem_regra() {
    assertThat(SegmentationReach.warningsFor(List.of(), Map.of())).isEmpty();
  }

  @Test
  @DisplayName("Exigir atributo que o app nunca enviou não alcança ninguém")
  void atributo_nunca_visto() {
    var equals = rule("cidade", RuleOperation.EQUALS, "rio");
    var present = rule("cupom", RuleOperation.PRESENT, null);
    var notEquals = rule("idioma", RuleOperation.NOT_EQUALS, "en");

    var warnings = SegmentationReach.warningsFor(List.of(equals, present, notEquals), PLANO);

    assertThat(warnings)
        .extracting(PublicationWarning::code)
        .containsOnly(PublicationWarning.NO_KNOWN_MATCH);
    assertThat(warnings)
        .extracting(warning -> warning.ruleId().orElseThrow())
        .containsExactly(equals.id(), present.id(), notEquals.id());
  }

  @Test
  @DisplayName("Ausência casa com quem não envia nada, e nunca é avisada")
  void ausencia_nunca_avisa() {
    assertThat(
            SegmentationReach.warningsFor(
                List.of(rule("cidade", RuleOperation.ABSENT, null)), Map.of()))
        .isEmpty();
  }

  @Test
  @DisplayName("Igualdade a valor já visto passa; a valor nunca visto é avisada")
  void igualdade_contra_os_valores_vistos() {
    assertThat(
            SegmentationReach.warningsFor(List.of(rule("plano", RuleOperation.EQUALS, "pro")), PLANO))
        .isEmpty();

    var unseen = rule("plano", RuleOperation.EQUALS, "enterprise");
    assertThat(SegmentationReach.warningsFor(List.of(unseen), PLANO))
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo(PublicationWarning.NO_KNOWN_MATCH);
              assertThat(warning.attribute()).contains("plano");
            });
  }

  @Test
  @DisplayName("Catálogo saturado não permite concluir que um valor nunca foi visto")
  void catalogo_saturado_nao_avisa_valor() {
    var saturated = Map.of("plano", new KnownAttribute("plano", Set.of("free"), true));

    assertThat(
            SegmentationReach.warningsFor(
                List.of(rule("plano", RuleOperation.EQUALS, "enterprise")), saturated))
        .isEmpty();
  }

  @Test
  @DisplayName("Dois valores exigidos para o mesmo atributo se contradizem")
  void dois_valores_exigidos() {
    var warnings =
        SegmentationReach.warningsFor(
            List.of(
                rule("plano", RuleOperation.EQUALS, "free"),
                rule("plano", RuleOperation.EQUALS, "pro")),
            PLANO);

    assertThat(warnings)
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo(PublicationWarning.CONTRADICTORY_RULES);
              assertThat(warning.attribute()).contains("plano");
              assertThat(warning.ruleId()).isEmpty();
            });
  }

  @Test
  @DisplayName("Exigir e proibir o mesmo valor, ou exigir e proibir a presença, se contradizem")
  void exigir_e_proibir() {
    assertThat(
            SegmentationReach.warningsFor(
                List.of(
                    rule("plano", RuleOperation.EQUALS, "pro"),
                    rule("plano", RuleOperation.NOT_EQUALS, "pro")),
                PLANO))
        .extracting(PublicationWarning::code)
        .containsExactly(PublicationWarning.CONTRADICTORY_RULES);

    assertThat(
            SegmentationReach.warningsFor(
                List.of(
                    rule("plano", RuleOperation.PRESENT, null),
                    rule("plano", RuleOperation.ABSENT, null)),
                PLANO))
        .extracting(PublicationWarning::code)
        .containsExactly(PublicationWarning.CONTRADICTORY_RULES);
  }

  @Test
  @DisplayName("Igualdade e diferença de valores distintos convivem")
  void igualdade_e_diferenca_compativeis() {
    assertThat(
            SegmentationReach.warningsFor(
                List.of(
                    rule("plano", RuleOperation.EQUALS, "pro"),
                    rule("plano", RuleOperation.NOT_EQUALS, "free")),
                PLANO))
        .isEmpty();
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.EventName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyVersion — disparo e regras")
class SurveyVersionTriggerTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private SurveyVersion version;

  @BeforeEach
  void setUp() {
    version = SurveyVersion.create(SurveyId.generate(), 1);
  }

  private static Trigger trigger(String event, double rate) {
    return new Trigger(EventName.of(event), TriggerWindow.of(NOW, null), SamplingRate.of(rate));
  }

  private static SegmentationRule rule(String attribute) {
    return SegmentationRule.create(attribute, RuleOperation.PRESENT, Optional.empty());
  }

  @Test
  @DisplayName("Definir o disparo substitui: existe sempre um só")
  void definir_substitui_o_disparo() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    version.defineTrigger(trigger("app.opened", 1.0));

    assertThat(version.trigger()).isPresent();
    assertThat(version.trigger().orElseThrow().event()).isEqualTo(EventName.of("app.opened"));
    assertThat(version.trigger().orElseThrow().rate()).isEqualTo(SamplingRate.of(1.0));
  }

  @Test
  @DisplayName("Acrescentar regra exige disparo definido")
  void regra_exige_disparo() {
    var semDisparo = rule("plan");

    assertThatThrownBy(() -> version.addRule(semDisparo))
        .satisfies(erro -> assertCode(erro, ErrorType.BUSINESS_RULE, "trigger.not_defined"));
  }

  @Test
  void acrescenta_regras_ao_disparo() {
    version.defineTrigger(trigger("checkout.completed", 0.25));

    version.addRule(rule("plan"));
    version.addRule(rule("country"));

    assertThat(version.getRules())
        .extracting(SegmentationRule::attribute)
        .containsExactly("plan", "country");
  }

  @Test
  @DisplayName("Remover uma regra deixa as demais intactas")
  void remover_regra_preserva_as_demais() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    var first = rule("plan");
    var second = rule("country");
    version.addRule(first);
    version.addRule(second);

    version.removeRule(first.id());

    assertThat(version.getRules())
        .extracting(SegmentationRule::attribute)
        .containsExactly("country");
  }

  @Test
  void remover_regra_inexistente_e_recusado() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    var estranha = SegmentationRule.create("plan", RuleOperation.PRESENT, Optional.empty());

    assertThatThrownBy(() -> version.removeRule(estranha.id()))
        .satisfies(erro -> assertCode(erro, ErrorType.NOT_FOUND, "segmentation_rule.not_found"));
  }

  @Test
  @DisplayName("Redefinir o disparo não descarta as regras já penduradas nele")
  void redefinir_disparo_preserva_as_regras() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    version.addRule(rule("plan"));

    version.defineTrigger(trigger("app.opened", 0.5));

    assertThat(version.getRules()).hasSize(1);
  }

  @Test
  @DisplayName("Versão publicada recusa as três escritas de disparo e regra")
  void versao_publicada_recusa_toda_escrita() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    var existente = rule("plan");
    version.addRule(existente);
    var published = publishedCopyOf(version);
    var nova = rule("country");
    var outro = trigger("app.opened", 0.5);

    assertThatThrownBy(() -> published.defineTrigger(outro))
        .satisfies(SurveyVersionTriggerTest::conteudoCongelado);
    assertThatThrownBy(() -> published.addRule(nova))
        .satisfies(SurveyVersionTriggerTest::conteudoCongelado);
    assertThatThrownBy(() -> published.removeRule(existente.id()))
        .satisfies(SurveyVersionTriggerTest::conteudoCongelado);
  }

  private static SurveyVersion publishedCopyOf(SurveyVersion draft) {
    return SurveyVersion.restore(
        draft.id(),
        draft.getSurveyId(),
        draft.getNumber(),
        SurveyVersionStatus.PUBLISHED,
        draft.getQuestions(),
        draft.trigger(),
        draft.getRules(),
        Optional.empty(),
        Optional.empty(),
        1,
        Optional.of(NOW));
  }

  private static void conteudoCongelado(Throwable error) {
    assertCode(error, ErrorType.BUSINESS_RULE, "survey.content_frozen");
  }

  private static void assertCode(Throwable error, ErrorType type, String code) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(type);
    assertThat(domainError.code()).isEqualTo(code);
  }

  @Test
  void a_lista_de_regras_devolvida_e_imutavel() {
    version.defineTrigger(trigger("checkout.completed", 0.25));
    var rules = version.getRules();
    var nova = rule("plan");

    assertThatThrownBy(() -> rules.add(nova)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(List.<SegmentationRule>of()).isEmpty();
  }
}

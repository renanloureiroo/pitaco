package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SegmentationRuleNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyContentFrozen;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.TriggerNotDefined;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SegmentationRuleOutput;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Casos de uso de disparo e segmentação")
class TriggerUseCasesTest {

  private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private ApplicationId applicationId;
  private Survey survey;

  private DefineTriggerUseCase define;
  private AddSegmentationRuleUseCase addRule;
  private RemoveSegmentationRuleUseCase removeRule;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    applicationId = ApplicationId.generate();
    survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);

    define = new DefineTriggerUseCase(surveys, versions);
    addRule = new AddSegmentationRuleUseCase(surveys, versions);
    removeRule = new RemoveSegmentationRuleUseCase(surveys, versions);
  }

  private DefineTriggerUseCase.Input triggerInput(String event, Instant end, double rate) {
    return new DefineTriggerUseCase.Input(
        applicationId.value(), survey.id().value(), event, START, Optional.ofNullable(end), rate);
  }

  private AddSegmentationRuleUseCase.Input ruleInput(
      String attribute, RuleOperation operation, String value) {
    return new AddSegmentationRuleUseCase.Input(
        applicationId.value(),
        survey.id().value(),
        attribute,
        operation,
        Optional.ofNullable(value));
  }

  private SurveyVersion stored() {
    return versions.findDraft(survey.id()).orElseThrow();
  }

  private void publishTheDraft() {
    versions.findDraft(survey.id()).ifPresent(draft -> versions.delete(draft.id()));
    SurveyVersionFactory.aVersion().forSurvey(survey.id()).buildPublishedSavedIn(versions);
  }

  @Test
  @DisplayName("Define o disparo e o grava na versão editável")
  void define_o_disparo() {
    var output = define.execute(triggerInput("checkout.completed", null, 0.25));

    assertThat(output.eventName()).isEqualTo("checkout.completed");
    assertThat(output.windowStart()).isEqualTo(START);
    assertThat(output.windowEnd()).isEmpty();
    assertThat(output.samplingRate()).isEqualTo(0.25);
    assertThat(output.rules()).isEmpty();
    assertThat(stored().trigger()).isPresent();
  }

  @Test
  @DisplayName("Redefinir substitui: continua havendo um só, e as regras permanecem")
  void redefinir_substitui() {
    define.execute(triggerInput("checkout.completed", null, 0.25));
    addRule.execute(ruleInput("plan", RuleOperation.EQUALS, "pro"));

    var output = define.execute(triggerInput("app.opened", START.plusSeconds(60), 1.0));

    assertThat(output.eventName()).isEqualTo("app.opened");
    assertThat(output.windowEnd()).contains(START.plusSeconds(60));
    assertThat(output.samplingRate()).isEqualTo(1.0);
    assertThat(output.rules())
        .extracting(SegmentationRuleOutput::attribute)
        .containsExactly("plan");
  }

  @Test
  @DisplayName("Janela com fim não posterior ao início é recusada pelo value object")
  void recusa_janela_incoerente() {
    var input = triggerInput("checkout.completed", START, 0.25);

    assertThatThrownBy(() -> define.execute(input))
        .isInstanceOf(DomainException.class)
        .satisfies(
            erro ->
                assertThat(((DomainException) erro).code()).isEqualTo("trigger.window_invalid"));
  }

  @Test
  void recusa_proporcao_fora_do_intervalo_e_evento_fora_do_formato() {
    assertThatThrownBy(() -> define.execute(triggerInput("checkout.completed", null, 1.5)))
        .satisfies(
            erro ->
                assertThat(((DomainException) erro).code())
                    .isEqualTo("trigger.sampling_rate_invalid"));
    assertThatThrownBy(() -> define.execute(triggerInput("Checkout Completed", null, 0.25)))
        .satisfies(
            erro ->
                assertThat(((DomainException) erro).code())
                    .isEqualTo("trigger.event_name_invalid"));
  }

  @Test
  void recusa_definir_sem_rascunho() {
    publishTheDraft();
    var input = triggerInput("checkout.completed", null, 0.25);

    assertThatThrownBy(() -> define.execute(input)).isInstanceOf(SurveyContentFrozen.class);
  }

  @Test
  void recusa_definir_fora_do_escopo() {
    var input =
        new DefineTriggerUseCase.Input(
            ApplicationId.generate().value(),
            survey.id().value(),
            "checkout.completed",
            START,
            Optional.empty(),
            0.25);

    assertThatThrownBy(() -> define.execute(input)).isInstanceOf(SurveyNotFound.class);
  }

  @Test
  @DisplayName("Acrescenta a regra vinculada ao disparo")
  void acrescenta_regra() {
    define.execute(triggerInput("checkout.completed", null, 0.25));

    var output = addRule.execute(ruleInput("plan", RuleOperation.EQUALS, "pro"));

    assertThat(output.attribute()).isEqualTo("plan");
    assertThat(output.operation()).isEqualTo(RuleOperation.EQUALS);
    assertThat(output.value()).contains("pro");
    assertThat(stored().getRules()).hasSize(1);
  }

  @Test
  @DisplayName("Sem disparo definido, não há onde pendurar a regra")
  void recusa_regra_sem_disparo() {
    var input = ruleInput("plan", RuleOperation.PRESENT, null);

    assertThatThrownBy(() -> addRule.execute(input))
        .isInstanceOf(TriggerNotDefined.class)
        .satisfies(
            erro -> assertThat(((TriggerNotDefined) erro).code()).isEqualTo("trigger.not_defined"));
  }

  @Test
  void recusa_regra_incoerente_com_a_operacao() {
    define.execute(triggerInput("checkout.completed", null, 0.25));

    assertThatThrownBy(() -> addRule.execute(ruleInput("plan", RuleOperation.EQUALS, null)))
        .satisfies(
            erro ->
                assertThat(((DomainException) erro).code()).isEqualTo("segmentation_rule.invalid"));
    assertThatThrownBy(() -> addRule.execute(ruleInput("plan", RuleOperation.PRESENT, "pro")))
        .satisfies(
            erro ->
                assertThat(((DomainException) erro).code()).isEqualTo("segmentation_rule.invalid"));
  }

  @Test
  @DisplayName("Remover uma regra deixa as demais intactas")
  void remove_regra() {
    define.execute(triggerInput("checkout.completed", null, 0.25));
    var first = addRule.execute(ruleInput("plan", RuleOperation.EQUALS, "pro"));
    addRule.execute(ruleInput("country", RuleOperation.PRESENT, null));

    removeRule.execute(
        new RemoveSegmentationRuleUseCase.Input(
            applicationId.value(), survey.id().value(), first.id()));

    assertThat(stored().getRules()).hasSize(1);
    assertThat(stored().getRules().getFirst().attribute()).isEqualTo("country");
  }

  @Test
  @DisplayName("Regra inexistente, de outra versão ou malformada recusam do mesmo jeito")
  void recusa_remover_regra_desconhecida() {
    define.execute(triggerInput("checkout.completed", null, 0.25));

    assertThatThrownBy(
            () ->
                removeRule.execute(
                    new RemoveSegmentationRuleUseCase.Input(
                        applicationId.value(),
                        survey.id().value(),
                        SegmentationRuleId.generate().value())))
        .isInstanceOf(SegmentationRuleNotFound.class);

    assertThatThrownBy(
            () ->
                removeRule.execute(
                    new RemoveSegmentationRuleUseCase.Input(
                        applicationId.value(), survey.id().value(), "nao-e-um-id")))
        .isInstanceOf(SegmentationRuleNotFound.class);
  }

  @Test
  void recusa_remover_regra_sem_rascunho() {
    define.execute(triggerInput("checkout.completed", null, 0.25));
    var rule = addRule.execute(ruleInput("plan", RuleOperation.PRESENT, null));
    publishTheDraft();

    var input =
        new RemoveSegmentationRuleUseCase.Input(
            applicationId.value(), survey.id().value(), rule.id());

    assertThatThrownBy(() -> removeRule.execute(input)).isInstanceOf(SurveyContentFrozen.class);
  }
}

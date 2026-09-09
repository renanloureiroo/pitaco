package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("DTOs de entrada de disparo e publicação")
class TriggerAndPublishRequestDTOsTest {

  private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void startValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static Map<String, String> violationsOf(Object dto) {
    return validator.validate(dto).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static DefineTriggerRequestDTO trigger(String event, Instant end, Double rate) {
    return new DefineTriggerRequestDTO(event, START, end, rate);
  }

  @Test
  void aceita_um_disparo_completo() {
    assertThat(violationsOf(trigger("checkout.completed", START.plusSeconds(60), 0.25))).isEmpty();
  }

  @Test
  @DisplayName("Fim de janela ausente é aceito: significa tempo indeterminado")
  void aceita_janela_sem_fim() {
    assertThat(violationsOf(trigger("checkout.completed", null, 0.25))).isEmpty();
  }

  @Test
  void recusa_evento_ausente() {
    assertThat(violationsOf(trigger(null, null, 0.25)))
        .containsEntry("eventName", "Nome do evento é obrigatório");
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Vazio e só-espaços violam obrigatoriedade e formato ao mesmo tempo")
  void recusa_evento_em_branco(String invalid) {
    assertThat(violationsOf(trigger(invalid, null, 0.25))).containsKey("eventName");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "Checkout.completed", "checkout completed", "1checkout", "x@y"})
  @DisplayName("A mensagem espelha a do value object EventName")
  void recusa_evento_fora_do_formato(String invalid) {
    assertThat(violationsOf(trigger(invalid, null, 0.25)))
        .containsEntry(
            "eventName",
            "Nome do evento deve começar por letra minúscula e usar apenas letras minúsculas, "
                + "dígitos, ponto e sublinhado, com 2 a 80 caracteres");
  }

  @Test
  void recusa_inicio_de_janela_ausente() {
    assertThat(violationsOf(new DefineTriggerRequestDTO("checkout.completed", null, null, 0.25)))
        .containsEntry("windowStart", "Início da janela é obrigatório");
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 1.1, 2.0})
  void recusa_proporcao_fora_do_intervalo(double rate) {
    assertThat(violationsOf(trigger("checkout.completed", null, rate)))
        .containsEntry("samplingRate", "Proporção deve estar entre 0 e 1");
  }

  @Test
  void aceita_os_extremos_da_proporcao() {
    assertThat(violationsOf(trigger("checkout.completed", null, 0.0))).isEmpty();
    assertThat(violationsOf(trigger("checkout.completed", null, 1.0))).isEmpty();
  }

  @Test
  void recusa_proporcao_ausente() {
    assertThat(violationsOf(trigger("checkout.completed", null, null)))
        .containsEntry("samplingRate", "Proporção é obrigatória");
  }

  @Test
  void converte_o_disparo_em_input() {
    var input = trigger("checkout.completed", START.plusSeconds(60), 0.25).toInput("app", "survey");

    assertThat(input.eventName()).isEqualTo("checkout.completed");
    assertThat(input.windowStart()).isEqualTo(START);
    assertThat(input.windowEnd()).contains(START.plusSeconds(60));
    assertThat(input.samplingRate()).isEqualTo(0.25);
  }

  @Test
  void aceita_uma_regra_de_igualdade_com_valor() {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO("plan", "equals", "pro"))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void recusa_atributo_ausente(String invalid) {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO(invalid, "present", null)))
        .containsEntry("attribute", "Atributo é obrigatório");
  }

  @Test
  @DisplayName("Operação vazia viola obrigatoriedade e formato ao mesmo tempo")
  void recusa_operacao_em_branco() {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO("plan", "  ", null)))
        .containsKey("operation");
  }

  @Test
  void recusa_atributo_longo_demais() {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO("a".repeat(81), "present", null)))
        .containsEntry("attribute", "Atributo não pode passar de 80 caracteres");
  }

  @ParameterizedTest
  @ValueSource(strings = {"EQUALS", "igual", "equals,present"})
  void recusa_operacao_desconhecida(String operation) {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO("plan", operation, null)))
        .containsEntry("operation", "Operação deve ser equals, not_equals, present ou absent");
  }

  @Test
  void recusa_valor_longo_demais() {
    assertThat(violationsOf(new AddSegmentationRuleRequestDTO("plan", "equals", "a".repeat(201))))
        .containsEntry("value", "Valor não pode passar de 200 caracteres");
  }

  @Test
  @DisplayName("A coerência entre operação e valor é do value object, não da borda")
  void converte_a_regra_em_input() {
    var comValor = new AddSegmentationRuleRequestDTO("plan", "equals", "pro").toInput("a", "s");
    var semValor = new AddSegmentationRuleRequestDTO("plan", "present", null).toInput("a", "s");

    assertThat(comValor.operation()).isEqualTo(RuleOperation.EQUALS);
    assertThat(comValor.value()).contains("pro");
    assertThat(semValor.operation()).isEqualTo(RuleOperation.PRESENT);
    assertThat(semValor.value()).isEmpty();
  }

  @Test
  @DisplayName("A classificação é opcional na versão 1")
  void aceita_publicacao_sem_classificacao() {
    assertThat(violationsOf(new PublishSurveyRequestDTO(null, null))).isEmpty();
    assertThat(violationsOf(PublishSurveyRequestDTO.empty())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cosmetic", "semantic"})
  void aceita_as_duas_classificacoes(String kind) {
    assertThat(violationsOf(new PublishSurveyRequestDTO(kind, "resumo"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"COSMETIC", "estetica", ""})
  void recusa_classificacao_desconhecida(String kind) {
    assertThat(violationsOf(new PublishSurveyRequestDTO(kind, null)))
        .containsEntry("changeKind", "Classificação deve ser cosmetic ou semantic");
  }

  @Test
  void recusa_resumo_longo_demais() {
    assertThat(violationsOf(new PublishSurveyRequestDTO("cosmetic", "a".repeat(501))))
        .containsEntry("changeSummary", "Resumo da mudança não pode passar de 500 caracteres");
    assertThat(violationsOf(new PublishSurveyRequestDTO("cosmetic", "a".repeat(500)))).isEmpty();
  }

  @Test
  void converte_a_publicacao_em_input() {
    var comClassificacao =
        new PublishSurveyRequestDTO("cosmetic", "Correção").toInput("app", "survey");
    var semClassificacao = PublishSurveyRequestDTO.empty().toInput("app", "survey");

    assertThat(comClassificacao.changeKind()).contains(ChangeKind.COSMETIC);
    assertThat(comClassificacao.changeSummary()).contains("Correção");
    assertThat(semClassificacao.changeKind()).isEmpty();
    assertThat(semClassificacao.changeSummary()).isEmpty();
  }
}

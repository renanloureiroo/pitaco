package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EligibilityRequestDTO")
class EligibilityRequestDTOTest {

  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";

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

  private Map<String, String> violationsOf(EligibilityRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static EligibilityRequestDTO requestWith(
      String event, RespondentDTO respondent, Map<String, String> attributes) {
    return new EligibilityRequestDTO(event, respondent, attributes);
  }

  @Test
  void aceita_o_minimo_com_referencia_do_app() {
    assertThat(
            violationsOf(
                requestWith("checkout.completed", new RespondentDTO("u-8f1c", null), null)))
        .isEmpty();
  }

  @Test
  void aceita_o_minimo_com_dispositivo() {
    assertThat(violationsOf(requestWith("checkout.completed", new RespondentDTO(null, DEVICE), null)))
        .isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void recusa_evento_ausente(String blank) {
    assertThat(violationsOf(requestWith(blank, new RespondentDTO("u-8f1c", null), null)))
        .containsEntry("event", "Evento é obrigatório");
  }

  @Test
  void recusa_evento_longo_demais() {
    assertThat(
            violationsOf(
                requestWith("e".repeat(81), new RespondentDTO("u-8f1c", null), null)))
        .containsEntry("event", "Evento não pode passar de 80 caracteres");
  }

  @Test
  void recusa_respondente_ausente() {
    assertThat(violationsOf(requestWith("checkout.completed", null, null)))
        .containsEntry("respondent", "Identificação do respondente é obrigatória");
  }

  @Test
  @DisplayName("A mensagem da referência espelha a do value object")
  void recusa_referencia_longa_demais() {
    assertThat(
            violationsOf(
                requestWith(
                    "checkout.completed", new RespondentDTO("u".repeat(201), null), null)))
        .containsEntry(
            "respondent.reference", "Identificação não pode passar de 200 caracteres");
  }

  @Test
  void recusa_dispositivo_que_nao_e_uuid() {
    assertThat(
            violationsOf(
                requestWith("checkout.completed", new RespondentDTO(null, "nao-e-uuid"), null)))
        .containsEntry("respondent.deviceId", "Identificador de dispositivo deve ser um UUID");
  }

  @Test
  void aceita_o_limite_de_cinquenta_atributos() {
    assertThat(
            violationsOf(
                requestWith("checkout.completed", new RespondentDTO("u-8f1c", null), attributes(50))))
        .isEmpty();
  }

  @Test
  void recusa_acima_de_cinquenta_atributos() {
    assertThat(
            violationsOf(
                requestWith("checkout.completed", new RespondentDTO("u-8f1c", null), attributes(51))))
        .containsEntry("attributes", "No máximo 50 atributos por consulta");
  }

  @Test
  void recusa_nome_de_atributo_longo_demais() {
    var violations =
        violationsOf(
            requestWith(
                "checkout.completed",
                new RespondentDTO("u-8f1c", null),
                Map.of("a".repeat(81), "v")));

    assertThat(violations.values())
        .contains("Nome do atributo não pode passar de 80 caracteres");
  }

  @Test
  void recusa_valor_de_atributo_longo_demais() {
    var violations =
        violationsOf(
            requestWith(
                "checkout.completed",
                new RespondentDTO("u-8f1c", null),
                Map.of("plano", "v".repeat(201))));

    assertThat(violations.values())
        .contains("Valor do atributo não pode passar de 200 caracteres");
  }

  private static Map<String, String> attributes(int count) {
    return IntStream.range(0, count)
        .boxed()
        .collect(
            LinkedHashMap::new, (map, i) -> map.put("attr" + i, "v" + i), LinkedHashMap::putAll);
  }
}

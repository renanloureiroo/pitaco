package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("OpenDisplayRequestDTO")
class OpenDisplayRequestDTOTest {

  private static final String DISPLAY = "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90";

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

  private Map<String, String> violationsOf(OpenDisplayRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static OpenDisplayRequestDTO requestWith(
      String displayId, String surveyId, String versionId, RespondentDTO respondent, String sdk) {
    return new OpenDisplayRequestDTO(displayId, surveyId, versionId, respondent, null, sdk);
  }

  private static OpenDisplayRequestDTO valid() {
    return requestWith(
        DISPLAY,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        new RespondentDTO("u-8f1c", null),
        "1.4.2");
  }

  @Test
  void aceita_um_envio_completo() {
    assertThat(violationsOf(valid())).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void recusa_identificador_de_exibicao_ausente(String blank) {
    assertThat(
            violationsOf(
                requestWith(
                    blank,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    new RespondentDTO("u-8f1c", null),
                    null)))
        .extractingByKey("displayId")
        .isIn(
            "Identificador de exibição é obrigatório", "Identificador de exibição deve ser um UUID");
  }

  @Test
  void recusa_identificador_de_exibicao_que_nao_e_uuid() {
    assertThat(
            violationsOf(
                requestWith(
                    "3b1f0a2c",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    new RespondentDTO("u-8f1c", null),
                    null)))
        .containsEntry("displayId", "Identificador de exibição deve ser um UUID");
  }

  @Test
  void recusa_pesquisa_e_versao_ausentes() {
    var violations =
        violationsOf(requestWith(DISPLAY, null, null, new RespondentDTO("u-8f1c", null), null));

    assertThat(violations)
        .containsEntry("surveyId", "Identificador de pesquisa é obrigatório")
        .containsEntry("versionId", "Identificador de versão é obrigatório");
  }

  @Test
  @DisplayName("Respondente sem nenhuma identificação é recusado")
  void recusa_respondente_sem_identificacao() {
    var violations =
        violationsOf(
            requestWith(
                DISPLAY,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new RespondentDTO(null, null),
                null));

    assertThat(violations)
        .containsEntry(
            "respondent.identified",
            "Informe a referência do app ou o identificador do dispositivo");
  }

  @Test
  void recusa_versao_de_sdk_longa_demais() {
    assertThat(
            violationsOf(
                requestWith(
                    DISPLAY,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    new RespondentDTO("u-8f1c", null),
                    "v".repeat(41))))
        .containsEntry("sdkVersion", "Versão do SDK não pode passar de 40 caracteres");
  }
}

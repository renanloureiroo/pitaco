package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IssueApiKeyRequestDTOTest {

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

  private Map<String, String> violationsOf(String label) {
    return validator.validate(new IssueApiKeyRequestDTO(label)).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  void aceita_um_rotulo_legivel() {
    assertThat(violationsOf("app iOS")).isEmpty();
  }

  @Test
  void aceita_o_rotulo_no_limite() {
    assertThat(violationsOf("a".repeat(80))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_rotulo_ausente(String invalid) {
    assertThat(violationsOf(invalid)).containsEntry("label", "Rótulo é obrigatório");
  }

  @Test
  void rejeita_rotulo_longo_demais() {
    assertThat(violationsOf("a".repeat(81)))
        .containsEntry("label", "Rótulo não pode passar de 80 caracteres");
  }

  @Test
  void converte_para_a_entrada_do_caso_de_uso() {
    var applicationId = UUID.randomUUID().toString();

    var input = new IssueApiKeyRequestDTO("app iOS").toInput(applicationId);

    assertThat(input.applicationId()).isEqualTo(applicationId);
    assertThat(input.label()).isEqualTo("app iOS");
  }

  @Test
  void nao_impoe_formato_ao_identificador_da_aplicacao() {
    // Validar o formato aqui devolveria 400 e revelaria o formato interno do identificador.
    assertThat(new IssueApiKeyRequestDTO("app iOS").toInput("nao-e-um-id").applicationId())
        .isEqualTo("nao-e-um-id");
  }
}

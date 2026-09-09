package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListRespondentsQueryDTOTest {

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

  private Map<String, String> violationsOf(Integer page, Integer size) {
    return validator.validate(new ListRespondentsQueryDTO(page, size)).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  void aceita_a_consulta_sem_nenhum_parametro() {
    assertThat(violationsOf(null, null)).isEmpty();
  }

  @Test
  void aceita_os_limites_de_tamanho_de_pagina() {
    assertThat(violationsOf(0, 1)).isEmpty();
    assertThat(violationsOf(0, 100)).isEmpty();
  }

  @Test
  void rejeita_pagina_negativa() {
    assertThat(violationsOf(-1, null)).containsEntry("page", "Página não pode ser negativa");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101})
  void rejeita_tamanho_de_pagina_fora_dos_limites(int size) {
    assertThat(violationsOf(null, size))
        .containsEntry("size", "Tamanho de página deve estar entre 1 e 100");
  }

  @Test
  void aplica_os_padroes_quando_nada_e_informado() {
    var input = new ListRespondentsQueryDTO(null, null).toInput("app");

    assertThat(input.applicationId()).isEqualTo("app");
    assertThat(input.page()).isZero();
    assertThat(input.size()).isEqualTo(20);
  }

  @Test
  void preserva_a_pagina_e_o_tamanho_informados() {
    var input = new ListRespondentsQueryDTO(3, 50).toInput("app");

    assertThat(input.page()).isEqualTo(3);
    assertThat(input.size()).isEqualTo(50);
  }
}

package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListApiKeysQueryDTOTest {

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

  private Map<String, String> violationsOf(String status, Integer page, Integer size) {
    return validator.validate(new ListApiKeysQueryDTO(status, page, size)).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  void aceita_a_consulta_sem_nenhum_parametro() {
    assertThat(violationsOf(null, null, null)).isEmpty();
  }

  @Test
  void aceita_os_limites_de_tamanho_de_pagina() {
    assertThat(violationsOf(null, 0, 1)).isEmpty();
    assertThat(violationsOf(null, 0, 100)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"active", "revoked"})
  void aceita_os_estados_conhecidos(String status) {
    assertThat(violationsOf(status, null, null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVE", "expirada", "", "active,revoked"})
  void rejeita_estado_desconhecido(String status) {
    assertThat(violationsOf(status, null, null))
        .containsEntry("status", "Estado deve ser active ou revoked");
  }

  @Test
  void rejeita_pagina_negativa() {
    assertThat(violationsOf(null, -1, null))
        .containsEntry("page", "Página não pode ser negativa");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101})
  void rejeita_tamanho_de_pagina_fora_dos_limites(int size) {
    assertThat(violationsOf(null, null, size))
        .containsEntry("size", "Tamanho de página deve estar entre 1 e 100");
  }

  @Test
  void aplica_os_padroes_quando_nada_e_informado() {
    var applicationId = UUID.randomUUID().toString();

    var input = new ListApiKeysQueryDTO(null, null, null).toInput(applicationId);

    assertThat(input.applicationId()).isEqualTo(applicationId);
    assertThat(input.status()).isEmpty();
    assertThat(input.page()).isZero();
    assertThat(input.size()).isEqualTo(20);
  }

  @Test
  void converte_o_estado_informado() {
    assertThat(new ListApiKeysQueryDTO("active", null, null).toInput("app").status())
        .contains(ApiKeyStatus.ACTIVE);
    assertThat(new ListApiKeysQueryDTO("revoked", null, null).toInput("app").status())
        .contains(ApiKeyStatus.REVOKED);
  }

  @Test
  void preserva_a_pagina_e_o_tamanho_informados() {
    var input = new ListApiKeysQueryDTO(null, 3, 50).toInput("app");

    assertThat(input.page()).isEqualTo(3);
    assertThat(input.size()).isEqualTo(50);
    assertThat(input.status()).isEqualTo(Optional.<ApiKeyStatus>empty());
  }

  @Test
  void nao_impoe_formato_ao_identificador_da_aplicacao() {
    // Validar o formato aqui devolveria 400 e revelaria o formato interno do identificador.
    assertThat(new ListApiKeysQueryDTO(null, null, null).toInput("nao-e-um-id").applicationId())
        .isEqualTo("nao-e-um-id");
  }
}

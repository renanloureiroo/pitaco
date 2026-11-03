package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.usecase.Patch;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class UpdateApplicationRequestDTOTest {

  private static ValidatorFactory factory;
  private static Validator validator;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @BeforeAll
  static void startValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private Map<String, String> violationsOf(UpdateApplicationRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static UpdateApplicationRequestDTO read(String json) {
    return JSON.readValue(json, UpdateApplicationRequestDTO.class);
  }

  @Test
  @DisplayName("Corpo vazio é válido e não altera campo nenhum")
  void corpo_vazio_nao_altera_nada() {
    var request = read("{}");

    assertThat(violationsOf(request)).isEmpty();

    var input = request.toInput("app-1");
    assertThat(input.name()).isEmpty();
    assertThat(input.quietPeriodDays()).isEqualTo(Patch.absent());
    assertThat(input.retentionDays()).isEqualTo(Patch.absent());
    assertThat(input.openTextRetentionDays()).isEqualTo(Patch.absent());
  }

  @Test
  @DisplayName("null explícito remove o prazo; valor define")
  void distingue_nulo_de_ausente_e_de_valor() {
    var input =
        read("{\"quietPeriodDays\":null,\"retentionDays\":365,\"name\":\"Acme\"}")
            .toInput("app-1");

    assertThat(input.quietPeriodDays()).isEqualTo(Patch.clear());
    assertThat(input.retentionDays()).isEqualTo(Patch.set(365));
    assertThat(input.openTextRetentionDays()).isEqualTo(Patch.absent());
    assertThat(input.name()).contains("Acme");
  }

  @Test
  @DisplayName("Nome enviado como null é corpo malformado: o nome não é removível")
  void nome_nulo_e_corpo_malformado() {
    assertThatThrownBy(() -> read("{\"name\":null}")).isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Prazo com tipo errado é corpo malformado")
  void prazo_com_tipo_errado_e_corpo_malformado() {
    assertThatThrownBy(() -> read("{\"retentionDays\":\"trinta\"}"))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Nome em branco é recusado na constraint")
  void recusa_nome_em_branco() {
    var request = new UpdateApplicationRequestDTO(Optional.of("   "), null, null, null);

    assertThat(violationsOf(request)).containsEntry("name", "Nome é obrigatório");
  }

  @Test
  @DisplayName("Prazo abaixo de um dia é recusado na constraint")
  void recusa_prazo_abaixo_de_um_dia() {
    var request =
        new UpdateApplicationRequestDTO(
            null, Optional.of(0), Optional.of(-1), Optional.of(0));

    var violations = violationsOf(request);

    assertThat(violations)
        .containsEntry("quietPeriodDays", "O intervalo de descanso deve ser de ao menos um dia")
        .containsEntry("retentionDays", "O prazo de retenção deve ser de ao menos um dia")
        .containsEntry(
            "openTextRetentionDays",
            "O prazo de retenção de texto livre deve ser de ao menos um dia");
  }

  @Test
  @DisplayName("Nome acima de 120 caracteres é recusado")
  void recusa_nome_longo() {
    var request =
        new UpdateApplicationRequestDTO(Optional.of("a".repeat(121)), null, null, null);

    assertThat(violationsOf(request))
        .containsEntry("name", "Nome não pode passar de 120 caracteres");
  }

  @Test
  @DisplayName("Todos os campos com valores válidos passam")
  void aceita_valores_validos() {
    var request =
        new UpdateApplicationRequestDTO(
            Optional.of("Acme"), Optional.of(15), Optional.of(180), Optional.of(30));

    assertThat(violationsOf(request)).isEmpty();
  }
}

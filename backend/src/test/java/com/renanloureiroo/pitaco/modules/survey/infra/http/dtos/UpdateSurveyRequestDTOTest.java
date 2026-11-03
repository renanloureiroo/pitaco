package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("UpdateSurveyRequestDTO")
class UpdateSurveyRequestDTOTest {

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

  private static Map<String, String> violationsOf(UpdateSurveyRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static UpdateSurveyRequestDTO read(String json) {
    return JSON.readValue(json, UpdateSurveyRequestDTO.class);
  }

  @Test
  @DisplayName("Corpo vazio é válido e não altera campo nenhum")
  void corpo_vazio_nao_altera_nada() {
    var request = read("{}");

    assertThat(violationsOf(request)).isEmpty();

    var input = request.toInput("app-1", "srv-1");
    assertThat(input.name()).isEmpty();
    assertThat(input.priority()).isEmpty();
    assertThat(input.responseQuota()).isEqualTo(Patch.absent());
    assertThat(input.ignoresQuietPeriod()).isEmpty();
  }

  @Test
  @DisplayName("Cota nula vira remoção; valores presentes vão como estão")
  void cota_nula_remove() {
    var input =
        read("{\"priority\":10,\"responseQuota\":null,\"ignoresQuietPeriod\":true}")
            .toInput("app-1", "srv-1");

    assertThat(input.priority()).contains(10);
    assertThat(input.responseQuota()).isEqualTo(Patch.clear());
    assertThat(input.ignoresQuietPeriod()).contains(true);

    assertThat(read("{\"responseQuota\":100}").toInput("app-1", "srv-1").responseQuota())
        .isEqualTo(Patch.set(100));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"name\":null}",
        "{\"priority\":null}",
        "{\"ignoresQuietPeriod\":null}",
        "{\"priority\":\"alta\"}",
        "{\"responseQuota\":1.5}",
        "{\"ignoresQuietPeriod\":\"sim\"}",
        "[]"
      })
  @DisplayName("Null em campo não removível e tipo errado são corpo malformado")
  void corpo_malformado(String json) {
    assertThatThrownBy(() -> read(json)).isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("As mensagens espelham as do value object Exposure")
  void mensagens_espelham_o_dominio() {
    assertThat(violationsOf(read("{\"priority\":101}")))
        .containsValue("A prioridade deve estar entre -100 e 100");
    assertThat(violationsOf(read("{\"priority\":-101}")))
        .containsValue("A prioridade deve estar entre -100 e 100");
    assertThat(violationsOf(read("{\"responseQuota\":0}")))
        .containsValue("A cota de respostas deve ser de ao menos uma");
    assertThat(violationsOf(new UpdateSurveyRequestDTO(Optional.of(" "), null, null, null)))
        .containsValue("Nome é obrigatório");
  }
}

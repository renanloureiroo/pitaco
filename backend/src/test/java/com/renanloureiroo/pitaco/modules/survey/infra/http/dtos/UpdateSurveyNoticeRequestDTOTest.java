package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.usecase.Patch;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("UpdateSurveyRequestDTO — aviso de texto livre")
class UpdateSurveyNoticeRequestDTOTest {

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

  private static UpdateSurveyRequestDTO read(String json) {
    return JSON.readValue(json, UpdateSurveyRequestDTO.class);
  }

  private static List<String> messagesOf(UpdateSurveyRequestDTO request) {
    return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }

  @Test
  @DisplayName("Ausente não mexe; texto nulo volta ao padrão; valores presentes vão como estão")
  void tres_estados() {
    var absent = read("{}").toInput("app-1", "srv-1");
    assertThat(absent.freeTextNoticeEnabled()).isEmpty();
    assertThat(absent.freeTextNoticeText()).isEqualTo(Patch.absent());

    var cleared =
        read("{\"freeTextNoticeEnabled\":false,\"freeTextNoticeText\":null}").toInput("app-1", "srv-1");
    assertThat(cleared.freeTextNoticeEnabled()).contains(false);
    assertThat(cleared.freeTextNoticeText()).isEqualTo(Patch.clear());

    var set = read("{\"freeTextNoticeText\":\"Sem telefone\"}").toInput("app-1", "srv-1");
    assertThat(set.freeTextNoticeText()).isEqualTo(Patch.set("Sem telefone"));
  }

  @Test
  @DisplayName("Texto em branco ou longo demais é violação com a mensagem do value object")
  void texto_invalido() {
    assertThat(messagesOf(read("{\"freeTextNoticeText\":\"  \"}")))
        .contains("O texto do aviso não pode ser vazio");
    assertThat(messagesOf(read("{\"freeTextNoticeText\":\"" + "a".repeat(201) + "\"}")))
        .contains("O texto do aviso não pode passar de 200 caracteres");
  }

  @Test
  @DisplayName("Aviso ligado nulo, ou de tipo errado, é corpo malformado")
  void ligado_nulo() {
    assertThatThrownBy(() -> read("{\"freeTextNoticeEnabled\":null}"))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> read("{\"freeTextNoticeText\":10}"))
        .isInstanceOf(JacksonException.class);
  }
}

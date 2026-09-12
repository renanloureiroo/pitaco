package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DTOs de autoria avançada")
class AdvancedAuthoringRequestDTOsTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static <T> Map<String, String> violations(T dto) {
    return validator.validate(dto).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  @DisplayName("Criar aceita os três modelos e nenhum, e recusa modelo desconhecido")
  void modelo_na_criacao() {
    assertThat(violations(new CreateSurveyRequestDTO("NPS", "nps"))).isEmpty();
    assertThat(violations(new CreateSurveyRequestDTO("NPS"))).isEmpty();
    assertThat(violations(new CreateSurveyRequestDTO("NPS", "sus")))
        .containsEntry("template", "Modelo deve ser nps, csat ou ces");

    assertThat(new CreateSurveyRequestDTO("NPS", "csat").toInput("app").template())
        .contains(SurveyTemplate.CSAT);
    assertThat(new CreateSurveyRequestDTO("NPS").toInput("app").template()).isEmpty();
  }

  @Test
  @DisplayName("Duplicar aceita corpo vazio; nome, quando vem, não é branco nem passa de 120")
  void corpo_da_duplicacao() {
    assertThat(violations(DuplicateSurveyRequestDTO.empty())).isEmpty();
    assertThat(violations(new DuplicateSurveyRequestDTO(null, "x".repeat(120)))).isEmpty();
    assertThat(violations(new DuplicateSurveyRequestDTO(null, "   ")))
        .containsEntry("name", "Nome não pode ser vazio");
    assertThat(violations(new DuplicateSurveyRequestDTO(null, "x".repeat(121))))
        .containsEntry("name", "Nome não pode passar de 120 caracteres");

    var input = new DuplicateSurveyRequestDTO("  ", null).toInput("app", "survey");
    assertThat(input.targetApplicationId()).isEmpty();
    assertThat(input.name()).isEmpty();
    assertThat(new DuplicateSurveyRequestDTO("destino", "Nome").toInput("app", "survey").targetApplicationId())
        .contains("destino");
  }

  @Test
  @DisplayName("Condição exige origem e operador conhecido, validada dentro da pergunta")
  void condicao() {
    var invalid = new ConditionDTO(" ", "greater_than", List.of("1"), null, null);
    var question = new AddQuestionRequestDTO("Por quê?", "free_text", true, null, null, invalid);

    assertThat(violations(question))
        .containsEntry("condition.sourceKey", "Pergunta de origem é obrigatória")
        .containsEntry(
            "condition.operator", "Operador deve ser equals, not_equals, in ou between");
  }

  @Test
  void condicao_vira_entrada_do_caso_de_uso() {
    var input = new ConditionDTO("chave", "between", null, 0, 6).toInput();

    assertThat(input.operator()).isEqualTo(ConditionOperator.BETWEEN);
    assertThat(input.values()).isEmpty();
    assertThat(input.min()).contains(0);
    assertThat(input.max()).contains(6);
  }

  @Test
  @DisplayName("Rótulo da escala tem até 60 caracteres, e chega ao rascunho")
  void rotulos_da_escala() {
    var range = new ScaleRangeDTO(1, 5, "x".repeat(60), "x".repeat(61));
    var question = new AddQuestionRequestDTO("Avalie", "rating", true, null, range);

    assertThat(violations(question))
        .containsOnlyKeys("range.maxLabel")
        .containsEntry("range.maxLabel", "Rótulo da escala não pode passar de 60 caracteres");

    var draft =
        new AddQuestionRequestDTO("Avalie", "rating", true, null, new ScaleRangeDTO(1, 5, "Ruim", "Bom"))
            .toDraft();
    assertThat(draft.range()).hasValueSatisfying(value -> assertThat(value.minLabel()).contains("Ruim"));
  }
}

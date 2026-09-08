package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DTOs de entrada de pergunta")
class QuestionRequestDTOsTest {

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

  private static AddQuestionRequestDTO add(
      String statement,
      String type,
      Boolean required,
      List<QuestionOptionDTO> options,
      ScaleRangeDTO range) {
    return new AddQuestionRequestDTO(statement, type, required, options, range);
  }

  @Test
  void aceita_uma_pergunta_de_texto_livre() {
    assertThat(violationsOf(add("O que achou?", "free_text", true, null, null))).isEmpty();
  }

  @Test
  void aceita_uma_escolha_com_opcoes() {
    var options = List.of(new QuestionOptionDTO("Sim", "yes"), new QuestionOptionDTO("Não", "no"));

    assertThat(violationsOf(add("Recomendaria?", "single_choice", true, options, null))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("A mensagem espelha a do value object QuestionStatement")
  void recusa_enunciado_ausente(String invalid) {
    assertThat(violationsOf(add(invalid, "free_text", true, null, null)))
        .containsEntry("statement", "Enunciado é obrigatório");
  }

  @Test
  void recusa_enunciado_longo_demais() {
    assertThat(violationsOf(add("a".repeat(501), "free_text", true, null, null)))
        .containsEntry("statement", "Enunciado não pode passar de 500 caracteres");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SINGLE_CHOICE", "texto", "single_choice,nps"})
  void recusa_tipo_desconhecido(String type) {
    assertThat(violationsOf(add("Enunciado", type, true, null, null)))
        .containsEntry(
            "type",
            "Tipo deve ser single_choice, multiple_choice, rating, scale, nps ou free_text");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Tipo ausente ou em branco viola obrigatoriedade e formato ao mesmo tempo")
  void recusa_tipo_ausente(String type) {
    assertThat(violationsOf(add("Enunciado", type, true, null, null))).containsKey("type");
  }

  @Test
  void recusa_obrigatoriedade_ausente() {
    assertThat(violationsOf(add("Enunciado", "free_text", null, null, null)))
        .containsEntry("required", "Obrigatoriedade é obrigatória");
  }

  @Test
  void recusa_opcao_com_rotulo_ou_valor_vazio() {
    var semRotulo = List.of(new QuestionOptionDTO("  ", "yes"));
    var semValor = List.of(new QuestionOptionDTO("Sim", "  "));

    assertThat(violationsOf(add("Recomendaria?", "single_choice", true, semRotulo, null)))
        .containsEntry("options[0].label", "Rótulo da opção é obrigatório");
    assertThat(violationsOf(add("Recomendaria?", "single_choice", true, semValor, null)))
        .containsEntry("options[0].value", "Valor da opção é obrigatório");
  }

  @Test
  void recusa_opcao_longa_demais() {
    var rotuloLongo = List.of(new QuestionOptionDTO("a".repeat(201), "yes"));
    var valorLongo = List.of(new QuestionOptionDTO("Sim", "a".repeat(121)));

    assertThat(violationsOf(add("Recomendaria?", "single_choice", true, rotuloLongo, null)))
        .containsEntry("options[0].label", "Rótulo da opção não pode passar de 200 caracteres");
    assertThat(violationsOf(add("Recomendaria?", "single_choice", true, valorLongo, null)))
        .containsEntry("options[0].value", "Valor da opção não pode passar de 120 caracteres");
  }

  @Test
  void recusa_faixa_incompleta() {
    assertThat(violationsOf(add("Nota", "scale", true, null, new ScaleRangeDTO(null, 5))))
        .containsEntry("range.min", "Mínimo da faixa é obrigatório");
    assertThat(violationsOf(add("Nota", "scale", true, null, new ScaleRangeDTO(1, null))))
        .containsEntry("range.max", "Máximo da faixa é obrigatório");
  }

  @Test
  @DisplayName("toInput converte o tipo textual no enum de domínio e numera as opções")
  void converte_em_input() {
    var options = List.of(new QuestionOptionDTO("Sim", "yes"), new QuestionOptionDTO("Não", "no"));

    var input = add("Recomendaria?", "single_choice", true, options, null).toInput("app", "survey");

    assertThat(input.applicationId()).isEqualTo("app");
    assertThat(input.surveyId()).isEqualTo("survey");
    assertThat(input.draft().type()).isEqualTo(QuestionType.SINGLE_CHOICE);
    assertThat(input.draft().statement()).isEqualTo("Recomendaria?");
    assertThat(input.draft().required()).isTrue();
    assertThat(input.draft().options())
        .extracting(option -> option.value())
        .containsExactly("yes", "no");
    assertThat(input.draft().range()).isEmpty();
  }

  @Test
  void converte_a_faixa_quando_informada() {
    var input = add("Nota", "nps", true, null, new ScaleRangeDTO(0, 10)).toInput("app", "survey");

    assertThat(input.draft().range()).isPresent();
    assertThat(input.draft().range().orElseThrow().min()).isZero();
    assertThat(input.draft().range().orElseThrow().max()).isEqualTo(10);
  }

  @Test
  void a_reescrita_valida_as_mesmas_constraints() {
    assertThat(violationsOf(new UpdateQuestionRequestDTO("  ", "free_text", true, null, null)))
        .containsEntry("statement", "Enunciado é obrigatório");
    assertThat(violationsOf(new UpdateQuestionRequestDTO("Enunciado", "x", true, null, null)))
        .containsKey("type");
  }

  @Test
  void a_reescrita_converte_em_input_com_o_identificador_da_pergunta() {
    var input =
        new UpdateQuestionRequestDTO("Enunciado", "free_text", false, null, null)
            .toInput("app", "survey", "question");

    assertThat(input.questionId()).isEqualTo("question");
    assertThat(input.draft().required()).isFalse();
  }

  @Test
  void a_reordenacao_exige_uma_lista_nao_vazia() {
    assertThat(violationsOf(new ReorderQuestionsRequestDTO(List.of())))
        .containsEntry("questionIds", "A nova ordem é obrigatória");
    assertThat(violationsOf(new ReorderQuestionsRequestDTO(null)))
        .containsEntry("questionIds", "A nova ordem é obrigatória");
    assertThat(violationsOf(new ReorderQuestionsRequestDTO(List.of("a", "b")))).isEmpty();
  }

  @Test
  void a_reordenacao_converte_em_input() {
    var input = new ReorderQuestionsRequestDTO(List.of("a", "b")).toInput("app", "survey");

    assertThat(input.questionIds()).containsExactly("a", "b");
  }
}

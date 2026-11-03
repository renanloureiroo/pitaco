package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.RawAnswerValue;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
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

@DisplayName("SubmissionRequestDTO")
class SubmissionRequestDTOTest {

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

  private Map<String, String> violationsOf(SubmissionRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static AnswerDTO answer(Object value) {
    return new AnswerDTO(QuestionKey.generate().value(), AnswerStatus.ANSWERED, value);
  }

  @Test
  @DisplayName("O valor é aceito nas quatro formas")
  void aceita_as_quatro_formas_de_valor() {
    var request =
        new SubmissionRequestDTO(
            SubmissionRequestDTO.Outcome.COMPLETED,
            List.of(
                answer("bom"),
                answer(9),
                answer(List.of("a", "b")),
                new AnswerDTO(QuestionKey.generate().value(), AnswerStatus.SKIPPED, null)));

    assertThat(violationsOf(request)).isEmpty();
  }

  @Test
  void recusa_desfecho_ausente() {
    assertThat(violationsOf(new SubmissionRequestDTO(null, List.of())))
        .containsEntry("outcome", "Desfecho é obrigatório");
  }

  @Test
  void recusa_lista_de_respostas_nula() {
    assertThat(violationsOf(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, null)))
        .containsEntry("answers", "Lista de respostas é obrigatória");
  }

  @Test
  void recusa_chave_da_pergunta_ausente() {
    var request =
        new SubmissionRequestDTO(
            SubmissionRequestDTO.Outcome.DISMISSED,
            List.of(new AnswerDTO("  ", AnswerStatus.ANSWERED, "bom")));

    assertThat(violationsOf(request))
        .containsEntry("answers[0].questionKey", "Chave da pergunta é obrigatória");
  }

  @Test
  void recusa_situacao_ausente() {
    var request =
        new SubmissionRequestDTO(
            SubmissionRequestDTO.Outcome.DISMISSED,
            List.of(new AnswerDTO(QuestionKey.generate().value(), null, "bom")));

    assertThat(violationsOf(request))
        .containsEntry("answers[0].status", "Situação da resposta é obrigatória");
  }

  @Test
  @DisplayName("Forma de valor que não é texto, inteiro nem lista de textos é recusada")
  void recusa_forma_de_valor_nao_suportada() {
    var request =
        new SubmissionRequestDTO(
            SubmissionRequestDTO.Outcome.DISMISSED, List.of(answer(Map.of("a", "b"))));

    assertThat(violationsOf(request))
        .containsEntry(
            "answers[0].valueShapeSupported", "Valor deve ser texto, inteiro ou lista de textos");
  }

  @Test
  @DisplayName("Cada forma vira o rascunho correspondente")
  void converte_para_o_rascunho() {
    assertThat(answer("bom").toDraft().value())
        .contains(new RawAnswerValue.RawText("bom"));
    assertThat(answer(9).toDraft().value()).contains(new RawAnswerValue.RawNumber(9));
    assertThat(answer(List.of("a", "b")).toDraft().value())
        .contains(new RawAnswerValue.RawChoices(List.of("a", "b")));
    assertThat(
            new AnswerDTO(QuestionKey.generate().value(), AnswerStatus.SKIPPED, null)
                .toDraft()
                .value())
        .isEmpty();
  }
}

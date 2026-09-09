package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SubmissionValidation")
class SubmissionValidationTest {

  private static final QuestionKey TEXTO = QuestionKey.generate();
  private static final QuestionKey UNICA = QuestionKey.generate();
  private static final QuestionKey MULTIPLA = QuestionKey.generate();
  private static final QuestionKey NPS = QuestionKey.generate();

  private static final List<DeliverableQuestion> QUESTIONS =
      List.of(
          question(TEXTO, 1, QuestionType.FREE_TEXT, true, List.of(), Optional.empty()),
          question(
              UNICA,
              2,
              QuestionType.SINGLE_CHOICE,
              true,
              List.of(new QuestionOption("Bom", "bom", 1), new QuestionOption("Ruim", "ruim", 2)),
              Optional.empty()),
          question(
              MULTIPLA,
              3,
              QuestionType.MULTIPLE_CHOICE,
              false,
              List.of(
                  new QuestionOption("E-mail", "email", 1),
                  new QuestionOption("Telefone", "phone", 2)),
              Optional.empty()),
          question(NPS, 4, QuestionType.NPS, false, List.of(), Optional.of(new ScaleRange(0, 10))));

  private static DeliverableQuestion question(
      QuestionKey key,
      int position,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {
    return new DeliverableQuestion(key, position, "Pergunta " + position, type, required, options, range);
  }

  private static AnswerDraft answered(QuestionKey key, RawAnswerValue value) {
    return new AnswerDraft(key, AnswerStatus.ANSWERED, Optional.of(value));
  }

  private static AnswerDraft skipped(QuestionKey key) {
    return new AnswerDraft(key, AnswerStatus.SKIPPED, Optional.empty());
  }

  private static List<String> codesOf(DisplayOutcome outcome, List<AnswerDraft> answers) {
    return SubmissionValidation.check(QUESTIONS, outcome, answers).stream()
        .map(SubmissionProblem::code)
        .toList();
  }

  private static List<AnswerDraft> validCompletion() {
    return List.of(
        answered(TEXTO, new RawAnswerValue.RawText("achei o frete caro")),
        answered(UNICA, new RawAnswerValue.RawText("bom")),
        answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of("email", "phone"))),
        answered(NPS, new RawAnswerValue.RawNumber(9)));
  }

  @Test
  @DisplayName("Um envio válido não tem problema nenhum")
  void envio_valido_nao_tem_problema() {
    assertThat(SubmissionValidation.check(QUESTIONS, DisplayOutcome.COMPLETED, validCompletion()))
        .isEmpty();
  }

  @Test
  @DisplayName("Pergunta pulada é aceita nas opcionais")
  void pulada_em_opcional_e_aceita() {
    var answers =
        List.of(
            answered(TEXTO, new RawAnswerValue.RawText("ok")),
            answered(UNICA, new RawAnswerValue.RawText("bom")),
            skipped(MULTIPLA),
            skipped(NPS));

    assertThat(codesOf(DisplayOutcome.COMPLETED, answers)).isEmpty();
  }

  @Nested
  @DisplayName("Dispensa")
  class Dispensa {

    @Test
    @DisplayName("Obrigatória ausente não é problema quando o desfecho é dispensa (FR-037)")
    void obrigatoria_ausente_nao_e_problema_na_dispensa() {
      assertThat(codesOf(DisplayOutcome.DISMISSED, List.of())).isEmpty();
      assertThat(codesOf(DisplayOutcome.DISMISSED, List.of(skipped(TEXTO), skipped(UNICA))))
          .isEmpty();
    }

    @Test
    @DisplayName("O parcial é aceito como está")
    void parcial_e_aceito() {
      var parcial = List.of(answered(TEXTO, new RawAnswerValue.RawText("respondi só a primeira")));

      assertThat(codesOf(DisplayOutcome.DISMISSED, parcial)).isEmpty();
    }

    @Test
    @DisplayName("A dispensa não afrouxa o resto: o conteúdo continua sendo conferido")
    void dispensa_nao_afrouxa_o_resto() {
      assertThat(
              codesOf(
                  DisplayOutcome.DISMISSED,
                  List.of(answered(NPS, new RawAnswerValue.RawNumber(11)))))
          .containsExactly(SubmissionProblem.VALUE_OUT_OF_RANGE);
    }
  }

  @Nested
  @DisplayName("Os dez códigos")
  class Codigos {

    @Test
    void chave_que_nao_pertence_a_versao() {
      var intrusa = QuestionKey.generate();

      assertThat(
              SubmissionValidation.check(
                  QUESTIONS,
                  DisplayOutcome.DISMISSED,
                  List.of(answered(intrusa, new RawAnswerValue.RawText("x")))))
          .singleElement()
          .satisfies(
              problem -> {
                assertThat(problem.code()).isEqualTo(SubmissionProblem.QUESTION_UNKNOWN);
                assertThat(problem.questionKey()).contains(intrusa);
              });
    }

    @Test
    void chave_repetida_no_mesmo_envio() {
      var answers =
          List.of(
              answered(TEXTO, new RawAnswerValue.RawText("a")),
              answered(TEXTO, new RawAnswerValue.RawText("b")));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.QUESTION_DUPLICATED);
    }

    @Test
    @DisplayName("Obrigatória ausente ou pulada derruba a conclusão")
    void obrigatoria_ausente_na_conclusao() {
      assertThat(codesOf(DisplayOutcome.COMPLETED, List.of()))
          .containsExactly(
              SubmissionProblem.REQUIRED_MISSING, SubmissionProblem.REQUIRED_MISSING);

      var puladas = List.of(skipped(TEXTO), skipped(UNICA));

      assertThat(codesOf(DisplayOutcome.COMPLETED, puladas))
          .containsExactly(
              SubmissionProblem.REQUIRED_MISSING, SubmissionProblem.REQUIRED_MISSING);
    }

    @Test
    void respondida_sem_valor() {
      var answers = List.of(new AnswerDraft(TEXTO, AnswerStatus.ANSWERED, Optional.empty()));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.VALUE_MISSING);
    }

    @Test
    void forma_incompativel_com_o_tipo() {
      var answers = List.of(answered(NPS, new RawAnswerValue.RawText("nove")));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.VALUE_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("Pulada que ainda traz valor é forma incompatível")
    void pulada_com_valor() {
      var answers =
          List.of(
              new AnswerDraft(
                  TEXTO,
                  AnswerStatus.SKIPPED,
                  Optional.of(new RawAnswerValue.RawText("sobrou")))); 

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.VALUE_TYPE_MISMATCH);
    }

    @Test
    void opcao_nao_declarada() {
      var unica = List.of(answered(UNICA, new RawAnswerValue.RawText("mais_ou_menos")));
      var multipla =
          List.of(answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of("email", "pombo"))));

      assertThat(codesOf(DisplayOutcome.DISMISSED, unica))
          .containsExactly(SubmissionProblem.OPTION_UNKNOWN);
      assertThat(codesOf(DisplayOutcome.DISMISSED, multipla))
          .containsExactly(SubmissionProblem.OPTION_UNKNOWN);
    }

    @Test
    void escolha_multipla_vazia() {
      var answers = List.of(answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of())));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.OPTIONS_EMPTY);
    }

    @Test
    void opcao_repetida() {
      var answers =
          List.of(answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of("email", "email"))));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.OPTIONS_DUPLICATED);
    }

    @Test
    @DisplayName("Inteiro fora da faixa, nos dois limites")
    void inteiro_fora_da_faixa() {
      assertThat(
              codesOf(DisplayOutcome.DISMISSED, List.of(answered(NPS, new RawAnswerValue.RawNumber(11)))))
          .containsExactly(SubmissionProblem.VALUE_OUT_OF_RANGE);
      assertThat(
              codesOf(DisplayOutcome.DISMISSED, List.of(answered(NPS, new RawAnswerValue.RawNumber(-1)))))
          .containsExactly(SubmissionProblem.VALUE_OUT_OF_RANGE);
      assertThat(
              codesOf(DisplayOutcome.DISMISSED, List.of(answered(NPS, new RawAnswerValue.RawNumber(0)))))
          .isEmpty();
      assertThat(
              codesOf(DisplayOutcome.DISMISSED, List.of(answered(NPS, new RawAnswerValue.RawNumber(10)))))
          .isEmpty();
    }

    @Test
    void texto_acima_do_limite() {
      var answers = List.of(answered(TEXTO, new RawAnswerValue.RawText("a".repeat(2001))));

      assertThat(codesOf(DisplayOutcome.DISMISSED, answers))
          .containsExactly(SubmissionProblem.TEXT_TOO_LONG);
      assertThat(
              codesOf(
                  DisplayOutcome.DISMISSED,
                  List.of(answered(TEXTO, new RawAnswerValue.RawText("a".repeat(2000))))))
          .isEmpty();
    }
  }

  @Test
  @DisplayName("Todos os problemas saem de uma vez, na ordem documentada (FR-035, SC-007)")
  void todos_os_problemas_de_uma_vez() {
    var intrusa = QuestionKey.generate();

    var answers =
        List.of(
            answered(intrusa, new RawAnswerValue.RawText("x")),
            answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of("email", "email"))),
            answered(MULTIPLA, new RawAnswerValue.RawChoices(List.of("pombo"))),
            new AnswerDraft(UNICA, AnswerStatus.ANSWERED, Optional.empty()),
            answered(NPS, new RawAnswerValue.RawNumber(11)));

    var codes = codesOf(DisplayOutcome.COMPLETED, answers);

    assertThat(codes)
        .containsExactly(
            SubmissionProblem.QUESTION_UNKNOWN,
            SubmissionProblem.QUESTION_DUPLICATED,
            SubmissionProblem.REQUIRED_MISSING,
            SubmissionProblem.VALUE_MISSING,
            SubmissionProblem.OPTION_UNKNOWN,
            SubmissionProblem.OPTIONS_DUPLICATED,
            SubmissionProblem.VALUE_OUT_OF_RANGE);
  }
}

package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableCondition;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SubmissionValidation — não aplicável")
class SubmissionValidationNotApplicableTest {

  private static final QuestionKey ESCOLHA = QuestionKey.generate();
  private static final QuestionKey MOTIVO = QuestionKey.generate();
  private static final QuestionKey NPS = QuestionKey.generate();

  private static final List<DeliverableQuestion> QUESTIONS =
      List.of(
          new DeliverableQuestion(
              ESCOLHA,
              1,
              "Gostou?",
              QuestionType.SINGLE_CHOICE,
              true,
              List.of(new QuestionOption("Bom", "bom", 1), new QuestionOption("Ruim", "ruim", 2)),
              Optional.empty()),
          new DeliverableQuestion(
              MOTIVO,
              2,
              "Por quê?",
              QuestionType.FREE_TEXT,
              true,
              List.of(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(
                  new DeliverableCondition(
                      ESCOLHA, "equals", List.of("ruim"), Optional.empty(), Optional.empty()))),
          new DeliverableQuestion(
              NPS, 3, "De 0 a 10?", QuestionType.NPS, false, List.of(), Optional.of(ScaleRange.NPS)));

  private static AnswerDraft chose(String value) {
    return new AnswerDraft(
        ESCOLHA, AnswerStatus.ANSWERED, Optional.of(new RawAnswerValue.RawText(value)));
  }

  private static AnswerDraft notApplicable(QuestionKey key) {
    return new AnswerDraft(key, AnswerStatus.NOT_APPLICABLE, Optional.empty());
  }

  @Test
  @DisplayName("Obrigatória condicionada, pulada pela condição, não impede concluir")
  void nao_aplicavel_satisfaz_obrigatoria_condicionada() {
    var problems =
        SubmissionValidation.check(
            QUESTIONS, DisplayOutcome.COMPLETED, List.of(chose("bom"), notApplicable(MOTIVO)));

    assertThat(problems).isEmpty();
  }

  @Test
  @DisplayName("Não aplicável em pergunta sem condição é recusado, apontando a pergunta")
  void nao_aplicavel_sem_condicao_e_recusado() {
    var problems =
        SubmissionValidation.check(
            QUESTIONS,
            DisplayOutcome.COMPLETED,
            List.of(chose("ruim"), notApplicable(MOTIVO), notApplicable(NPS)));

    assertThat(problems)
        .containsExactly(
            SubmissionProblem.of(SubmissionProblem.NOT_APPLICABLE_UNCONDITIONAL, NPS));
  }

  @Test
  void nao_aplicavel_com_valor_e_forma_errada() {
    var problems =
        SubmissionValidation.check(
            QUESTIONS,
            DisplayOutcome.DISMISSED,
            List.of(
                new AnswerDraft(
                    MOTIVO,
                    AnswerStatus.NOT_APPLICABLE,
                    Optional.of(new RawAnswerValue.RawText("texto")))));

    assertThat(problems)
        .containsExactly(SubmissionProblem.of(SubmissionProblem.VALUE_TYPE_MISMATCH, MOTIVO));
  }

  @Test
  @DisplayName("Obrigatória condicionada que não veio de jeito nenhum continua faltando")
  void obrigatoria_condicionada_ausente_continua_faltando() {
    var problems =
        SubmissionValidation.check(QUESTIONS, DisplayOutcome.COMPLETED, List.of(chose("ruim")));

    assertThat(problems)
        .containsExactly(SubmissionProblem.of(SubmissionProblem.REQUIRED_MISSING, MOTIVO));
  }
}

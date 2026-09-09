package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Answer")
class AnswerTest {

  private static final Instant NOW = Instant.parse("2026-09-08T18:00:00Z");
  private static final DisplayId DISPLAY = DisplayId.of(UUID.randomUUID().toString());

  @Test
  void respondida_guarda_o_valor() {
    var answer =
        Answer.create(
            DISPLAY,
            QuestionKey.generate(),
            AnswerStatus.ANSWERED,
            Optional.of(new AnswerValue.NumericValue(9)),
            NOW);

    assertThat(answer.getStatus()).isEqualTo(AnswerStatus.ANSWERED);
    assertThat(answer.value()).contains(new AnswerValue.NumericValue(9));
  }

  @Test
  @DisplayName("Respondida exige valor")
  void respondida_sem_valor_e_recusada() {
    assertThatThrownBy(
            () ->
                Answer.create(
                    DISPLAY, QuestionKey.generate(), AnswerStatus.ANSWERED, Optional.empty(), NOW))
        .satisfies(AnswerTest::invalida);
  }

  @Test
  @DisplayName("Pulada exige ausência de valor")
  void pulada_com_valor_e_recusada() {
    assertThatThrownBy(
            () ->
                Answer.create(
                    DISPLAY,
                    QuestionKey.generate(),
                    AnswerStatus.SKIPPED,
                    Optional.of(new AnswerValue.NumericValue(1)),
                    NOW))
        .satisfies(AnswerTest::invalida);
  }

  @Test
  void pulada_sem_valor_e_aceita() {
    var answer =
        Answer.create(
            DISPLAY, QuestionKey.generate(), AnswerStatus.SKIPPED, Optional.empty(), NOW);

    assertThat(answer.value()).isEmpty();
  }

  @Test
  void chave_da_pergunta_e_obrigatoria() {
    assertThatThrownBy(
            () -> Answer.create(DISPLAY, null, AnswerStatus.SKIPPED, Optional.empty(), NOW))
        .satisfies(AnswerTest::invalida);
  }

  @Test
  void exibicao_e_obrigatoria() {
    assertThatThrownBy(
            () ->
                Answer.create(
                    null, QuestionKey.generate(), AnswerStatus.SKIPPED, Optional.empty(), NOW))
        .satisfies(AnswerTest::invalida);
  }

  private static void invalida(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    assertThat(((DomainException) error).code()).isEqualTo("answer.invalid");
  }
}

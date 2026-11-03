package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SurveyDisplay")
class SurveyDisplayTest {

  private static final Instant OPENED_AT = Instant.parse("2026-09-08T18:00:00Z");
  private static final Duration TIMEOUT = Duration.ofMinutes(30);

  private static SurveyDisplay started() {
    return SurveyDisplay.create(
        DisplayId.of(UUID.randomUUID().toString()),
        ApplicationId.generate(),
        RespondentId.generate(),
        SurveyId.generate(),
        SurveyVersionId.generate(),
        1,
        Optional.of("1.4.2"),
        AttributeSnapshot.empty(),
        OPENED_AT);
  }

  @Test
  void nasce_iniciada_e_sem_fechamento() {
    var display = started();

    assertThat(display.getOutcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(display.closedAt()).isEmpty();
  }

  @Test
  @DisplayName("Concluir a partir de iniciada grava o instante de fechamento")
  void concluir_fecha_a_exibicao() {
    var display = started();
    var closedAt = OPENED_AT.plusSeconds(120);

    display.complete(closedAt);

    assertThat(display.getOutcome()).isEqualTo(DisplayOutcome.COMPLETED);
    assertThat(display.closedAt()).contains(closedAt);
  }

  @Test
  void dispensar_fecha_a_exibicao() {
    var display = started();
    var closedAt = OPENED_AT.plusSeconds(30);

    display.dismiss(closedAt);

    assertThat(display.getOutcome()).isEqualTo(DisplayOutcome.DISMISSED);
    assertThat(display.closedAt()).contains(closedAt);
  }

  @Test
  @DisplayName("Exibição fechada recusa qualquer novo desfecho")
  void exibicao_fechada_recusa_novo_desfecho() {
    var completed = started();
    completed.complete(OPENED_AT.plusSeconds(10));

    assertThatThrownBy(() -> completed.dismiss(OPENED_AT.plusSeconds(20)))
        .satisfies(SurveyDisplayTest::jaFechada);
    assertThatThrownBy(() -> completed.complete(OPENED_AT.plusSeconds(20)))
        .satisfies(SurveyDisplayTest::jaFechada);

    var dismissed = started();
    dismissed.dismiss(OPENED_AT.plusSeconds(10));

    assertThatThrownBy(() -> dismissed.complete(OPENED_AT.plusSeconds(20)))
        .satisfies(SurveyDisplayTest::jaFechada);
  }

  @Test
  @DisplayName("Iniciada dentro do prazo continua iniciada")
  void iniciada_dentro_do_prazo() {
    assertThat(started().outcomeAt(OPENED_AT.plus(TIMEOUT).minusSeconds(1), TIMEOUT))
        .isEqualTo(DisplayOutcome.STARTED);
  }

  @Test
  @DisplayName("Iniciada vencida é abandonada, e o abandono nunca é gravado")
  void iniciada_vencida_e_abandonada() {
    var display = started();

    assertThat(display.outcomeAt(OPENED_AT.plus(TIMEOUT), TIMEOUT))
        .isEqualTo(DisplayOutcome.ABANDONED);
    assertThat(display.getOutcome()).isEqualTo(DisplayOutcome.STARTED);
  }

  @Test
  @DisplayName("Desfecho final nunca vira abandono, por mais antigo que seja")
  void desfecho_final_nunca_vira_abandono() {
    var completed = started();
    completed.complete(OPENED_AT.plusSeconds(1));
    var dismissed = started();
    dismissed.dismiss(OPENED_AT.plusSeconds(1));

    var muitoDepois = OPENED_AT.plus(Duration.ofDays(30));

    assertThat(completed.outcomeAt(muitoDepois, TIMEOUT)).isEqualTo(DisplayOutcome.COMPLETED);
    assertThat(dismissed.outcomeAt(muitoDepois, TIMEOUT)).isEqualTo(DisplayOutcome.DISMISSED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"nao-e-uuid", "3b1f0a2c", ""})
  void identificador_de_exibicao_precisa_ser_uuid(String invalid) {
    assertThatThrownBy(() -> DisplayId.of(invalid)).isInstanceOf(DomainException.class);
  }

  private static void jaFechada(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    assertThat(((DomainException) error).code()).isEqualTo("display.already_closed");
  }
}

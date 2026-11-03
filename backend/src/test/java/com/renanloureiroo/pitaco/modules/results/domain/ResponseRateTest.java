package com.renanloureiroo.pitaco.modules.results.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResponseRate e DisplayResolution")
class ResponseRateTest {

  private static final Instant CUTOFF = Instant.parse("2026-09-08T12:00:00Z");

  @Test
  @DisplayName("A taxa é concluídas sobre exibidas, e abandonadas e dispensadas ficam no denominador")
  void taxa_sobre_todas_as_exibicoes() {
    var rate = ResponseRate.of(10, 4, 3, 2, 1);

    assertThat(rate.rate()).hasValueSatisfying(value -> assertThat(value).isCloseTo(0.4, within(0.0001)));
    assertThat(ResponseRate.DEFINITION).contains("concluídas ÷ exibidas");
  }

  @Test
  @DisplayName("Sem exibição não há taxa, não zero")
  void sem_exibicao_nao_ha_taxa() {
    assertThat(ResponseRate.of(0, 0, 0, 0, 0).rate()).isEmpty();
  }

  @Test
  @DisplayName("Exibição iniciada antes do prazo é abandono; depois dele, ainda em andamento")
  void abandono_derivado_da_abertura() {
    assertThat(DisplayResolution.of("STARTED", CUTOFF.minusSeconds(1), CUTOFF))
        .isEqualTo(DisplayResolution.ABANDONED);
    assertThat(DisplayResolution.of("STARTED", CUTOFF, CUTOFF))
        .isEqualTo(DisplayResolution.IN_PROGRESS);
    assertThat(DisplayResolution.of("COMPLETED", CUTOFF.minusSeconds(1), CUTOFF))
        .isEqualTo(DisplayResolution.COMPLETED);
    assertThat(DisplayResolution.of("DISMISSED", CUTOFF, CUTOFF))
        .isEqualTo(DisplayResolution.DISMISSED);
  }
}

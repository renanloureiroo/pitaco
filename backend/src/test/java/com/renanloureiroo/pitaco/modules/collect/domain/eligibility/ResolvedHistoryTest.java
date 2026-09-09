package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository.DisplayHistoryEntry;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResolvedHistory")
class ResolvedHistoryTest {

  private static final SurveyId SURVEY = SurveyId.generate();
  private static final Instant NOW = Instant.parse("2026-09-08T18:00:00Z");
  private static final Duration TIMEOUT = Duration.ofMinutes(30);
  private static final int GROUP = 1;
  private static final int MAX_ATTEMPTS = 3;

  private static DisplayHistoryEntry entry(DisplayOutcome outcome, Instant openedAt) {
    return new DisplayHistoryEntry(SURVEY, GROUP, outcome, openedAt);
  }

  private static ResolvedHistory historyOf(DisplayHistoryEntry... entries) {
    return ResolvedHistory.of(List.of(entries), NOW, TIMEOUT, MAX_ATTEMPTS);
  }

  @Test
  @DisplayName("Sem histórico nada está resolvido nem esgotado")
  void sem_historico() {
    var history = historyOf();

    assertThat(history.isResolved(SURVEY, GROUP)).isFalse();
    assertThat(history.exhaustedAttempts(SURVEY, GROUP)).isFalse();
  }

  @Test
  @DisplayName("Concluída e dispensada resolvem")
  void concluida_e_dispensada_resolvem() {
    assertThat(
            historyOf(entry(DisplayOutcome.COMPLETED, NOW.minusSeconds(60)))
                .isResolved(SURVEY, GROUP))
        .isTrue();
    assertThat(
            historyOf(entry(DisplayOutcome.DISMISSED, NOW.minusSeconds(60)))
                .isResolved(SURVEY, GROUP))
        .isTrue();
  }

  @Test
  @DisplayName("Iniciada dentro do prazo não resolve nem conta tentativa")
  void iniciada_dentro_do_prazo() {
    var history = historyOf(entry(DisplayOutcome.STARTED, NOW.minus(TIMEOUT).plusSeconds(1)));

    assertThat(history.isResolved(SURVEY, GROUP)).isFalse();
    assertThat(history.exhaustedAttempts(SURVEY, GROUP)).isFalse();
  }

  @Test
  @DisplayName("Iniciada além do prazo é abandono: não resolve, mas conta tentativa")
  void iniciada_alem_do_prazo_e_abandono() {
    var abandonadas =
        new DisplayHistoryEntry[] {
          entry(DisplayOutcome.STARTED, NOW.minus(TIMEOUT)),
          entry(DisplayOutcome.STARTED, NOW.minus(TIMEOUT).minusSeconds(1)),
          entry(DisplayOutcome.STARTED, NOW.minus(TIMEOUT).minusSeconds(2))
        };

    assertThat(historyOf(abandonadas[0]).isResolved(SURVEY, GROUP)).isFalse();
    assertThat(historyOf(abandonadas[0], abandonadas[1]).exhaustedAttempts(SURVEY, GROUP))
        .isFalse();
    assertThat(historyOf(abandonadas).exhaustedAttempts(SURVEY, GROUP)).isTrue();
  }

  @Test
  @DisplayName("Atingido o limite, a pesquisa deixa de ser entregue")
  void limite_de_tentativas() {
    var history =
        ResolvedHistory.of(
            List.of(
                entry(DisplayOutcome.STARTED, NOW.minusSeconds(7200)),
                entry(DisplayOutcome.STARTED, NOW.minusSeconds(7300))),
            NOW,
            TIMEOUT,
            2);

    assertThat(history.exhaustedAttempts(SURVEY, GROUP)).isTrue();
  }

  @Test
  @DisplayName("Grupo de comparabilidade diferente não resolve nada")
  void grupo_diferente_nao_resolve() {
    var history = historyOf(entry(DisplayOutcome.COMPLETED, NOW.minusSeconds(60)));

    assertThat(history.isResolved(SURVEY, GROUP + 1)).isFalse();
    assertThat(history.exhaustedAttempts(SURVEY, GROUP + 1)).isFalse();
  }

  @Test
  @DisplayName("Pesquisa diferente não resolve nada")
  void pesquisa_diferente_nao_resolve() {
    var history = historyOf(entry(DisplayOutcome.COMPLETED, NOW.minusSeconds(60)));

    assertThat(history.isResolved(SurveyId.generate(), GROUP)).isFalse();
  }

  @Test
  @DisplayName("Abandono não resolve, por mais que se acumule")
  void abandono_nunca_resolve() {
    var history =
        historyOf(
            entry(DisplayOutcome.STARTED, NOW.minusSeconds(7200)),
            entry(DisplayOutcome.STARTED, NOW.minusSeconds(7300)),
            entry(DisplayOutcome.STARTED, NOW.minusSeconds(7400)));

    assertThat(history.isResolved(SURVEY, GROUP)).isFalse();
    assertThat(history.exhaustedAttempts(SURVEY, GROUP)).isTrue();
  }
}

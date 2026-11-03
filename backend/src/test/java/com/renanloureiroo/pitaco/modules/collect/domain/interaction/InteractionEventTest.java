package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionEvent")
class InteractionEventTest {

  private static final DisplayId DISPLAY = DisplayId.of(UUID.randomUUID().toString());
  private static final Instant AT = Instant.parse("2026-09-12T13:45:00Z");
  private static final QuestionKey KEY = QuestionKey.generate();

  private static InteractionEvent event(
      int seq, int catalogVersion, InteractionEventType type, Optional<QuestionKey> key, long elapsedMs) {
    return new InteractionEvent(
        DISPLAY, seq, catalogVersion, type, key, AT, elapsedMs, Map.of("length", 3, "text", "oi"), AT);
  }

  @Test
  @DisplayName("Nasce com o payload fechado pelo catálogo")
  void payload_fechado() {
    var created = event(1, 1, InteractionEventType.TEXT_EDITED, Optional.of(KEY), 0);

    assertThat(created.data()).containsExactly(Map.entry("length", 3L));
    assertThat(created.questionKey()).contains(KEY);
  }

  @Test
  @DisplayName("Seq começa em 1: zero recusa, um aceita")
  void seq() {
    assertThatThrownBy(() -> event(0, 1, InteractionEventType.SURVEY_BACKGROUNDED, Optional.empty(), 0))
        .isInstanceOf(DomainException.class)
        .extracting("type", "code")
        .containsExactly(ErrorType.VALIDATION, "interaction_event.invalid");
    assertThat(event(1, 1, InteractionEventType.SURVEY_BACKGROUNDED, Optional.empty(), 0).seq()).isOne();
  }

  @Test
  @DisplayName("Versão do catálogo começa em 1, e versão mais nova é aceita")
  void versao_do_catalogo() {
    assertThatThrownBy(() -> event(1, 0, InteractionEventType.SURVEY_BACKGROUNDED, Optional.empty(), 0))
        .isInstanceOf(DomainException.class);
    assertThat(event(1, 7, InteractionEventType.SURVEY_BACKGROUNDED, Optional.empty(), 0).catalogVersion())
        .isEqualTo(7);
  }

  @Test
  @DisplayName("Tempo desde a apresentação não é negativo; zero é o próprio survey_presented")
  void tempo_decorrido() {
    assertThatThrownBy(() -> event(1, 1, InteractionEventType.SURVEY_PRESENTED, Optional.empty(), -1))
        .isInstanceOf(DomainException.class);
    assertThat(event(1, 1, InteractionEventType.SURVEY_PRESENTED, Optional.empty(), 0).elapsedMs()).isZero();
  }

  @Test
  @DisplayName("Evento de pergunta exige a chave; evento de pesquisa descarta a que vier")
  void chave_da_pergunta() {
    assertThatThrownBy(() -> event(1, 1, InteractionEventType.QUESTION_VIEWED, Optional.empty(), 0))
        .isInstanceOf(DomainException.class);
    assertThat(event(1, 1, InteractionEventType.SURVEY_COMPLETED, Optional.of(KEY), 0).questionKey())
        .isEmpty();
  }

  @Test
  @DisplayName("Sem instante do dispositivo não há evento")
  void sem_instante() {
    assertThatThrownBy(
            () ->
                new InteractionEvent(
                    DISPLAY, 1, 1, InteractionEventType.SURVEY_BACKGROUNDED, Optional.empty(), null, 0, Map.of(), AT))
        .isInstanceOf(DomainException.class);
  }
}

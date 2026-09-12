package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ObservedEvent")
class ObservedEventTest {

  private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
  private static final EventName EVENT = EventName.of("checkout.completed");

  @Test
  @DisplayName("Nasce visto agora: primeira e última ocorrência são o mesmo instante")
  void nasce_com_os_dois_instantes_iguais() {
    var event = ObservedEvent.create(ApplicationId.generate(), EVENT, NOW);

    assertThat(event.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(event.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Ser visto de novo só avança a última ocorrência")
  void ser_visto_avanca_apenas_a_ultima_vez() {
    var event = ObservedEvent.create(ApplicationId.generate(), EVENT, NOW);
    var later = NOW.plusSeconds(3600);

    event.seenAt(later);

    assertThat(event.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(event.getLastSeenAt()).isEqualTo(later);
  }

  @Test
  @DisplayName("Uma ocorrência mais antiga que a última registrada não a recua")
  void ocorrencia_antiga_nao_recua() {
    var event = ObservedEvent.create(ApplicationId.generate(), EVENT, NOW);

    event.seenAt(NOW.minusSeconds(60));

    assertThat(event.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Restaurar com última ocorrência anterior à primeira é recusado")
  void restore_recusa_instantes_invertidos() {
    assertThatThrownBy(
            () ->
                ObservedEvent.restore(
                    ObservedEventId.generate(),
                    ApplicationId.generate(),
                    EVENT,
                    NOW,
                    NOW.minusSeconds(1)))
        .isInstanceOf(DomainException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("observed_event.invalid");
  }

  @Test
  @DisplayName("Restaurar não recalcula nada")
  void restore_devolve_o_que_veio_do_banco() {
    var id = ObservedEventId.generate();
    var applicationId = ApplicationId.generate();
    var lastSeen = NOW.plusSeconds(600);

    var event = ObservedEvent.restore(id, applicationId, EVENT, NOW, lastSeen);

    assertThat(event.id()).isEqualTo(id);
    assertThat(event.getApplicationId()).isEqualTo(applicationId);
    assertThat(event.getName()).isEqualTo(EVENT);
    assertThat(event.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(event.getLastSeenAt()).isEqualTo(lastSeen);
  }
}

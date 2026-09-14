package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsRequestDTO.InteractionEventDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionEventsRequestDTO")
class InteractionEventsRequestDTOTest {

  private static final Instant AT = Instant.parse("2026-09-12T13:45:00Z");

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

  private Map<String, String> violationsOf(InteractionEventsRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private static InteractionEventDTO anEvent(long seq) {
    return new InteractionEventDTO(1, "survey_backgrounded", null, seq, AT, 0L, null, Map.of());
  }

  private static List<InteractionEventDTO> events(int amount) {
    var list = new ArrayList<InteractionEventDTO>();
    for (var seq = 1; seq <= amount; seq++) {
      list.add(anEvent(seq));
    }
    return list;
  }

  @Test
  @DisplayName("De 1 a 100 eventos: um passa, cem passam, cento e um falha")
  void tamanho_do_lote() {
    assertThat(violationsOf(new InteractionEventsRequestDTO(events(1)))).isEmpty();
    assertThat(violationsOf(new InteractionEventsRequestDTO(events(100)))).isEmpty();
    assertThat(violationsOf(new InteractionEventsRequestDTO(events(101))))
        .containsEntry("events", "Lote deve ter de 1 a 100 eventos");
  }

  @Test
  @DisplayName("Lote vazio falha com a mesma mensagem do limite")
  void lote_vazio() {
    assertThat(violationsOf(new InteractionEventsRequestDTO(List.of())))
        .containsEntry("events", "Lote deve ter de 1 a 100 eventos");
  }

  @Test
  @DisplayName("Sem lote nenhum, a mensagem diz que ele é obrigatório")
  void sem_lote() {
    assertThat(violationsOf(new InteractionEventsRequestDTO(null)))
        .containsEntry("events", "Lote de eventos é obrigatório");
  }

  @Test
  @DisplayName("Evento com envelope incompleto não viola constraint: o destino dele é decidido depois")
  void envelope_incompleto_nao_e_400() {
    var incomplete = new InteractionEventDTO(null, "survey_teleported", null, null, null, null, null, "texto");

    assertThat(violationsOf(new InteractionEventsRequestDTO(List.of(incomplete)))).isEmpty();
  }

  @Test
  @DisplayName("toInput leva cada campo, trata data que não é objeto como vazio e evento nulo como vazio")
  void to_input() {
    var complete =
        new InteractionEventDTO(2, "answer_selected", "d-1", 3L, AT, 40L, "q-1", Map.of("value", "yes"));
    var textData = new InteractionEventDTO(1, "text_edited", null, 4L, AT, 50L, "q-1", "meu texto");

    var input =
        new InteractionEventsRequestDTO(new ArrayList<>(java.util.Arrays.asList(complete, textData, null)))
            .toInput("app-1", "display-1");

    assertThat(input.applicationId()).isEqualTo("app-1");
    assertThat(input.displayId()).isEqualTo("display-1");
    var first = input.events().get(0);
    assertThat(first.catalogVersion()).contains(2);
    assertThat(first.type()).contains("answer_selected");
    assertThat(first.displayId()).contains("d-1");
    assertThat(first.seq()).contains(3L);
    assertThat(first.occurredAt()).contains(AT);
    assertThat(first.elapsedMs()).contains(40L);
    assertThat(first.questionKey()).contains("q-1");
    assertThat(first.data()).containsEntry("value", "yes");
    assertThat(input.events().get(1).data()).isEqualTo(Collections.emptyMap());
    assertThat(input.events().get(2).type()).isEqualTo(Optional.empty());
  }
}

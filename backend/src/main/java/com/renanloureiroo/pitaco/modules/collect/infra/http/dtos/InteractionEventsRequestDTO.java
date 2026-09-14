package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordInteractionEventsUseCase;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionDraft;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Schema(description = "Um lote de eventos de interação de uma exibição, no catálogo do Pitaco")
public record InteractionEventsRequestDTO(
    @NotNull(message = "Lote de eventos é obrigatório")
        @Size(
            min = 1,
            max = RecordInteractionEventsUseCase.MAX_BATCH_SIZE,
            message =
                "Lote deve ter de 1 a " + RecordInteractionEventsUseCase.MAX_BATCH_SIZE + " eventos")
        @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "De 1 a 100 eventos, em qualquer ordem. Tipo fora do catálogo, envelope "
                            + "incompleto e campo extra não recusam o lote: o evento ou o campo "
                            + "é descartado",
                    requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(ref = "#/components/schemas/InteractionEvent"),
            minItems = 1,
            maxItems = RecordInteractionEventsUseCase.MAX_BATCH_SIZE)
        List<InteractionEventDTO> events) {

  // Lido sem constraint nenhuma: o que faltar aqui decide o destino do evento, não o do lote.
  @Schema(hidden = true)
  public record InteractionEventDTO(
      Integer catalogVersion,
      String type,
      String displayId,
      Long seq,
      Instant occurredAt,
      Long elapsedMs,
      String questionKey,
      Object data) {

    InteractionDraft toDraft() {
      return new InteractionDraft(
          Optional.ofNullable(catalogVersion),
          Optional.ofNullable(type),
          Optional.ofNullable(displayId),
          Optional.ofNullable(seq),
          Optional.ofNullable(occurredAt),
          Optional.ofNullable(elapsedMs),
          Optional.ofNullable(questionKey),
          dataOf(data));
    }

    // `data` que não é objeto não tem campo nenhum para aproveitar.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Object raw) {
      return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
  }

  public RecordInteractionEventsUseCase.Input toInput(String applicationId, String displayId) {
    return new RecordInteractionEventsUseCase.Input(
        applicationId,
        displayId,
        events.stream()
            .map(event -> event == null ? new InteractionEventDTO(null, null, null, null, null, null, null, null) : event)
            .map(InteractionEventDTO::toDraft)
            .toList());
  }
}

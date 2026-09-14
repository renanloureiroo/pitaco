package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

// O evento como chegou do dispositivo, antes de qualquer leitura: tudo pode faltar, e o que
// faltar decide o destino dele, não o do lote.
public record InteractionDraft(
    Optional<Integer> catalogVersion,
    Optional<String> type,
    Optional<String> displayId,
    Optional<Long> seq,
    Optional<Instant> occurredAt,
    Optional<Long> elapsedMs,
    Optional<String> questionKey,
    Map<String, Object> data) {

  public InteractionDraft {
    catalogVersion = catalogVersion == null ? Optional.empty() : catalogVersion;
    type = type == null ? Optional.empty() : type;
    displayId = displayId == null ? Optional.empty() : displayId;
    seq = seq == null ? Optional.empty() : seq;
    occurredAt = occurredAt == null ? Optional.empty() : occurredAt;
    elapsedMs = elapsedMs == null ? Optional.empty() : elapsedMs;
    questionKey = questionKey == null ? Optional.empty() : questionKey;
    data = data == null ? Map.of() : data;
  }
}

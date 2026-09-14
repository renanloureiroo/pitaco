package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

// Value object: a identidade é (exibição, seq), e o mesmo par reenviado é o mesmo evento. O
// `data` sai daqui sempre fechado pelo catálogo.
public record InteractionEvent(
    DisplayId displayId,
    int seq,
    int catalogVersion,
    InteractionEventType type,
    Optional<QuestionKey> questionKey,
    Instant occurredAt,
    long elapsedMs,
    Map<String, Object> data,
    Instant receivedAt) {

  private static final String INVALID_CODE = "interaction_event.invalid";

  public InteractionEvent {
    require(displayId != null, "Evento precisa de uma exibição");
    require(type != null, "Evento precisa de um tipo do catálogo");
    require(seq >= 1, "Sequência do evento começa em 1");
    require(catalogVersion >= 1, "Versão do catálogo começa em 1");
    require(occurredAt != null, "Evento precisa do instante em que aconteceu");
    require(elapsedMs >= 0, "Tempo desde a apresentação não pode ser negativo");
    require(receivedAt != null, "Evento precisa do instante de recebimento");

    questionKey = questionKey == null ? Optional.empty() : questionKey;
    require(
        !type.questionScoped() || questionKey.isPresent(),
        "Evento de pergunta precisa da chave da pergunta");
    if (!type.questionScoped()) {
      questionKey = Optional.empty();
    }

    data = type.sanitize(data);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, message);
    }
  }
}

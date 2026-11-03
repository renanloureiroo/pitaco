package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.time.Instant;
import java.util.Optional;

public record TriggerWindow(Instant start, Optional<Instant> end) {

  private static final String INVALID_CODE = "trigger.window_invalid";

  public TriggerWindow {
    if (start == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Início da janela é obrigatório");
    }
    if (end == null) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Fim da janela é inválido");
    }
    if (end.isPresent() && !end.get().isAfter(start)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Fim da janela deve ser posterior ao início");
    }
  }

  public static TriggerWindow of(Instant start, Instant end) {
    return new TriggerWindow(start, Optional.ofNullable(end));
  }

  // Início inclusivo: publicar no instante exato da abertura já deixa a pesquisa ativa.
  public boolean isOpenAt(Instant now) {
    return !now.isBefore(start) && !hasClosedAt(now);
  }

  public boolean hasOpenedAt(Instant now) {
    return !now.isBefore(start);
  }

  public boolean hasClosedAt(Instant now) {
    return end.map(closing -> !now.isBefore(closing)).orElse(false);
  }
}

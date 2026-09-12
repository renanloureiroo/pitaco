package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import lombok.Getter;

// Catálogo, não log: uma linha por nome de evento por aplicação, que só avança o último
// instante em que foi visto.
@Getter
public final class ObservedEvent extends Entity<ObservedEventId> {

  private static final String INVALID_CODE = "observed_event.invalid";

  private final ApplicationId applicationId;
  private final EventName name;
  private final Instant firstSeenAt;

  private Instant lastSeenAt;

  private ObservedEvent(
      ObservedEventId id,
      ApplicationId applicationId,
      EventName name,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    super(id);

    if (applicationId == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Evento observado precisa pertencer a uma aplicação");
    }
    if (name == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Evento observado precisa de nome");
    }
    if (firstSeenAt == null || lastSeenAt == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Evento observado precisa dos instantes de contato");
    }
    if (lastSeenAt.isBefore(firstSeenAt)) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Última ocorrência não pode ser anterior à primeira");
    }

    this.applicationId = applicationId;
    this.name = name;
    this.firstSeenAt = firstSeenAt;
    this.lastSeenAt = lastSeenAt;
  }

  public static ObservedEvent create(ApplicationId applicationId, EventName name, Instant now) {
    return new ObservedEvent(ObservedEventId.generate(), applicationId, name, now, now);
  }

  public static ObservedEvent restore(
      ObservedEventId id,
      ApplicationId applicationId,
      EventName name,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    return new ObservedEvent(id, applicationId, name, firstSeenAt, lastSeenAt);
  }

  public void seenAt(Instant now) {
    if (now.isAfter(lastSeenAt)) {
      this.lastSeenAt = now;
    }
  }
}

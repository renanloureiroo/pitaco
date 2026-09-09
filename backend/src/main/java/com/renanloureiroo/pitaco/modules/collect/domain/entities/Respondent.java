package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.time.Instant;
import lombok.Getter;

@Getter
public final class Respondent extends Entity<RespondentId> {

  private static final String INVALID_CODE = "respondent.invalid";

  private final ApplicationId applicationId;
  private final RespondentIdentity identity;
  private final Instant firstSeenAt;

  private Instant lastSeenAt;

  private Respondent(
      RespondentId id,
      ApplicationId applicationId,
      RespondentIdentity identity,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    super(id);

    if (applicationId == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Respondente precisa pertencer a uma aplicação");
    }
    if (identity == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Respondente precisa de identificação");
    }
    if (firstSeenAt == null || lastSeenAt == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Respondente precisa dos instantes de contato");
    }

    this.applicationId = applicationId;
    this.identity = identity;
    this.firstSeenAt = firstSeenAt;
    this.lastSeenAt = lastSeenAt;
  }

  public static Respondent create(
      ApplicationId applicationId, RespondentIdentity identity, Instant now) {
    return new Respondent(RespondentId.generate(), applicationId, identity, now, now);
  }

  public static Respondent restore(
      RespondentId id,
      ApplicationId applicationId,
      RespondentIdentity identity,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    return new Respondent(id, applicationId, identity, firstSeenAt, lastSeenAt);
  }

  public void seenAt(Instant now) {
    this.lastSeenAt = now;
  }
}

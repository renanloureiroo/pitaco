package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import java.time.Instant;

public record RespondentOutput(
    RespondentId id,
    RespondentIdentityKind identityKind,
    String identityValue,
    Instant firstSeenAt,
    Instant lastSeenAt) {

  public static RespondentOutput of(Respondent respondent) {
    return new RespondentOutput(
        respondent.id(),
        respondent.getIdentity().kind(),
        respondent.getIdentity().value(),
        respondent.getFirstSeenAt(),
        respondent.getLastSeenAt());
  }
}

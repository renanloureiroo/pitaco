package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import java.time.Instant;

public final class RespondentFactory {

  public static final String DEFAULT_REFERENCE = "u-8f1c";
  public static final Instant DEFAULT_FIRST_SEEN_AT = Instant.parse("2026-09-08T18:00:00Z");

  private ApplicationId applicationId = ApplicationId.generate();
  private RespondentIdentity identity =
      new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, DEFAULT_REFERENCE);
  private Instant firstSeenAt = DEFAULT_FIRST_SEEN_AT;
  private Instant lastSeenAt;

  private RespondentFactory() {}

  public static RespondentFactory aRespondent() {
    return new RespondentFactory();
  }

  public RespondentFactory forApplication(ApplicationId applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public RespondentFactory identifiedByReference(String reference) {
    this.identity = new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, reference);
    return this;
  }

  public RespondentFactory identifiedByDevice(String deviceId) {
    this.identity = new RespondentIdentity(RespondentIdentityKind.DEVICE, deviceId);
    return this;
  }

  public RespondentFactory firstSeenAt(Instant firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
    return this;
  }

  public RespondentFactory lastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
    return this;
  }

  public RespondentIdentity identity() {
    return identity;
  }

  public Respondent build() {
    var respondent = Respondent.create(applicationId, identity, firstSeenAt);
    if (lastSeenAt != null) {
      respondent.seenAt(lastSeenAt);
    }
    return respondent;
  }

  public Respondent buildSavedIn(RespondentRepository repository) {
    return repository.create(build());
  }
}

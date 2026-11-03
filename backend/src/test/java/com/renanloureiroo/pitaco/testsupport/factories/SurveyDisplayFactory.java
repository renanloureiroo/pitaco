package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SurveyDisplayFactory {

  public static final Instant DEFAULT_OPENED_AT = Instant.parse("2026-09-08T18:00:00Z");
  public static final int DEFAULT_COMPARABILITY_GROUP = 1;

  private DisplayId id = DisplayId.of(UUID.randomUUID().toString());
  private ApplicationId applicationId = ApplicationId.generate();
  private RespondentId respondentId = RespondentId.generate();
  private SurveyId surveyId = SurveyId.generate();
  private SurveyVersionId versionId = SurveyVersionId.generate();
  private int comparabilityGroup = DEFAULT_COMPARABILITY_GROUP;
  private Optional<String> sdkVersion = Optional.of("1.4.2");
  private AttributeSnapshot attributes = AttributeSnapshot.empty();
  private Instant openedAt = DEFAULT_OPENED_AT;
  private DisplayOutcome outcome = DisplayOutcome.STARTED;
  private Instant closedAt;

  private SurveyDisplayFactory() {}

  public static SurveyDisplayFactory aDisplay() {
    return new SurveyDisplayFactory();
  }

  public SurveyDisplayFactory withId(DisplayId id) {
    this.id = id;
    return this;
  }

  public SurveyDisplayFactory forApplication(ApplicationId applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public SurveyDisplayFactory forRespondent(RespondentId respondentId) {
    this.respondentId = respondentId;
    return this;
  }

  public SurveyDisplayFactory forSurvey(SurveyId surveyId) {
    this.surveyId = surveyId;
    return this;
  }

  public SurveyDisplayFactory forVersion(SurveyVersionId versionId) {
    this.versionId = versionId;
    return this;
  }

  public SurveyDisplayFactory inComparabilityGroup(int comparabilityGroup) {
    this.comparabilityGroup = comparabilityGroup;
    return this;
  }

  public SurveyDisplayFactory withoutSdkVersion() {
    this.sdkVersion = Optional.empty();
    return this;
  }

  public SurveyDisplayFactory withAttributes(Map<String, String> attributes) {
    this.attributes = AttributeSnapshot.of(attributes);
    return this;
  }

  public SurveyDisplayFactory openedAt(Instant openedAt) {
    this.openedAt = openedAt;
    return this;
  }

  public SurveyDisplayFactory completedAt(Instant closedAt) {
    this.outcome = DisplayOutcome.COMPLETED;
    this.closedAt = closedAt;
    return this;
  }

  public SurveyDisplayFactory dismissedAt(Instant closedAt) {
    this.outcome = DisplayOutcome.DISMISSED;
    this.closedAt = closedAt;
    return this;
  }

  public DisplayId id() {
    return id;
  }

  // Sempre restore: o desfecho e o instante de fechamento vêm controlados pelo cenário.
  public SurveyDisplay build() {
    return SurveyDisplay.restore(
        id,
        applicationId,
        respondentId,
        surveyId,
        versionId,
        comparabilityGroup,
        outcome,
        sdkVersion,
        attributes,
        openedAt,
        Optional.ofNullable(closedAt));
  }

  public SurveyDisplay buildSavedIn(SurveyDisplayRepository repository) {
    return repository.create(build());
  }
}

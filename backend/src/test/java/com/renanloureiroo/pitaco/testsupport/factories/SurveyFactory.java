package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SurveyFactory {

  public static final String DEFAULT_NAME = "NPS pós-checkout";

  // Instante fixo e afastado do relógio: a ordenação por createdAt precisa ser determinística.
  public static final Instant BATCH_FIRST_CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");

  private ApplicationId applicationId = ApplicationId.generate();
  private String name = DEFAULT_NAME;
  private SurveyLifecycle lifecycle = SurveyLifecycle.DRAFT;
  private Integer publishedVersionNumber;
  private Integer draftVersionNumber = 1;
  private Instant createdAt = BATCH_FIRST_CREATED_AT;

  private SurveyFactory() {}

  public static SurveyFactory aSurvey() {
    return new SurveyFactory();
  }

  public static SurveyFactory aPublishedSurvey() {
    return aSurvey().published(1);
  }

  public static SurveyFactory aPausedSurvey() {
    return aSurvey().published(1).inLifecycle(SurveyLifecycle.PAUSED);
  }

  public static SurveyFactory anEndedSurvey() {
    return aSurvey().published(1).inLifecycle(SurveyLifecycle.ENDED);
  }

  public SurveyFactory forApplication(ApplicationId applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public SurveyFactory withName(String name) {
    this.name = name;
    return this;
  }

  public SurveyFactory published(int versionNumber) {
    this.lifecycle = SurveyLifecycle.PUBLISHED;
    this.publishedVersionNumber = versionNumber;
    this.draftVersionNumber = null;
    return this;
  }

  public SurveyFactory withOpenDraft(int versionNumber) {
    this.draftVersionNumber = versionNumber;
    return this;
  }

  public SurveyFactory inLifecycle(SurveyLifecycle lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public SurveyFactory createdAt(Instant createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public Survey build() {
    return Survey.restore(
        SurveyId.generate(),
        applicationId,
        SurveyName.of(name),
        lifecycle,
        publishedVersionNumber,
        draftVersionNumber,
        createdAt);
  }

  public Survey buildSavedIn(SurveyRepository repository) {
    return repository.create(build());
  }

  public List<Survey> buildBatchSavedIn(SurveyRepository repository, int amount) {
    var created = new ArrayList<Survey>();
    for (var index = 0; index < amount; index++) {
      created.add(
          withName(DEFAULT_NAME + " " + (index + 1))
              .createdAt(BATCH_FIRST_CREATED_AT.plusSeconds(index))
              .buildSavedIn(repository));
    }
    return List.copyOf(created);
  }
}

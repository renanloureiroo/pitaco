package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import java.time.Instant;
import java.util.Optional;

public final class SurveyVersionFactory {

  private SurveyId surveyId = SurveyId.generate();
  private int number = 1;
  private boolean withQuestion = true;
  private boolean withTrigger = true;
  private TriggerFactory trigger = TriggerFactory.anOpenTrigger();
  private Instant publishedAt = Instant.parse("2026-09-01T10:00:00Z");

  private SurveyVersionFactory() {}

  public static SurveyVersionFactory aVersion() {
    return new SurveyVersionFactory();
  }

  /** Rascunho vazio: nem pergunta nem disparo, como nasce junto da pesquisa. */
  public static SurveyVersionFactory anEmptyDraft() {
    return aVersion().withoutQuestions().withoutTrigger();
  }

  /** Rascunho completo: uma pergunta e um disparo, pronto para publicar. */
  public static SurveyVersionFactory aPublishableDraft() {
    return aVersion();
  }

  public SurveyVersionFactory forSurvey(SurveyId surveyId) {
    this.surveyId = surveyId;
    return this;
  }

  public SurveyVersionFactory numbered(int number) {
    this.number = number;
    return this;
  }

  public SurveyVersionFactory withoutQuestions() {
    this.withQuestion = false;
    return this;
  }

  public SurveyVersionFactory withoutTrigger() {
    this.withTrigger = false;
    return this;
  }

  public SurveyVersionFactory triggeredBy(TriggerFactory trigger) {
    this.trigger = trigger;
    this.withTrigger = true;
    return this;
  }

  public SurveyVersionFactory publishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
    return this;
  }

  public SurveyVersion build() {
    var version = SurveyVersion.create(surveyId, number);

    if (withQuestion) {
      QuestionFactory.aFreeTextQuestion().buildAddedTo(version);
    }
    if (withTrigger) {
      version.defineTrigger(trigger.build());
    }

    return version;
  }

  public SurveyVersion buildPublished() {
    var version = build();

    return SurveyVersion.restore(
        version.id(),
        version.getSurveyId(),
        version.getNumber(),
        SurveyVersionStatus.PUBLISHED,
        version.getQuestions(),
        version.trigger(),
        version.getRules(),
        Optional.empty(),
        Optional.empty(),
        SurveyVersion.FIRST_COMPARABILITY_GROUP,
        Optional.of(publishedAt));
  }

  public SurveyVersion buildSavedIn(SurveyVersionRepository repository) {
    return repository.create(build());
  }

  public SurveyVersion buildPublishedSavedIn(SurveyVersionRepository repository) {
    return repository.create(buildPublished());
  }
}

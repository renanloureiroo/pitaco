package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SurveyVersionFactory {

  private SurveyId surveyId = SurveyId.generate();
  private int number = 1;
  private boolean withQuestion = true;
  private final List<QuestionFactory> questions = new ArrayList<>();
  private final List<SegmentationRule> rules = new ArrayList<>();
  private int comparabilityGroup = SurveyVersion.FIRST_COMPARABILITY_GROUP;
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

  public SurveyVersionFactory withQuestions(QuestionFactory... questions) {
    this.questions.addAll(List.of(questions));
    this.withQuestion = false;
    return this;
  }

  public SurveyVersionFactory ruledBy(SegmentationRule... rules) {
    this.rules.addAll(List.of(rules));
    return this;
  }

  public SurveyVersionFactory inComparabilityGroup(int comparabilityGroup) {
    this.comparabilityGroup = comparabilityGroup;
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
    questions.forEach(question -> question.buildAddedTo(version));

    if (withTrigger) {
      version.defineTrigger(trigger.build());
    }

    rules.forEach(version::addRule);

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
        comparabilityGroup,
        Optional.of(publishedAt));
  }

  public SurveyVersion buildSavedIn(SurveyVersionRepository repository) {
    return repository.create(build());
  }

  public SurveyVersion buildPublishedSavedIn(SurveyVersionRepository repository) {
    return repository.create(buildPublished());
  }
}

package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyVersionJpaEntity;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// O único ponto de contato entre `collect` e `survey`.
@Component
public class PublishedSurveyCatalogSurvey implements PublishedSurveyCatalog {

  private final PublishedSurveyJpaRepository repository;

  public PublishedSurveyCatalogSurvey(PublishedSurveyJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<SurveyCandidate> candidatesFor(
      ApplicationId applicationId, EventName event, Instant now) {
    return repository.findCandidates(applicationId.value(), event.value(), now).stream()
        .map(PublishedSurveyCatalogSurvey::candidateOf)
        .toList();
  }

  @Override
  public Optional<DeliverableSurvey> contentOf(SurveyVersionId versionId) {
    return repository.findPublishedContent(versionId.value()).map(
        version ->
            new DeliverableSurvey(
                SurveyId.of(version.getSurveyId()),
                SurveyVersionId.of(version.getId()),
                version.getNumber(),
                version.getQuestions().stream()
                    .sorted(Comparator.comparingInt(QuestionJpaEntity::getPosition))
                    .map(PublishedSurveyCatalogSurvey::questionOf)
                    .toList()));
  }

  @Override
  public Optional<PublishedVersion> publishedVersionOf(
      SurveyVersionId versionId, ApplicationId applicationId) {
    return repository
        .findPublishedVersion(versionId.value(), applicationId.value())
        .map(
            version ->
                new PublishedVersion(
                    SurveyId.of(version.getSurveyId()),
                    SurveyVersionId.of(version.getId()),
                    version.getNumber(),
                    version.getComparabilityGroup()));
  }

  private static SurveyCandidate candidateOf(SurveyVersionJpaEntity version) {
    return new SurveyCandidate(
        SurveyId.of(version.getSurveyId()),
        SurveyVersionId.of(version.getId()),
        version.getNumber(),
        version.getComparabilityGroup(),
        SamplingRate.of(version.getTriggerSamplingRate().doubleValue()),
        version.getRules().stream()
            .map(
                rule ->
                    new SegmentationCriterion(
                        rule.getAttribute(),
                        RuleOperation.valueOf(rule.getOperation()),
                        Optional.ofNullable(rule.getValue())))
            .toList(),
        version.getPublishedAt());
  }

  private static DeliverableQuestion questionOf(QuestionJpaEntity question) {
    return new DeliverableQuestion(
        QuestionKey.of(question.getQuestionKey()),
        question.getPosition(),
        question.getStatement(),
        QuestionType.valueOf(question.getType()),
        question.isRequired(),
        question.getOptions().stream()
            .map(option -> new QuestionOption(option.getLabel(), option.getValue(), option.getPosition()))
            .toList(),
        rangeOf(question));
  }

  private static Optional<ScaleRange> rangeOf(QuestionJpaEntity question) {
    return question.getRangeMin() == null || question.getRangeMax() == null
        ? Optional.empty()
        : Optional.of(new ScaleRange(question.getRangeMin(), question.getRangeMax()));
  }
}

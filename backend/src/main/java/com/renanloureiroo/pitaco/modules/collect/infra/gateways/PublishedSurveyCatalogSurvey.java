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
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableCondition;
import java.time.Instant;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    var versions = repository.findCandidates(applicationId.value(), event.value(), now);
    if (versions.isEmpty()) {
      return List.of();
    }

    var exposures = new java.util.HashMap<String, Object[]>();
    repository
        .findExposures(versions.stream().map(SurveyVersionJpaEntity::getSurveyId).toList())
        .forEach(row -> exposures.put((String) row[0], row));

    return versions.stream()
        .map(version -> candidateOf(version, exposures.get(version.getSurveyId())))
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
                    .toList(),
                noticeOf(version.getSurveyId())));
  }

  // O texto padrão mora na autoria; é aqui, na travessia, que ele é resolvido para o SDK.
  private Optional<String> noticeOf(String surveyId) {
    return repository.findFreeTextNotice(surveyId).stream()
        .findFirst()
        .map(row -> new FreeTextNotice((Boolean) row[0], Optional.ofNullable((String) row[1])))
        .filter(FreeTextNotice::enabled)
        .map(FreeTextNotice::text);
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

  @Override
  public Optional<CurrentPublication> currentPublicationOf(SurveyId surveyId) {
    return repository.findCurrentPublication(surveyId.value()).stream()
        .findFirst()
        .map(
            row ->
                new CurrentPublication(
                    SurveyVersionId.of((String) row[0]),
                    Optional.ofNullable((String) row[1]).map(EventName::of)));
  }

  private static SurveyCandidate candidateOf(SurveyVersionJpaEntity version, Object[] exposure) {
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
        version.getPublishedAt(),
        (Integer) exposure[1],
        (Boolean) exposure[2]);
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
        rangeOf(question),
        Optional.ofNullable(question.getRangeMinLabel()),
        Optional.ofNullable(question.getRangeMaxLabel()),
        conditionOf(question));
  }

  private static Optional<DeliverableCondition> conditionOf(QuestionJpaEntity question) {
    if (question.getConditionSourceKey() == null || question.getConditionOperator() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new DeliverableCondition(
            QuestionKey.of(question.getConditionSourceKey()),
            question.getConditionOperator().toLowerCase(Locale.ROOT),
            question.getConditionValues() == null
                ? List.of()
                : List.copyOf(question.getConditionValues()),
            Optional.ofNullable(question.getConditionMin()),
            Optional.ofNullable(question.getConditionMax())));
  }

  private static Optional<ScaleRange> rangeOf(QuestionJpaEntity question) {
    return question.getRangeMin() == null || question.getRangeMax() == null
        ? Optional.empty()
        : Optional.of(new ScaleRange(question.getRangeMin(), question.getRangeMax()));
  }
}

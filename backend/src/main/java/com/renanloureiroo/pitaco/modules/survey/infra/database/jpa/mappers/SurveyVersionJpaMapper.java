package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SegmentationRuleJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyVersionJpaEntity;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SurveyVersionJpaMapper {

  private static final int SAMPLING_RATE_SCALE = 4;

  private SurveyVersionJpaMapper() {}

  public static SurveyVersionJpaEntity toJpa(SurveyVersion version) {
    var trigger = version.trigger();

    return new SurveyVersionJpaEntity(
        version.id().value(),
        version.getSurveyId().value(),
        version.getNumber(),
        version.getStatus().name(),
        trigger.map(part -> part.event().value()).orElse(null),
        trigger.map(part -> part.window().start()).orElse(null),
        trigger.flatMap(part -> part.window().end()).orElse(null),
        trigger
            .map(part -> BigDecimal.valueOf(part.rate().value()).setScale(SAMPLING_RATE_SCALE))
            .orElse(null),
        version.changeKind().map(Enum::name).orElse(null),
        version.changeSummary().orElse(null),
        version.getComparabilityGroup(),
        version.publishedAt().orElse(null),
        version.getQuestions().stream()
            .map(QuestionJpaMapper::toJpa)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        version.getRules().stream()
            .map(SurveyVersionJpaMapper::toJpa)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private static SegmentationRuleJpaEntity toJpa(SegmentationRule rule) {
    return new SegmentationRuleJpaEntity(
        rule.id().value(), rule.attribute(), rule.operation().name(), rule.value().orElse(null));
  }

  public static SurveyVersion toDomain(SurveyVersionJpaEntity entity) {
    var questions =
        entity.getQuestions().stream()
            .sorted(Comparator.comparingInt(QuestionJpaEntity::getPosition))
            .map(QuestionJpaMapper::toDomain)
            .toList();

    var rules =
        entity.getRules().stream()
            .map(
                rule ->
                    new SegmentationRule(
                        SegmentationRuleId.of(rule.getId()),
                        rule.getAttribute(),
                        RuleOperation.valueOf(rule.getOperation()),
                        Optional.ofNullable(rule.getValue())))
            .toList();

    return SurveyVersion.restore(
        SurveyVersionId.of(entity.getId()),
        SurveyId.of(entity.getSurveyId()),
        entity.getNumber(),
        SurveyVersionStatus.valueOf(entity.getStatus()),
        questions,
        triggerOf(entity),
        rules,
        Optional.ofNullable(entity.getChangeKind()).map(ChangeKind::valueOf),
        Optional.ofNullable(entity.getChangeSummary()),
        entity.getComparabilityGroup(),
        Optional.ofNullable(entity.getPublishedAt()));
  }

  // Ou as três colunas obrigatórias do disparo estão preenchidas, ou o disparo não existe. O fim
  // da janela é a única parte legitimamente ausente: significa tempo indeterminado.
  private static Optional<Trigger> triggerOf(SurveyVersionJpaEntity entity) {
    if (entity.getTriggerEventName() == null
        || entity.getTriggerWindowStart() == null
        || entity.getTriggerSamplingRate() == null) {
      return Optional.empty();
    }

    return Optional.of(
        new Trigger(
            EventName.of(entity.getTriggerEventName()),
            new TriggerWindow(
                entity.getTriggerWindowStart(), Optional.ofNullable(entity.getTriggerWindowEnd())),
            SamplingRate.of(entity.getTriggerSamplingRate().doubleValue())));
  }
}

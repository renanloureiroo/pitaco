package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationWarning;
import java.util.List;
import java.util.Optional;

public record PublicationWarningOutput(
    String code,
    Optional<String> ruleId,
    Optional<String> attribute,
    List<CompetingSurveyOutput> competingSurveys,
    Optional<String> minRequiredVersion,
    Optional<Double> unsupportedShare) {

  public static List<PublicationWarningOutput> ofAll(List<PublicationWarning> warnings) {
    return warnings.stream()
        .map(
            warning ->
                new PublicationWarningOutput(
                    warning.code(),
                    warning.ruleId().map(SegmentationRuleId::value),
                    warning.attribute(),
                    warning.competingSurveys().stream().map(CompetingSurveyOutput::of).toList(),
                    warning.minRequiredVersion().map(SdkVersion::value),
                    warning.unsupportedShare()))
        .toList();
  }
}

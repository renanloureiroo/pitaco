package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record SurveyVersionOutput(
    SurveyVersionId id,
    int number,
    SurveyVersionStatus status,
    Optional<Instant> publishedAt,
    Optional<ChangeKind> changeKind,
    Optional<String> changeSummary,
    int comparabilityGroup) {

  public static SurveyVersionOutput of(SurveyVersion version) {
    return new SurveyVersionOutput(
        version.id(),
        version.getNumber(),
        version.getStatus(),
        version.publishedAt(),
        version.changeKind(),
        version.changeSummary(),
        version.getComparabilityGroup());
  }

  public static List<SurveyVersionOutput> ofAll(List<SurveyVersion> versions) {
    return versions.stream().map(SurveyVersionOutput::of).toList();
  }
}

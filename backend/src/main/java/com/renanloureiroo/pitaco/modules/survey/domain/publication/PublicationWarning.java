package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import java.util.List;
import java.util.Optional;

// Aviso, não impedimento: a publicação continua liberada, e o que ele faz é transformar uma
// escolha invisível em escolha consciente.
public record PublicationWarning(
    String code,
    Optional<SegmentationRuleId> ruleId,
    Optional<String> attribute,
    List<CompetingSurvey> competingSurveys,
    Optional<SdkVersion> minRequiredVersion,
    Optional<Double> unsupportedShare) {

  public static final String NO_KNOWN_MATCH = "segmentation.no_known_match";
  public static final String CONTRADICTORY_RULES = "segmentation.contradictory";
  public static final String COMPETING_SURVEYS = "trigger.competing_surveys";
  public static final String UNSUPPORTED_BY_MAJORITY = "compatibility.unsupported_by_majority";

  public PublicationWarning {
    competingSurveys = List.copyOf(competingSurveys);
    minRequiredVersion = minRequiredVersion == null ? Optional.empty() : minRequiredVersion;
    unsupportedShare = unsupportedShare == null ? Optional.empty() : unsupportedShare;
  }

  public PublicationWarning(
      String code,
      Optional<SegmentationRuleId> ruleId,
      Optional<String> attribute,
      List<CompetingSurvey> competingSurveys) {
    this(code, ruleId, attribute, competingSurveys, Optional.empty(), Optional.empty());
  }

  public static PublicationWarning noKnownMatch(SegmentationRuleId ruleId, String attribute) {
    return new PublicationWarning(
        NO_KNOWN_MATCH, Optional.of(ruleId), Optional.of(attribute), List.of());
  }

  public static PublicationWarning contradictory(String attribute) {
    return new PublicationWarning(
        CONTRADICTORY_RULES, Optional.empty(), Optional.of(attribute), List.of());
  }

  public static PublicationWarning competing(List<CompetingSurvey> surveys) {
    return new PublicationWarning(COMPETING_SURVEYS, Optional.empty(), Optional.empty(), surveys);
  }

  public static PublicationWarning unsupportedByMajority(
      SdkVersion minRequiredVersion, double unsupportedShare) {
    return new PublicationWarning(
        UNSUPPORTED_BY_MAJORITY,
        Optional.empty(),
        Optional.empty(),
        List.of(),
        Optional.of(minRequiredVersion),
        Optional.of(unsupportedShare));
  }
}

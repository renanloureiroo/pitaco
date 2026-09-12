package com.renanloureiroo.pitaco.modules.collect.domain.health;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

// Uma pesquisa elegível que o SDK não soube desenhar. Não é exibição: não conta na taxa de
// resposta, e é justamente por isso que precisa de registro próprio.
@Getter
public final class SuppressionEvent extends Entity<SuppressionEventId> {

  private static final String INVALID_CODE = "suppression.invalid";

  private final ApplicationId applicationId;
  private final SurveyId surveyId;
  private final SurveyVersionId versionId;
  private final RespondentId respondentId;
  private final SuppressionDedupKey dedupKey;
  private final SdkVersion sdkVersion;
  private final SuppressionReason reason;
  private final SdkVersion minRequiredVersion;
  private final Instant occurredAt;

  private SuppressionEvent(
      SuppressionEventId id,
      ApplicationId applicationId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      Optional<RespondentId> respondentId,
      Optional<SuppressionDedupKey> dedupKey,
      Optional<SdkVersion> sdkVersion,
      SuppressionReason reason,
      SdkVersion minRequiredVersion,
      Instant occurredAt) {
    super(id);

    if (applicationId == null || surveyId == null || versionId == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Supressão precisa de aplicação, pesquisa e versão");
    }
    if (reason == null || minRequiredVersion == null || occurredAt == null) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Supressão precisa de motivo, versão mínima e instante");
    }

    this.applicationId = applicationId;
    this.surveyId = surveyId;
    this.versionId = versionId;
    this.respondentId = respondentId.orElse(null);
    this.dedupKey = dedupKey.orElse(null);
    this.sdkVersion = sdkVersion.orElse(null);
    this.reason = reason;
    this.minRequiredVersion = minRequiredVersion;
    this.occurredAt = occurredAt;
  }

  public static SuppressionEvent create(
      ApplicationId applicationId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      Optional<RespondentId> respondentId,
      Optional<SuppressionDedupKey> dedupKey,
      Optional<SdkVersion> sdkVersion,
      SuppressionReason reason,
      SdkVersion minRequiredVersion,
      Instant now) {
    return new SuppressionEvent(
        SuppressionEventId.generate(),
        applicationId,
        surveyId,
        versionId,
        respondentId,
        dedupKey,
        sdkVersion,
        reason,
        minRequiredVersion,
        now);
  }

  public static SuppressionEvent restore(
      SuppressionEventId id,
      ApplicationId applicationId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      Optional<RespondentId> respondentId,
      Optional<SuppressionDedupKey> dedupKey,
      Optional<SdkVersion> sdkVersion,
      SuppressionReason reason,
      SdkVersion minRequiredVersion,
      Instant occurredAt) {
    return new SuppressionEvent(
        id,
        applicationId,
        surveyId,
        versionId,
        respondentId,
        dedupKey,
        sdkVersion,
        reason,
        minRequiredVersion,
        occurredAt);
  }

  public Optional<RespondentId> respondentId() {
    return Optional.ofNullable(respondentId);
  }

  public Optional<SuppressionDedupKey> dedupKey() {
    return Optional.ofNullable(dedupKey);
  }

  public Optional<SdkVersion> sdkVersion() {
    return Optional.ofNullable(sdkVersion);
  }
}

package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class SurveyDisplay extends Entity<DisplayId> {

  public static final int MAX_SDK_VERSION_LENGTH = 40;

  private static final String INVALID_CODE = "display.invalid";
  private static final String ALREADY_CLOSED_CODE = "display.already_closed";

  private final ApplicationId applicationId;
  private final RespondentId respondentId;
  private final SurveyId surveyId;
  private final SurveyVersionId versionId;
  private final int comparabilityGroup;
  private final String sdkVersion;
  private final AttributeSnapshot attributes;
  private final Instant openedAt;

  private DisplayOutcome outcome;
  private Instant closedAt;

  private SurveyDisplay(
      DisplayId id,
      ApplicationId applicationId,
      RespondentId respondentId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      int comparabilityGroup,
      DisplayOutcome outcome,
      Optional<String> sdkVersion,
      AttributeSnapshot attributes,
      Instant openedAt,
      Optional<Instant> closedAt) {
    super(id);

    require(applicationId != null, "Exibição precisa pertencer a uma aplicação");
    require(respondentId != null, "Exibição precisa de um respondente");
    require(surveyId != null, "Exibição precisa de uma pesquisa");
    require(versionId != null, "Exibição precisa da versão exibida");
    require(attributes != null, "Exibição precisa do instantâneo de atributos");
    require(openedAt != null, "Exibição precisa do instante de abertura");
    require(outcome != null, "Exibição precisa de um desfecho");
    require(outcome != DisplayOutcome.ABANDONED, "Abandono não é desfecho gravável");
    require(
        outcome.isFinal() == closedAt.isPresent(),
        "O fechamento existe se e somente se o desfecho é final");

    var version = sdkVersion.map(String::strip).filter(text -> !text.isEmpty());
    require(
        version.map(text -> text.length() <= MAX_SDK_VERSION_LENGTH).orElse(true),
        "Versão do SDK não pode passar de " + MAX_SDK_VERSION_LENGTH + " caracteres");

    this.applicationId = applicationId;
    this.respondentId = respondentId;
    this.surveyId = surveyId;
    this.versionId = versionId;
    this.comparabilityGroup = comparabilityGroup;
    this.outcome = outcome;
    this.sdkVersion = version.orElse(null);
    this.attributes = attributes;
    this.openedAt = openedAt;
    this.closedAt = closedAt.orElse(null);
  }

  public static SurveyDisplay create(
      DisplayId id,
      ApplicationId applicationId,
      RespondentId respondentId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      int comparabilityGroup,
      Optional<String> sdkVersion,
      AttributeSnapshot attributes,
      Instant openedAt) {
    return new SurveyDisplay(
        id,
        applicationId,
        respondentId,
        surveyId,
        versionId,
        comparabilityGroup,
        DisplayOutcome.STARTED,
        sdkVersion,
        attributes,
        openedAt,
        Optional.empty());
  }

  public static SurveyDisplay restore(
      DisplayId id,
      ApplicationId applicationId,
      RespondentId respondentId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      int comparabilityGroup,
      DisplayOutcome outcome,
      Optional<String> sdkVersion,
      AttributeSnapshot attributes,
      Instant openedAt,
      Optional<Instant> closedAt) {
    return new SurveyDisplay(
        id,
        applicationId,
        respondentId,
        surveyId,
        versionId,
        comparabilityGroup,
        outcome,
        sdkVersion,
        attributes,
        openedAt,
        closedAt);
  }

  public Optional<String> sdkVersion() {
    return Optional.ofNullable(sdkVersion);
  }

  public Optional<Instant> closedAt() {
    return Optional.ofNullable(closedAt);
  }

  public boolean isClosed() {
    return outcome.isFinal();
  }

  public void complete(Instant now) {
    close(DisplayOutcome.COMPLETED, now);
  }

  public void dismiss(Instant now) {
    close(DisplayOutcome.DISMISSED, now);
  }

  // Derivado, nunca gravado: uma coluna atualizada por job mentiria entre o vencimento do prazo
  // e a execução dele (D-11).
  public DisplayOutcome outcomeAt(Instant now, Duration timeout) {
    if (outcome != DisplayOutcome.STARTED) {
      return outcome;
    }
    return now.isBefore(openedAt.plus(timeout)) ? DisplayOutcome.STARTED : DisplayOutcome.ABANDONED;
  }

  private void close(DisplayOutcome finalOutcome, Instant now) {
    if (isClosed()) {
      throw new DomainException(
          ErrorType.CONFLICT, ALREADY_CLOSED_CODE, "Esta exibição já foi encerrada");
    }
    this.outcome = finalOutcome;
    this.closedAt = now;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, message);
    }
  }
}

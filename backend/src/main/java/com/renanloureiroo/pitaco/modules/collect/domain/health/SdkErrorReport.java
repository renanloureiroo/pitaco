package com.renanloureiroo.pitaco.modules.collect.domain.health;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

// Falha do próprio SDK, reportada pelo canal do Pitaco e nunca pelo do app hospedeiro.
@Getter
public final class SdkErrorReport extends Entity<SdkErrorReportId> {

  public static final int MAX_MESSAGE_LENGTH = 500;

  // O relógio do dispositivo não é confiável: um instante no futuro vira o do recebimento.
  static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

  private static final String INVALID_CODE = "sdk_error.invalid";

  private final ApplicationId applicationId;
  private final SdkVersion sdkVersion;
  private final SdkErrorKind kind;
  private final String message;
  private final SdkErrorContext context;
  private final Instant occurredAt;
  private final Instant receivedAt;

  private SdkErrorReport(
      SdkErrorReportId id,
      ApplicationId applicationId,
      Optional<SdkVersion> sdkVersion,
      SdkErrorKind kind,
      String message,
      SdkErrorContext context,
      Instant occurredAt,
      Instant receivedAt) {
    super(id);

    if (applicationId == null || kind == null || occurredAt == null || receivedAt == null) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Relatório de erro precisa de aplicação, tipo e instantes");
    }

    this.applicationId = applicationId;
    this.sdkVersion = sdkVersion.orElse(null);
    this.kind = kind;
    this.message = message == null ? "" : message;
    this.context = context == null ? SdkErrorContext.empty() : context;
    this.occurredAt = occurredAt;
    this.receivedAt = receivedAt;
  }

  public static SdkErrorReport create(
      ApplicationId applicationId,
      Optional<SdkVersion> sdkVersion,
      SdkErrorKind kind,
      String rawMessage,
      SdkErrorContext context,
      Optional<Instant> reportedOccurredAt,
      Instant now) {
    var occurredAt =
        reportedOccurredAt.filter(instant -> !instant.isAfter(now.plus(CLOCK_TOLERANCE))).orElse(now);

    return new SdkErrorReport(
        SdkErrorReportId.generate(),
        applicationId,
        sdkVersion,
        kind,
        cleanMessage(rawMessage),
        context,
        occurredAt,
        now);
  }

  public static SdkErrorReport restore(
      SdkErrorReportId id,
      ApplicationId applicationId,
      Optional<SdkVersion> sdkVersion,
      SdkErrorKind kind,
      String message,
      SdkErrorContext context,
      Instant occurredAt,
      Instant receivedAt) {
    return new SdkErrorReport(
        id, applicationId, sdkVersion, kind, message, context, occurredAt, receivedAt);
  }

  public Optional<SdkVersion> sdkVersion() {
    return Optional.ofNullable(sdkVersion);
  }

  // Controle vira espaço para que a mensagem não quebre a tabela nem o log de quem a ler.
  private static String cleanMessage(String raw) {
    if (raw == null) {
      return "";
    }

    var flattened = raw.replaceAll("\\p{Cntrl}+", " ").strip();
    var masked = PersonalDataMask.mask(flattened);

    return masked.length() > MAX_MESSAGE_LENGTH
        ? masked.substring(0, MAX_MESSAGE_LENGTH)
        : masked;
  }
}

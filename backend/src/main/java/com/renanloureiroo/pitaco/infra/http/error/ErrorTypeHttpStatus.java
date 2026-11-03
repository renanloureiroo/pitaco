package com.renanloureiroo.pitaco.infra.http.error;

import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tradução de {@link ErrorType} para status HTTP.
 *
 * <p>Vive na borda HTTP porque status é detalhe de protocolo: o core descreve a natureza do erro e
 * cada adapter decide como representá-la.
 */
final class ErrorTypeHttpStatus {

  private static final Map<ErrorType, HttpStatus> STATUSES =
      new EnumMap<>(
          Map.of(
              ErrorType.NOT_FOUND, HttpStatus.NOT_FOUND,
              ErrorType.CONFLICT, HttpStatus.CONFLICT,
              ErrorType.VALIDATION, HttpStatus.BAD_REQUEST,
              ErrorType.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
              ErrorType.FORBIDDEN, HttpStatus.FORBIDDEN,
              ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_CONTENT,
              ErrorType.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS));

  private ErrorTypeHttpStatus() {}

  static HttpStatus of(ErrorType type) {
    return STATUSES.getOrDefault(type, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}

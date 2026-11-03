package com.renanloureiroo.pitaco.core.error;

/** Identidade do solicitante ausente ou não comprovada. */
public class UnauthorizedException extends ApplicationException {

  public UnauthorizedException(String code, String message) {
    super(ErrorType.UNAUTHORIZED, code, message);
  }

  public UnauthorizedException(String code, String message, Throwable cause) {
    super(ErrorType.UNAUTHORIZED, code, message, cause);
  }
}

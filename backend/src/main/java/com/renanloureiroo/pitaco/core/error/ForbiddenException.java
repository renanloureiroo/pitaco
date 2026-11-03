package com.renanloureiroo.pitaco.core.error;

/** Identidade conhecida, porém sem permissão para a operação. */
public class ForbiddenException extends ApplicationException {

  public ForbiddenException(String code, String message) {
    super(ErrorType.FORBIDDEN, code, message);
  }

  public ForbiddenException(String code, String message, Throwable cause) {
    super(ErrorType.FORBIDDEN, code, message, cause);
  }
}

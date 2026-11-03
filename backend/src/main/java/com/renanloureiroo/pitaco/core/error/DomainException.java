package com.renanloureiroo.pitaco.core.error;

/**
 * Invariante de entidade / value object ou regra de negócio violada.
 *
 * <p>Lançada de dentro do domínio, onde a regra vive — não do caso de uso. Pode ser usada
 * diretamente ou estendida por erros nomeados de cada agregado.
 */
public class DomainException extends ApplicationException {

  public DomainException(String code, String message) {
    super(ErrorType.BUSINESS_RULE, code, message);
  }

  public DomainException(ErrorType type, String code, String message) {
    super(type, code, message);
  }

  public DomainException(ErrorType type, String code, String message, Throwable cause) {
    super(type, code, message, cause);
  }
}

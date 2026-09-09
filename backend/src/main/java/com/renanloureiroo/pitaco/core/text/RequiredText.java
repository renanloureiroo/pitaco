package com.renanloureiroo.pitaco.core.text;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

// A invariante que sete value objects de três módulos repetiam: obrigatório, sem espaço nas
// pontas, dentro de um limite. O tipo, o `code` e o assunto continuam de quem chama — o que sobe
// para o core é a regra, não o vocabulário.
public final class RequiredText {

  private RequiredText() {}

  public static String of(String value, int maxLength, String code, String subject) {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, code, subject + " é obrigatório");
    }

    var stripped = value.strip();
    if (stripped.length() > maxLength) {
      throw new DomainException(
          ErrorType.VALIDATION,
          code,
          subject + " não pode passar de " + maxLength + " caracteres");
    }

    return stripped;
  }

  // Mesma regra para o texto que pode faltar por inteiro, mas não pode chegar em branco.
  public static String optional(String value, int maxLength, String code, String subject) {
    return value == null ? null : of(value, maxLength, code, subject);
  }
}

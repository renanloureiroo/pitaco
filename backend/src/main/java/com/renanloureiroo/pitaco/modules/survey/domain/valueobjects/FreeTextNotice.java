package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Optional;

// O lembrete junto do campo de texto livre. Curto e humano de propósito: aviso longo não é lido, e
// aviso jurídico assusta quem ia responder. Ligado por padrão; texto ausente é o padrão.
public record FreeTextNotice(boolean enabled, Optional<String> customText) {

  public static final int MAX_TEXT_LENGTH = 200;
  public static final String DEFAULT_TEXT =
      "Evite escrever dados pessoais, como nome, telefone ou e-mail.";

  private static final String TEXT_INVALID_CODE = "survey.free_text_notice_invalid";

  public FreeTextNotice {
    customText = customText == null ? Optional.empty() : customText.map(String::strip);

    if (customText.filter(String::isEmpty).isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION, TEXT_INVALID_CODE, "O texto do aviso não pode ser vazio");
    }
    if (customText.filter(text -> text.length() > MAX_TEXT_LENGTH).isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          TEXT_INVALID_CODE,
          "O texto do aviso não pode passar de " + MAX_TEXT_LENGTH + " caracteres");
    }
  }

  public static FreeTextNotice standard() {
    return new FreeTextNotice(true, Optional.empty());
  }

  public String text() {
    return customText.orElse(DEFAULT_TEXT);
  }

  public FreeTextNotice enabled(boolean value) {
    return new FreeTextNotice(value, customText);
  }

  public FreeTextNotice withText(String text) {
    return new FreeTextNotice(enabled, Optional.of(text));
  }

  public FreeTextNotice withDefaultText() {
    return new FreeTextNotice(enabled, Optional.empty());
  }
}

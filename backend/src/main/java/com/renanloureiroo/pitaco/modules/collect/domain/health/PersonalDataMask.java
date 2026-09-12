package com.renanloureiroo.pitaco.modules.collect.domain.health;

import java.util.regex.Pattern;

// Rede de segurança, não garantia: o contrato já pede que o SDK não mande dado de usuário, e isto
// só apaga os dois formatos que mais escapam por acidente numa mensagem de erro — e-mail e
// sequência longa de dígitos (telefone, CPF, cartão).
final class PersonalDataMask {

  static final String EMAIL_MASK = "[email]";
  static final String NUMBER_MASK = "[número]";

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern LONG_NUMBER = Pattern.compile("\\d(?:[\\d .()/-]*\\d){7,}");

  private PersonalDataMask() {}

  static String mask(String text) {
    var withoutEmail = EMAIL.matcher(text).replaceAll(EMAIL_MASK);
    return LONG_NUMBER.matcher(withoutEmail).replaceAll(NUMBER_MASK);
  }

  static boolean looksPersonal(String text) {
    return EMAIL.matcher(text).find() || LONG_NUMBER.matcher(text).find();
  }
}

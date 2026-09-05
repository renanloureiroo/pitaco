package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identificador público e imutável de uma aplicação.
 *
 * <p>Aparece em URL e em chave de acesso, e por isso aceita apenas minúsculas, dígitos e hífen
 * entre termos — nada que precise ser escapado ou que mude de forma dependendo de onde é lido.
 *
 * <p>O texto é validado na construção, então um {@code Slug} em mãos é sempre um slug válido: quem
 * o recebe não precisa checar de novo.
 */
public record Slug(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

  private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

  private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

  private static final int MAX_LENGTH = 50;

  private static final String INVALID_CODE = "application.slug_invalid";

  public Slug {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Slug é obrigatório");
    }
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Slug não pode passar de " + MAX_LENGTH + " caracteres");
    }
    if (!FORMAT.matcher(value).matches()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Slug aceita apenas minúsculas, dígitos e hífen entre termos");
    }
  }

  /** Slug que já vem pronto — do banco, de uma URL ou do que o usuário digitou. */
  public static Slug of(String value) {
    return new Slug(value);
  }

  /**
   * Slug derivado de um texto legível — o caminho comum, em que o usuário informa só o rótulo e o
   * identificador público sai dele.
   *
   * <p>Recebe texto, não {@link Name}: a derivação é a mesma para qualquer rótulo — nome de
   * aplicação, título de pesquisa — e prender o tipo a um deles cobraria um método novo a cada
   * rótulo que aparecer.
   *
   * <p>A derivação é uma aproximação legível, não uma tradução fiel: acento vira a letra sem acento
   * ("Pitaço Café" → {@code pitaco-cafe}), qualquer outra pontuação vira separador, e o excesso é
   * cortado no limite de tamanho. Dois textos diferentes podem, portanto, chegar ao mesmo slug —
   * garantir que ele seja único é responsabilidade de quem persiste, não deste tipo.
   *
   * <p>Texto que não deixa nenhum caractere aproveitável (só ideogramas ou só pontuação) não tem
   * slug derivável: nesse caso o slug precisa ser informado à parte, via {@link #of(String)}.
   */
  public static Slug from(String text) {
    if (text == null || text.isBlank()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Texto é obrigatório para derivar o slug");
    }

    var withoutAccents =
        DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    var separated = SEPARATORS.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
    var trimmed = EDGE_HYPHENS.matcher(separated).replaceAll("");

    if (trimmed.length() > MAX_LENGTH) {
      trimmed = EDGE_HYPHENS.matcher(trimmed.substring(0, MAX_LENGTH)).replaceAll("");
    }
    if (trimmed.isEmpty()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Texto \"" + text + "\" não produz nenhum slug válido");
    }

    return new Slug(trimmed);
  }

  /** O próprio texto: é assim que o slug aparece em log, URL e mensagem. */
  @Override
  public String toString() {
    return value;
  }
}

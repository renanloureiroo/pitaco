package com.renanloureiroo.pitaco.modules.collect.domain.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// O contexto do relatório é o que o SDK sabia do próprio estado, não do usuário. A sanitização
// descarta em vez de recusar: um relatório com metade do contexto ainda serve para investigar, e
// recusar o relatório inteiro não protegeria mais ninguém.
public record SdkErrorContext(Map<String, Object> values, int discarded) {

  public static final int MAX_KEYS = 20;
  public static final int MAX_NESTED_KEYS = 10;
  public static final int MAX_STRING_LENGTH = 200;
  public static final int MAX_TOTAL_SIZE = 2000;

  private static final Pattern KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,39}$");
  private static final Pattern TOKEN_BOUNDARY =
      Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|[_.-]+");

  // Por palavra da chave, não por trecho: "context" contém "text" e não pode cair junto de
  // "freeText".
  private static final Set<String> PERSONAL_WORDS =
      Set.of(
          "email", "mail", "phone", "telefone", "celular", "cpf", "cnpj", "rg", "name", "nome",
          "user", "usuario", "username", "respondent", "reference", "ref", "device", "ip",
          "address", "endereco", "answer", "answers", "resposta", "respostas", "text", "texto",
          "value", "valor", "token", "password", "senha", "secret", "segredo", "cookie",
          "session", "location", "latitude", "longitude", "lat", "lng", "birth", "nascimento",
          "card", "cartao", "attribute", "attributes", "atributo", "atributos");

  public SdkErrorContext {
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static SdkErrorContext empty() {
    return new SdkErrorContext(Map.of(), 0);
  }

  public static SdkErrorContext sanitize(Map<String, ?> raw) {
    if (raw == null || raw.isEmpty()) {
      return empty();
    }

    var budget = new int[] {MAX_TOTAL_SIZE};
    var discarded = new int[] {0};
    var kept = sanitizeLevel(raw, MAX_KEYS, true, budget, discarded);

    return new SdkErrorContext(kept, discarded[0]);
  }

  private static Map<String, Object> sanitizeLevel(
      Map<String, ?> raw, int maxKeys, boolean allowNesting, int[] budget, int[] discarded) {
    var kept = new LinkedHashMap<String, Object>();

    for (var entry : raw.entrySet()) {
      var key = entry.getKey();
      var value = cleanValue(entry.getValue(), allowNesting, budget, discarded);

      if (kept.size() >= maxKeys || !isAllowedKey(key) || value == null) {
        discarded[0]++;
        continue;
      }

      var cost = key.length() + sizeOf(value);
      if (cost > budget[0]) {
        discarded[0]++;
        continue;
      }

      budget[0] -= cost;
      kept.put(key, value);
    }

    return kept;
  }

  private static Object cleanValue(
      Object value, boolean allowNesting, int[] budget, int[] discarded) {
    return switch (value) {
      case null -> null;
      case Boolean flag -> flag;
      case Integer number -> number;
      case Long number -> number;
      case Short number -> number.intValue();
      case Double number -> Double.isFinite(number) ? number : null;
      case Float number -> Float.isFinite(number) ? number.doubleValue() : null;
      case java.math.BigInteger number -> number.bitLength() < 64 ? number.longValue() : null;
      case java.math.BigDecimal number -> number.doubleValue();
      case String text -> cleanText(text);
      case Map<?, ?> nested when allowNesting -> nestedOf(nested, budget, discarded);
      default -> null;
    };
  }

  private static Object nestedOf(Map<?, ?> nested, int[] budget, int[] discarded) {
    var typed = new LinkedHashMap<String, Object>();
    nested.forEach(
        (key, value) -> {
          if (key instanceof String name) {
            typed.put(name, value);
          } else {
            discarded[0]++;
          }
        });

    var kept = sanitizeLevel(typed, MAX_NESTED_KEYS, false, budget, discarded);
    return kept.isEmpty() ? null : kept;
  }

  private static String cleanText(String text) {
    var stripped = text.strip();
    if (stripped.isEmpty() || PersonalDataMask.looksPersonal(stripped)) {
      return null;
    }
    return stripped.length() > MAX_STRING_LENGTH
        ? stripped.substring(0, MAX_STRING_LENGTH)
        : stripped;
  }

  private static boolean isAllowedKey(String key) {
    if (key == null || !KEY.matcher(key).matches()) {
      return false;
    }

    return List.of(TOKEN_BOUNDARY.split(key)).stream()
        .map(word -> word.toLowerCase(Locale.ROOT))
        .noneMatch(PERSONAL_WORDS::contains);
  }

  private static int sizeOf(Object value) {
    return switch (value) {
      case String text -> text.length();
      case Map<?, ?> nested ->
          nested.entrySet().stream()
              .mapToInt(entry -> entry.getKey().toString().length() + sizeOf(entry.getValue()))
              .sum();
      default -> String.valueOf(value).length();
    };
  }
}

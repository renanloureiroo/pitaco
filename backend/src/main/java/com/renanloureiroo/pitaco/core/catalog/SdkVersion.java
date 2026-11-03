package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

// Semver sem o metadado de build: "1.2.3+abc" e "1.2.3" são a mesma versão em circulação, e é
// assim que ela é gravada e contada.
public record SdkVersion(int major, int minor, int patch, Optional<String> preRelease)
    implements Comparable<SdkVersion> {

  public static final int MAX_LENGTH = 40;
  public static final SdkVersion BASELINE = new SdkVersion(1, 0, 0, Optional.empty());

  private static final String INVALID_CODE = "sdk_version.invalid";
  private static final Pattern FORMAT =
      Pattern.compile(
          "^(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})"
              + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
              + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
  private static final Pattern NUMERIC = Pattern.compile("^\\d+$");

  private static final Comparator<SdkVersion> ORDER =
      Comparator.comparingInt(SdkVersion::major)
          .thenComparingInt(SdkVersion::minor)
          .thenComparingInt(SdkVersion::patch)
          .thenComparing(SdkVersion::preRelease, SdkVersion::comparePreRelease);

  public SdkVersion {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Versão do SDK não aceita número negativo");
    }
    preRelease = preRelease == null ? Optional.empty() : preRelease;
  }

  // Leniente de propósito: a superfície pública ignora versão malformada em vez de recusar a
  // consulta, porque quem pagaria a recusa seria o app hospedeiro.
  public static Optional<SdkVersion> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }

    var text = raw.strip();
    if (text.isEmpty() || text.length() > MAX_LENGTH) {
      return Optional.empty();
    }

    var matcher = FORMAT.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    return Optional.of(
        new SdkVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)),
            Optional.ofNullable(matcher.group(4))));
  }

  public static SdkVersion of(String raw) {
    return parse(raw)
        .orElseThrow(
            () ->
                new DomainException(
                    ErrorType.VALIDATION, INVALID_CODE, "Versão do SDK fora do formato semver"));
  }

  public String value() {
    var core = major + "." + minor + "." + patch;
    return preRelease.map(tag -> core + "-" + tag).orElse(core);
  }

  public boolean isAtLeast(SdkVersion other) {
    return compareTo(other) >= 0;
  }

  public static SdkVersion highest(SdkVersion first, SdkVersion second) {
    return first.compareTo(second) >= 0 ? first : second;
  }

  @Override
  public int compareTo(SdkVersion other) {
    return ORDER.compare(this, other);
  }

  @Override
  public String toString() {
    return value();
  }

  // A regra 11 do semver: sem pré-lançamento vence com pré-lançamento; entre dois, compara
  // identificador a identificador, numérico por valor e numérico antes de alfanumérico.
  private static int comparePreRelease(Optional<String> first, Optional<String> second) {
    if (first.isEmpty() || second.isEmpty()) {
      return Boolean.compare(first.isEmpty(), second.isEmpty());
    }

    var left = first.get().split("\\.");
    var right = second.get().split("\\.");

    for (var index = 0; index < Math.min(left.length, right.length); index++) {
      var compared = compareIdentifier(left[index], right[index]);
      if (compared != 0) {
        return compared;
      }
    }

    return Integer.compare(left.length, right.length);
  }

  private static int compareIdentifier(String left, String right) {
    var leftNumeric = NUMERIC.matcher(left).matches();
    var rightNumeric = NUMERIC.matcher(right).matches();

    if (leftNumeric && rightNumeric) {
      return new java.math.BigInteger(left).compareTo(new java.math.BigInteger(right));
    }
    if (leftNumeric != rightNumeric) {
      return leftNumeric ? -1 : 1;
    }
    return left.compareTo(right);
  }
}

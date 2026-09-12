package com.renanloureiroo.pitaco.modules.collect.domain.health;

import java.util.Locale;

// Lista fechada: o que o SDK mandar fora dela vira UNKNOWN em vez de recusa, porque um SDK mais
// novo que o servidor é situação normal.
public enum SdkErrorKind {
  RENDER_ERROR,
  NETWORK_ERROR,
  MALFORMED_RESPONSE,
  STORAGE_ERROR,
  UNKNOWN;

  public static SdkErrorKind fromWire(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNKNOWN;
    }

    var normalized = raw.strip().toUpperCase(Locale.ROOT);
    for (var kind : values()) {
      if (kind.name().equals(normalized)) {
        return kind;
      }
    }
    return UNKNOWN;
  }

  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }
}

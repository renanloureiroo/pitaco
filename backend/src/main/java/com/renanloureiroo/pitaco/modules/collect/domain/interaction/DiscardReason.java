package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import java.util.Locale;

public enum DiscardReason {
  DISPLAY_UNAVAILABLE,
  OUTSIDE_WINDOW,
  UNKNOWN_TYPE,
  INVALID_ENVELOPE,
  UNKNOWN_QUESTION,
  OVER_LIMIT;

  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }
}

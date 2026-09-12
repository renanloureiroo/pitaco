package com.renanloureiroo.pitaco.modules.results.domain.comparability;

import java.util.List;

public record Comparability(boolean comparable, List<Integer> versions) {

  public Comparability {
    versions = List.copyOf(versions);
  }
}

package com.renanloureiroo.pitaco.modules.collect.domain.health;

import java.util.Optional;

// Supressões sobre tudo que a pesquisa teria alcançado: as exibidas mais as que o SDK não soube
// desenhar. O mínimo absoluto existe porque uma supressão em duas consultas é 50% e não diz
// nada.
public record SuppressionShare(long displays, long suppressions) {

  public SuppressionShare {
    if (displays < 0 || suppressions < 0) {
      throw new IllegalArgumentException("Contagens não podem ser negativas");
    }
  }

  public Optional<Double> share() {
    var reached = displays + suppressions;
    return reached == 0 ? Optional.empty() : Optional.of((double) suppressions / reached);
  }

  public boolean isRelevant(double threshold, long minimum) {
    return suppressions >= minimum && share().map(value -> value >= threshold).orElse(false);
  }
}

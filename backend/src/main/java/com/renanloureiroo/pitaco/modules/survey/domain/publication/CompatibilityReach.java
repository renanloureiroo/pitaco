package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import java.util.List;
import java.util.Optional;

// Avisa quando as versões que suportam a pesquisa não chegam a ser maioria estrita do tráfego
// recente. Sem tráfego registrado não há o que dizer: a aplicação ainda não falou com o SDK.
public final class CompatibilityReach {

  private CompatibilityReach() {}

  public static Optional<PublicationWarning> warningFor(
      SdkVersion required, List<SdkTraffic> traffic) {
    var total = traffic.stream().mapToLong(SdkTraffic::requests).sum();
    if (total <= 0) {
      return Optional.empty();
    }

    var unsupported =
        traffic.stream()
            .filter(entry -> !entry.version().isAtLeast(required))
            .mapToLong(SdkTraffic::requests)
            .sum();

    var supported = total - unsupported;
    if (supported * 2 > total) {
      return Optional.empty();
    }

    return Optional.of(
        PublicationWarning.unsupportedByMajority(required, (double) unsupported / total));
  }
}

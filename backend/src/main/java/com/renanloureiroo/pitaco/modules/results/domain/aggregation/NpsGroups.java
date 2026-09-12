package com.renanloureiroo.pitaco.modules.results.domain.aggregation;

import java.util.Map;

public record NpsGroups(long promoters, long passives, long detractors) {

  private static final int PROMOTER_MIN = 9;
  private static final int PASSIVE_MIN = 7;

  public static NpsGroups of(Map<Integer, Long> countsByScore) {
    long promoters = 0;
    long passives = 0;
    long detractors = 0;

    for (var entry : countsByScore.entrySet()) {
      if (entry.getKey() >= PROMOTER_MIN) {
        promoters += entry.getValue();
      } else if (entry.getKey() >= PASSIVE_MIN) {
        passives += entry.getValue();
      } else {
        detractors += entry.getValue();
      }
    }

    return new NpsGroups(promoters, passives, detractors);
  }

  public long total() {
    return promoters + passives + detractors;
  }

  // Promotores menos detratores, em pontos de -100 a 100. Calculado aqui para que ninguém
  // precise exportar e fazer a conta na planilha.
  public double score() {
    var total = total();
    return total == 0 ? 0 : (promoters - detractors) * 100.0 / total;
  }
}

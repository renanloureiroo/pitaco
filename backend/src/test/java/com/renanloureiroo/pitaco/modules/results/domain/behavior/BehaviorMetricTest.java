package com.renanloureiroo.pitaco.modules.results.domain.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BehaviorMetric")
class BehaviorMetricTest {

  @Test
  @DisplayName("Sem denominador não há taxa; com ele, a fração")
  void taxa() {
    assertThat(BehaviorMetric.rate(0, 0)).isEmpty();
    assertThat(BehaviorMetric.rate(1, 4)).contains(0.25);
    assertThat(BehaviorMetric.rate(0, 3)).contains(0.0);
  }

  @Test
  @DisplayName("Toda métrica tem chave única e definição escrita")
  void definicoes() {
    assertThat(Arrays.stream(BehaviorMetric.values()).map(BehaviorMetric::key)).doesNotHaveDuplicates();
    assertThat(BehaviorMetric.values()).allSatisfy(metric -> assertThat(metric.definition()).isNotBlank());
  }
}

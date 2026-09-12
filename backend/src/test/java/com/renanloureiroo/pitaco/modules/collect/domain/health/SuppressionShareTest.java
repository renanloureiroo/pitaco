package com.renanloureiroo.pitaco.modules.collect.domain.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SuppressionShare")
class SuppressionShareTest {

  private static final double THRESHOLD = 0.05;
  private static final long MINIMUM = 5;

  @Test
  @DisplayName("A proporção é supressões sobre exibidas mais suprimidas")
  void proporcao() {
    assertThat(new SuppressionShare(90, 10).share()).hasValueSatisfying(
        share -> assertThat(share).isCloseTo(0.10, within(1e-9)));
  }

  @Test
  @DisplayName("Sem exibição nem supressão, não há proporção")
  void sem_alcance() {
    assertThat(new SuppressionShare(0, 0).share()).isEmpty();
    assertThat(new SuppressionShare(0, 0).isRelevant(THRESHOLD, MINIMUM)).isFalse();
  }

  @Test
  @DisplayName("Relevante no limiar exato e com o mínimo de supressões")
  void relevante_no_limiar() {
    assertThat(new SuppressionShare(95, 5).isRelevant(THRESHOLD, MINIMUM)).isTrue();
  }

  @Test
  @DisplayName("Abaixo do limiar não é relevante, por mais supressões que haja")
  void abaixo_do_limiar() {
    assertThat(new SuppressionShare(1000, 20).isRelevant(THRESHOLD, MINIMUM)).isFalse();
  }

  @Test
  @DisplayName("Proporção alta com poucas supressões não é relevante")
  void poucas_supressoes() {
    assertThat(new SuppressionShare(1, 4).isRelevant(THRESHOLD, MINIMUM)).isFalse();
  }

  @Test
  @DisplayName("Só supressões, nenhuma exibição: 100% e relevante a partir do mínimo")
  void so_supressoes() {
    var share = new SuppressionShare(0, 5);

    assertThat(share.share()).contains(1.0);
    assertThat(share.isRelevant(THRESHOLD, MINIMUM)).isTrue();
  }
}

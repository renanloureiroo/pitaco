package com.renanloureiroo.pitaco.modules.results.domain.comparability;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionComparability")
class QuestionComparabilityTest {

  private static final QuestionKey KEY = QuestionKey.generate();

  private static QuestionShape choice(int version, String... values) {
    return new QuestionShape(
        KEY, version, QuestionType.SINGLE_CHOICE, Set.of(values), Optional.empty(), Optional.empty());
  }

  private static QuestionShape rating(int version, int max, Optional<String> condition) {
    return new QuestionShape(
        KEY, version, QuestionType.RATING, Set.of(), Optional.of(new ScaleRange(1, max)), condition);
  }

  @Test
  @DisplayName("Mesma forma nas versões exibidas: seguro somar")
  void mesma_forma_e_comparavel() {
    var result = QuestionComparability.of(List.of(choice(2, "yes", "no"), choice(1, "no", "yes")), Set.of(1, 2));

    assertThat(result.comparable()).isTrue();
    assertThat(result.versions()).containsExactly(1, 2);
  }

  @Test
  void opcoes_trocadas_nao_somam() {
    var result = QuestionComparability.of(List.of(choice(1, "yes", "no"), choice(2, "yes", "maybe")), Set.of(1, 2));

    assertThat(result.comparable()).isFalse();
    assertThat(result.versions()).containsExactly(1, 2);
  }

  @Test
  void faixa_ou_condicao_trocada_nao_somam() {
    assertThat(
            QuestionComparability.of(
                    List.of(rating(1, 5, Optional.empty()), rating(2, 7, Optional.empty())), Set.of(1, 2))
                .comparable())
        .isFalse();
    assertThat(
            QuestionComparability.of(
                    List.of(rating(1, 5, Optional.empty()), rating(2, 5, Optional.of("k|EQUALS|1|-|-"))),
                    Set.of(1, 2))
                .comparable())
        .isFalse();
  }

  @Test
  @DisplayName("Versão sem exibição no recorte não entra: não soma nada, então não engana")
  void versao_sem_exibicao_nao_conta() {
    var result = QuestionComparability.of(List.of(choice(1, "yes", "no"), choice(2, "yes", "maybe")), Set.of(1));

    assertThat(result.comparable()).isTrue();
    assertThat(result.versions()).containsExactly(1);
  }

  @Test
  void sem_versao_exibida_e_comparavel_e_vazia() {
    var result = QuestionComparability.of(List.of(choice(1, "yes")), Set.of());

    assertThat(result.comparable()).isTrue();
    assertThat(result.versions()).isEmpty();
  }
}

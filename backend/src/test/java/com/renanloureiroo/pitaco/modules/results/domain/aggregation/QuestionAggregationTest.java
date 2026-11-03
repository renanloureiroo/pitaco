package com.renanloureiroo.pitaco.modules.results.domain.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionAggregation")
class QuestionAggregationTest {

  private static final List<QuestionOption> OPTIONS =
      List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2));

  @Test
  @DisplayName("Pergunta sem resposta não tem agregado, o que é diferente de zero em cada opção")
  void sem_resposta_nao_tem_agregado() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.SINGLE_CHOICE, OPTIONS, Optional.empty(), 0, Map.of(), Map.of());

    assertThat(aggregate).isEmpty();
  }

  @Test
  @DisplayName("Escolha traz contagem e proporção por opção, na ordem da definição, com zero onde ninguém marcou")
  void escolha_por_opcao() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.SINGLE_CHOICE, OPTIONS, Optional.empty(), 4, Map.of("yes", 3L), Map.of());

    var choice = (QuestionAggregate.Choice) aggregate.orElseThrow();
    assertThat(choice.options())
        .containsExactly(
            new QuestionAggregate.OptionShare("yes", "Sim", 3, 0.75),
            new QuestionAggregate.OptionShare("no", "Não", 0, 0));
  }

  @Test
  @DisplayName("Opção que a definição atual não tem entra pelo valor, no fim, sem sumir da conta")
  void opcao_de_outra_versao_nao_some() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.MULTIPLE_CHOICE,
            OPTIONS,
            Optional.empty(),
            2,
            Map.of("yes", 2L, "maybe", 1L),
            Map.of());

    var choice = (QuestionAggregate.Choice) aggregate.orElseThrow();
    assertThat(choice.options()).extracting(QuestionAggregate.OptionShare::value)
        .containsExactly("yes", "no", "maybe");
    assertThat(choice.options().get(2).label()).isEqualTo("maybe");
  }

  @Test
  @DisplayName("Avaliação traz média e a faixa inteira, com zero onde ninguém marcou")
  void avaliacao_media_e_distribuicao() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.RATING,
            List.of(),
            Optional.of(new ScaleRange(1, 3)),
            3,
            Map.of(),
            Map.of(1, 1L, 3, 2L));

    var numeric = (QuestionAggregate.Numeric) aggregate.orElseThrow();
    assertThat(numeric.average()).isCloseTo(7.0 / 3, within(0.0001));
    assertThat(numeric.distribution())
        .extracting(QuestionAggregate.ValueShare::value, QuestionAggregate.ValueShare::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 1L),
            org.assertj.core.groups.Tuple.tuple(2, 0L),
            org.assertj.core.groups.Tuple.tuple(3, 2L));
  }

  @Test
  @DisplayName("NPS separa os três grupos e calcula a pontuação de -100 a 100")
  void nps_com_tres_grupos() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.NPS,
            List.of(),
            Optional.of(ScaleRange.NPS),
            10,
            Map.of(),
            Map.of(10, 4L, 9, 2L, 7, 1L, 8, 1L, 3, 2L));

    var nps = (QuestionAggregate.Nps) aggregate.orElseThrow();
    assertThat(nps.promoters()).isEqualTo(6);
    assertThat(nps.passives()).isEqualTo(2);
    assertThat(nps.detractors()).isEqualTo(2);
    assertThat(nps.score()).isCloseTo(40.0, within(0.0001));
    assertThat(nps.distribution()).hasSize(11);
  }

  @Test
  @DisplayName("Texto livre com resposta só sinaliza que há texto: a listagem vem de outro lugar")
  void texto_livre() {
    var aggregate =
        QuestionAggregation.aggregate(
            QuestionType.FREE_TEXT, List.of(), Optional.empty(), 2, Map.of(), Map.of());

    assertThat(aggregate).contains(new QuestionAggregate.Text());
  }
}

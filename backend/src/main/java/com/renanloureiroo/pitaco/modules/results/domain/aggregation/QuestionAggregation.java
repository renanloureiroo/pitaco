package com.renanloureiroo.pitaco.modules.results.domain.aggregation;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class QuestionAggregation {

  private QuestionAggregation() {}

  // Ausente quando ninguém respondeu: "sem resposta" e "zero em cada opção" são coisas
  // diferentes, e a tela precisa distingui-las.
  public static Optional<QuestionAggregate> aggregate(
      QuestionType type,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      long answered,
      Map<String, Long> optionCounts,
      Map<Integer, Long> numericCounts) {
    if (answered == 0) {
      return Optional.empty();
    }

    return Optional.of(
        switch (type) {
          case SINGLE_CHOICE, MULTIPLE_CHOICE -> choice(options, answered, optionCounts);
          case RATING, SCALE -> numeric(range, answered, numericCounts);
          case NPS -> nps(answered, numericCounts);
          case FREE_TEXT -> new QuestionAggregate.Text();
        });
  }

  // A proporção é sobre quem respondeu a pergunta: em múltipla escolha as fatias somam mais
  // de cem por cento, e é assim mesmo — cada opção diz quantos a marcaram.
  private static QuestionAggregate.Choice choice(
      List<QuestionOption> options, long answered, Map<String, Long> optionCounts) {
    var shares = new ArrayList<QuestionAggregate.OptionShare>();
    var remaining = new LinkedHashMap<>(optionCounts);

    for (var option : options) {
      var count = remaining.getOrDefault(option.value(), 0L);
      remaining.remove(option.value());
      shares.add(
          new QuestionAggregate.OptionShare(
              option.value(), option.label(), count, share(count, answered)));
    }

    // Opção que a definição atual não tem, mas alguma versão teve: aparece pelo valor, nunca
    // some da conta.
    remaining.forEach(
        (value, count) ->
            shares.add(new QuestionAggregate.OptionShare(value, value, count, share(count, answered))));

    return new QuestionAggregate.Choice(List.copyOf(shares));
  }

  private static QuestionAggregate.Numeric numeric(
      Optional<ScaleRange> range, long answered, Map<Integer, Long> numericCounts) {
    return new QuestionAggregate.Numeric(
        average(numericCounts), distribution(range, answered, numericCounts));
  }

  private static QuestionAggregate.Nps nps(long answered, Map<Integer, Long> numericCounts) {
    var groups = NpsGroups.of(numericCounts);

    return new QuestionAggregate.Nps(
        groups.promoters(),
        groups.passives(),
        groups.detractors(),
        groups.score(),
        distribution(Optional.of(ScaleRange.NPS), answered, numericCounts));
  }

  private static double average(Map<Integer, Long> numericCounts) {
    long sum = 0;
    long total = 0;
    for (var entry : numericCounts.entrySet()) {
      sum += (long) entry.getKey() * entry.getValue();
      total += entry.getValue();
    }
    return total == 0 ? 0 : (double) sum / total;
  }

  // A faixa inteira aparece, com zero onde ninguém marcou: um gráfico de distribuição com
  // buracos esconde justamente os valores que ninguém escolheu.
  private static List<QuestionAggregate.ValueShare> distribution(
      Optional<ScaleRange> range, long answered, Map<Integer, Long> numericCounts) {
    var ordered = new TreeMap<Integer, Long>();
    range.ifPresent(
        scale -> {
          for (var value = scale.min(); value <= scale.max(); value++) {
            ordered.put(value, 0L);
          }
        });
    ordered.putAll(numericCounts);

    return ordered.entrySet().stream()
        .map(
            entry ->
                new QuestionAggregate.ValueShare(
                    entry.getKey(), entry.getValue(), share(entry.getValue(), answered)))
        .toList();
  }

  private static double share(long count, long answered) {
    return answered == 0 ? 0 : (double) count / answered;
  }
}

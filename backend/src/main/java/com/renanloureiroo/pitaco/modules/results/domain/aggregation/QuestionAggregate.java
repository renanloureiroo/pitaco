package com.renanloureiroo.pitaco.modules.results.domain.aggregation;

import java.util.List;

// Selada para que o presenter seja exaustivo em compilação: cada tipo de pergunta é lido na
// forma que faz sentido para ele, e nenhum cai num "genérico".
public sealed interface QuestionAggregate {

  record Choice(List<OptionShare> options) implements QuestionAggregate {}

  record Numeric(double average, List<ValueShare> distribution) implements QuestionAggregate {}

  record Nps(
      long promoters, long passives, long detractors, double score, List<ValueShare> distribution)
      implements QuestionAggregate {}

  record Text() implements QuestionAggregate {}

  record OptionShare(String value, String label, long count, double share) {}

  record ValueShare(int value, long count, double share) {}
}

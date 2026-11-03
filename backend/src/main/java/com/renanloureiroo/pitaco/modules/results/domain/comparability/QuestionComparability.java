package com.renanloureiroo.pitaco.modules.results.domain.comparability;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Pergunta a pergunta, e não pela pesquisa inteira: marcar tudo como suspeito por uma opção
// trocada jogaria fora o dado bom das perguntas que não mudaram. Só entram as versões que
// receberam exibição no recorte — uma versão sem resposta nenhuma não soma nada, então não tem
// como enganar a soma.
public final class QuestionComparability {

  private QuestionComparability() {}

  public static Comparability of(List<QuestionShape> shapes, Set<Integer> displayedVersions) {
    var considered =
        shapes.stream()
            .filter(shape -> displayedVersions.contains(shape.versionNumber()))
            .sorted(Comparator.comparingInt(QuestionShape::versionNumber))
            .toList();

    var comparable =
        considered.isEmpty()
            || considered.stream().allMatch(shape -> shape.sameMeaningAs(considered.get(0)));

    return new Comparability(
        comparable, considered.stream().map(QuestionShape::versionNumber).toList());
  }
}

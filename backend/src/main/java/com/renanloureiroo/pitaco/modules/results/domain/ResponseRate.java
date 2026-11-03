package com.renanloureiroo.pitaco.modules.results.domain;

import java.util.Optional;

// Uma definição só no sistema inteiro, escrita aqui e devolvida junto dos números: quem lê a
// tela e quem lê o export contam a mesma coisa.
public record ResponseRate(
    long displayed,
    long completed,
    long dismissed,
    long abandoned,
    long inProgress,
    Optional<Double> rate) {

  public static final String DEFINITION =
      "Taxa de resposta = concluídas ÷ exibidas. Exibidas conta toda abertura da pesquisa, "
          + "inclusive as dispensadas, as abandonadas e as ainda em andamento.";

  public static ResponseRate of(
      long displayed, long completed, long dismissed, long abandoned, long inProgress) {
    var rate = displayed == 0 ? Optional.<Double>empty() : Optional.of((double) completed / displayed);

    return new ResponseRate(displayed, completed, dismissed, abandoned, inProgress, rate);
  }
}

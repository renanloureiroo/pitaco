package com.renanloureiroo.pitaco.modules.results.domain.behavior;

import java.util.Optional;

// Uma definição por número, escrita aqui e devolvida junto dele: o painel mostra a mesma frase
// que o contrato documenta, e quem lê a tela conta a mesma coisa que quem lê a API.
public enum BehaviorMetric {
  INSTRUMENTED(
      "instrumented",
      "Exibições instrumentadas = exibições do recorte com ao menos um evento de interação "
          + "aceito. É a base de toda leitura de comportamento: exibição de SDK que não envia "
          + "eventos não entra na conta."),
  VIEWED(
      "viewed",
      "Vista = exibições em que a pergunta passou a ser a atual ao menos uma vez "
          + "(question_viewed)."),
  ANSWERED(
      "answered",
      "Respondida = exibições em que a última saída da pergunta (question_left de maior seq) "
          + "tem answered verdadeiro."),
  SKIPPED(
      "skipped",
      "Pulada = exibições em que a última saída da pergunta tem answered falso e seguiu adiante "
          + "(to = next ou complete)."),
  ABANDONED(
      "abandoned",
      "Abandonada nela = exibições dispensadas, ou sem desfecho depois do prazo de abandono, "
          + "cuja última pergunta vista (question_viewed de maior seq) é esta."),
  ACTIVE_TIME(
      "activeTime",
      "Tempo ativo = por exibição, a soma do activeMs de todas as visitas à pergunta "
          + "(question_left), já sem o tempo em segundo plano. Mediana e p90 entre as exibições "
          + "que saíram da pergunta ao menos uma vez, em milissegundos, com interpolação linear."),
  REVISIT_RATE(
      "revisitRate",
      "Taxa de volta = exibições que voltaram à pergunta depois de sair dela (question_viewed "
          + "com visit ≥ 2) ÷ exibições que a viram."),
  ANSWER_CHANGE_RATE(
      "answerChangeRate",
      "Taxa de troca de resposta = exibições que trocaram ou desmarcaram a escolha "
          + "(answer_changed ou answer_deselected) ÷ exibições que escolheram algo na pergunta "
          + "(answer_selected)."),
  VALIDATION_BLOCKS(
      "validationBlocks",
      "Bloqueios de validação = tentativas de avançar com a pergunta obrigatória em branco "
          + "(validation_blocked), contadas em eventos e em exibições com ao menos uma."),
  DISMISSAL_VIA(
      "dismissalVia",
      "Dispensa por via = exibições instrumentadas com survey_dismissed, pela via da última "
          + "dispensa; a fração é sobre o total de dispensas. Via ausente ou fora do catálogo "
          + "conta como não informada.");

  private final String key;
  private final String definition;

  BehaviorMetric(String key, String definition) {
    this.key = key;
    this.definition = definition;
  }

  public String key() {
    return key;
  }

  public String definition() {
    return definition;
  }

  // Sem denominador não há taxa: ausente, nunca zero.
  public static Optional<Double> rate(long numerator, long denominator) {
    return denominator == 0 ? Optional.empty() : Optional.of((double) numerator / denominator);
  }
}

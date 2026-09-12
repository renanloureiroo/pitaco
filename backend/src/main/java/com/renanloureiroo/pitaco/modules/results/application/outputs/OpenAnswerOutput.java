package com.renanloureiroo.pitaco.modules.results.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.time.Instant;
import java.util.List;

// Texto com contexto: o que a mesma pessoa respondeu nas outras perguntas da exibição, já em
// forma legível — "achei confuso" vindo de quem deu 9 é outra coisa vindo de quem deu 2.
public record OpenAnswerOutput(
    String displayId,
    QuestionKey questionKey,
    String statement,
    String text,
    Instant answeredAt,
    List<AnswerContext> context) {

  public record AnswerContext(QuestionKey questionKey, String statement, String value) {}
}

package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DisplayDetailOutput(
    DisplaySummaryOutput summary,
    RespondentId respondentId,
    SurveyId surveyId,
    Map<String, String> attributes,
    List<AnswerReadOutput> answers) {

  // LinkedHashMap e não Map.copyOf: a ordem dos atributos é a do instantâneo gravado.
  public DisplayDetailOutput {
    attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    answers = List.copyOf(answers);
  }
}

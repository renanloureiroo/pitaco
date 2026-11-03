package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.SubmissionProblem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// BUSINESS_RULE e não VALIDATION porque a entrada está bem-formada: o que falha é a regra da
// versão exibida, e o contrato exige 422 — que é onde ErrorTypeHttpStatus já mapeia este tipo.
public final class SubmissionRejected extends ApplicationException {

  private static final String CODE = "submission.rejected";
  private static final String ERRORS_PROPERTY = "errors";

  private final transient List<SubmissionProblem> problems;

  public SubmissionRejected(List<SubmissionProblem> problems) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "O envio tem respostas que a versão exibida não aceita");
    this.problems = List.copyOf(problems);
  }

  public List<SubmissionProblem> problems() {
    return problems;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of(
        ERRORS_PROPERTY,
        problems.stream()
            .map(SubmissionRejected::describe)
            .toList());
  }

  private static Map<String, Object> describe(SubmissionProblem problem) {
    var described = new LinkedHashMap<String, Object>();
    problem.questionKey().map(QuestionKey::value).ifPresent(key -> described.put("questionKey", key));
    described.put("code", problem.code());
    return described;
  }
}

package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import java.util.List;
import java.util.Optional;

// Uma resposta que a retenção vai apagar, com o que é preciso para congelar o agregado dela.
// O texto livre não está aqui: texto não agrega, e não precisa sobreviver ao descarte.
public record ExpiringAnswer(
    String answerId,
    SurveyId surveyId,
    SurveyVersionId versionId,
    String displayId,
    QuestionKey key,
    String status,
    Optional<Integer> number,
    List<String> options) {

  public static final String ANSWERED = "ANSWERED";

  public ExpiringAnswer {
    number = number == null ? Optional.empty() : number;
    options = options == null ? List.of() : List.copyOf(options);
  }

  public boolean isAnswered() {
    return ANSWERED.equals(status);
  }
}

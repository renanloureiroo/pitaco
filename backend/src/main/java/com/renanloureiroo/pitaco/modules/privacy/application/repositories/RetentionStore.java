package com.renanloureiroo.pitaco.modules.privacy.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.ExpiringAnswer;
import java.time.Instant;
import java.util.List;

// As respostas da aplicação vistas pelo prazo: o que já venceu, em lotes, e quanto vence até
// um instante. Nenhum método carrega a aplicação inteira de uma vez.
public interface RetentionStore {

  // Em ordem de resposta, com o identificador desempatando; no máximo `limit`.
  List<ExpiringAnswer> expiringAnswers(ApplicationId applicationId, Instant before, int limit);

  int deleteAnswers(List<String> answerIds);

  // Apaga o texto e mantém a resposta: ela continua contando como dada. Devolve quantas mudou.
  int clearTexts(ApplicationId applicationId, Instant before, int limit);

  long countAnswersBefore(ApplicationId applicationId, Instant before);

  long countTextsBefore(ApplicationId applicationId, Instant before);
}

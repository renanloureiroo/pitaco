package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.util.Optional;

// A cota é da pesquisa e o encerramento é da autoria; a coleta só sabe contar concluídas e
// avisar quando a conta chegou lá.
public interface SurveyQuotaGateway {

  Optional<Integer> responseQuotaOf(SurveyId surveyId);

  // Relê a cota travando a linha da pesquisa até o fim da transação: duas conclusões simultâneas
  // passam a contar uma depois da outra, e nenhuma deixa de enxergar a outra.
  Optional<Integer> lockedResponseQuotaOf(SurveyId surveyId);

  void endByQuota(ApplicationId applicationId, SurveyId surveyId);
}

package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyQuotaGateway;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.EndSurveyByQuotaUseCase;
import java.util.Optional;
import org.springframework.stereotype.Component;

// O encerramento passa pelo caso de uso da autoria, e não por um update direto aqui: registrar
// a transição com o motivo certo é regra dela. A transação é a da conclusão, que ele encontra
// aberta e à qual se junta.
@Component
public class SurveyQuotaGatewaySurvey implements SurveyQuotaGateway {

  private final SurveyScopeJpaRepository surveys;
  private final EndSurveyByQuotaUseCase endSurveyByQuota;

  public SurveyQuotaGatewaySurvey(
      SurveyScopeJpaRepository surveys, EndSurveyByQuotaUseCase endSurveyByQuota) {
    this.surveys = surveys;
    this.endSurveyByQuota = endSurveyByQuota;
  }

  @Override
  public Optional<Integer> responseQuotaOf(SurveyId surveyId) {
    return surveys.findResponseQuota(surveyId.value());
  }

  @Override
  public Optional<Integer> lockedResponseQuotaOf(SurveyId surveyId) {
    return surveys.lockResponseQuota(surveyId.value());
  }

  @Override
  public void endByQuota(ApplicationId applicationId, SurveyId surveyId) {
    endSurveyByQuota.execute(new EndSurveyByQuotaUseCase.Input(applicationId, surveyId));
  }
}

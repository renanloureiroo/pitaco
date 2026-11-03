package com.renanloureiroo.pitaco.modules.results.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component("resultsSurveyScopeGateway")
public class SurveyScopeGatewayJpa implements SurveyScopeGateway {

  private final ResultsScopeJpaRepository repository;

  public SurveyScopeGatewayJpa(ResultsScopeJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<SurveyScope> scopeOf(ApplicationId applicationId, SurveyId surveyId) {
    return repository.scope(applicationId.value(), surveyId.value()).stream()
        .findFirst()
        .map(
            row ->
                new SurveyScope(
                    row[0] != null,
                    Optional.ofNullable(row[1]).map(days -> ((Number) days).intValue()),
                    Optional.ofNullable(row[2]).map(Object::toString),
                    Boolean.TRUE.equals(row[3])));
  }
}

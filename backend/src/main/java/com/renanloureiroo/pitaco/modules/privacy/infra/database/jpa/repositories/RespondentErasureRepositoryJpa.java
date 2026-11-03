package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RespondentErasureRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects.ErasureTarget;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RespondentErasureRepositoryJpa implements RespondentErasureRepository {

  private final ErasureJpaRepository repository;

  public RespondentErasureRepositoryJpa(ErasureJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<ErasableRespondent> find(ApplicationId applicationId, ErasureTarget target) {
    return repository
        .find(applicationId.value(), target.kind().name(), target.value())
        .stream()
        .findFirst()
        .map(
            row ->
                new ErasableRespondent(
                    (String) row[0], ((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
  }

  @Override
  public void erase(String respondentId) {
    repository.erase(respondentId);
  }
}

package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionRunRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.mappers.PrivacyJpaMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RetentionRunRepositoryJpa implements RetentionRunRepository {

  private final RetentionRunJpaRepository repository;

  public RetentionRunRepositoryJpa(RetentionRunJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public RetentionRun create(RetentionRun run) {
    repository.save(PrivacyJpaMapper.toJpa(run));
    return run;
  }

  @Override
  public Optional<Instant> lastRunAt(ApplicationId applicationId) {
    return repository.lastRunAt(applicationId.value());
  }
}

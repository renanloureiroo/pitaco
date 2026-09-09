package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.RespondentMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RespondentRepositoryJpa implements RespondentRepository {

  private final RespondentJpaRepository repository;

  public RespondentRepositoryJpa(RespondentJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Respondent> findByIdentity(
      ApplicationId applicationId, RespondentIdentity identity) {
    return repository
        .findByApplicationIdAndIdentityKindAndIdentityValue(
            applicationId.value(), identity.kind().name(), identity.value())
        .map(RespondentMapper::toDomain);
  }

  @Override
  public Respondent create(Respondent respondent) {
    return save(respondent);
  }

  @Override
  public Respondent update(Respondent respondent) {
    return save(respondent);
  }

  private Respondent save(Respondent respondent) {
    repository.save(RespondentMapper.toJpa(respondent));
    return respondent;
  }
}

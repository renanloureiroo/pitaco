package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.RespondentMapper;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RespondentRepositoryJpa implements RespondentRepository {

  private final RespondentJpaRepository repository;

  public RespondentRepositoryJpa(RespondentJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Respondent> findById(RespondentId id, ApplicationId applicationId) {
    return repository
        .findByIdAndApplicationId(id.value(), applicationId.value())
        .map(RespondentMapper::toDomain);
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

  @Override
  public Page<Respondent> findPage(ListRespondentsQuery query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.desc("id")));

    var page = repository.findByApplicationId(query.applicationId().value(), pageable);

    return new Page<>(
        page.getContent().stream().map(RespondentMapper::toDomain).toList(),
        page.getTotalElements());
  }

  private Respondent save(Respondent respondent) {
    repository.save(RespondentMapper.toJpa(respondent));
    return respondent;
  }
}

package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.DeletionAuditRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.mappers.PrivacyJpaMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class DeletionAuditRepositoryJpa implements DeletionAuditRepository {

  private final DeletionAuditJpaRepository repository;

  public DeletionAuditRepositoryJpa(DeletionAuditJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public DeletionAudit create(DeletionAudit audit) {
    repository.save(PrivacyJpaMapper.toJpa(audit));
    return audit;
  }

  @Override
  public Page<DeletionAudit> findPage(Query query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("performedAt"), Sort.Order.desc("id")));

    var page = repository.findByApplicationId(query.applicationId().value(), pageable);

    return new Page<>(
        page.getContent().stream().map(PrivacyJpaMapper::toDomain).toList(),
        page.getTotalElements());
  }
}

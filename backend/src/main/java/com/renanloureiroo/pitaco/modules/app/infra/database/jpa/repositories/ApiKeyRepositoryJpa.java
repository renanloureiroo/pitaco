package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApiKeyJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class ApiKeyRepositoryJpa implements ApiKeyRepository {

  private final ApiKeyJpaRepository repository;

  public ApiKeyRepositoryJpa(ApiKeyJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public ApiKey create(ApiKey apiKey) {
    return ApiKeyJpaMapper.toDomain(repository.save(ApiKeyJpaMapper.toJpa(apiKey)));
  }

  @Override
  public Optional<ApiKey> findByIdAndApplicationId(ApiKeyId id, ApplicationId applicationId) {
    return repository
        .findByIdAndApplicationId(id.value(), applicationId.value())
        .map(ApiKeyJpaMapper::toDomain);
  }

  @Override
  public boolean revoke(ApiKey apiKey) {
    return repository.revoke(apiKey.id().value(), apiKey.revokedAt().orElseThrow()) > 0;
  }

  @Override
  public Page<ApiKey> findPage(Query query) {
    var page = pageOf(query);

    return new Page<>(
        page.getContent().stream().map(ApiKeyJpaMapper::toDomain).toList(),
        page.getTotalElements());
  }

  private org.springframework.data.domain.Page<ApiKeyJpaEntity> pageOf(Query query) {
    var applicationId = query.applicationId().value();
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    if (query.status().isEmpty()) {
      return repository.findByApplicationId(applicationId, pageable);
    }

    return query.status().get() == ApiKeyStatus.ACTIVE
        ? repository.findByApplicationIdAndRevokedAtIsNull(applicationId, pageable)
        : repository.findByApplicationIdAndRevokedAtIsNotNull(applicationId, pageable);
  }
}

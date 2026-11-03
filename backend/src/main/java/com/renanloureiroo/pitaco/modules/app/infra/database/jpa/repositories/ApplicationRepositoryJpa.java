package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationRepositoryJpa implements ApplicationRepository {

  private final ApplicationJpaRepository repository;

  public ApplicationRepositoryJpa(ApplicationJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Application create(Application application) {
    return save(application);
  }

  @Override
  public Application update(Application application) {
    return save(application);
  }

  @Override
  public Optional<Application> findBySlug(Slug slug) {
    return repository.findBySlug(slug.value()).map(ApplicationJpaMapper::toDomain);
  }

  @Override
  public Optional<Application> findById(ApplicationId applicationId) {
    return repository.findById(applicationId.value()).map(ApplicationJpaMapper::toDomain);
  }

  @Override
  public Page<Application> findPage(Query query) {
    var page = pageOf(query);

    return new Page<>(
        page.getContent().stream().map(ApplicationJpaMapper::toDomain).toList(),
        page.getTotalElements());
  }

  private org.springframework.data.domain.Page<ApplicationJpaEntity> pageOf(Query query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    return query
        .status()
        .map(status -> repository.findByStatus(status, pageable))
        .orElseGet(() -> repository.findAll(pageable));
  }

  private Application save(Application application) {
    var saved = repository.save(ApplicationJpaMapper.toJpa(application));
    return ApplicationJpaMapper.toDomain(saved);
  }
}

package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEventId;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.ObservedEventMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class ObservedEventRepositoryJpa implements ObservedEventRepository {

  // Mesma ordem de grandeza do registro de último uso da chave: "visto nos últimos minutos" é
  // tudo que a autoria precisa saber.
  static final Duration REFRESH_INTERVAL = Duration.ofMinutes(5);

  private final ApplicationEventJpaRepository repository;

  public ObservedEventRepositoryJpa(ApplicationEventJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(ApplicationId applicationId, EventName name, Instant now) {
    repository.record(
        ObservedEventId.generate().value(),
        applicationId.value(),
        name.value(),
        now,
        now.minus(REFRESH_INTERVAL));
  }

  @Override
  public Optional<Instant> lastSeenAt(ApplicationId applicationId, EventName name) {
    return repository.findLastSeenAt(applicationId.value(), name.value());
  }

  @Override
  public Page<ObservedEvent> findPage(ListObservedEventsQuery query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.asc("name")));

    var page = repository.findByApplicationId(query.applicationId().value(), pageable);

    return new Page<>(
        page.getContent().stream().map(ObservedEventMapper::toDomain).toList(),
        page.getTotalElements());
  }
}

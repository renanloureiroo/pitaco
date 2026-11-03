package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttributeId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationAttributeJpaEntity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationAttributeValueJpaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ObservedAttributeRepositoryJpa implements ObservedAttributeRepository {

  // O mesmo limiar do catálogo de eventos: "visto nos últimos minutos" basta à autoria.
  static final Duration REFRESH_INTERVAL = ObservedEventRepositoryJpa.REFRESH_INTERVAL;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final ApplicationAttributeJpaRepository attributes;
  private final ApplicationAttributeValueJpaRepository values;

  public ObservedAttributeRepositoryJpa(
      ApplicationAttributeJpaRepository attributes, ApplicationAttributeValueJpaRepository values) {
    this.attributes = attributes;
    this.values = values;
  }

  @Override
  public void record(ApplicationId applicationId, AttributeSnapshot snapshot, Instant now) {
    if (snapshot.values().isEmpty()) {
      return;
    }

    var payload = JSON.writeValueAsString(snapshot.values());
    var threshold = now.minus(REFRESH_INTERVAL);

    attributes.recordNames(applicationId.value(), payload, now, threshold);
    attributes.recordValues(
        applicationId.value(),
        payload,
        now,
        threshold,
        ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE);
  }

  @Override
  public Page<ObservedAttribute> findPage(ListObservedAttributesQuery query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.asc("name")));

    var page = attributes.findByApplicationId(query.applicationId().value(), pageable);
    var ids = page.getContent().stream().map(ApplicationAttributeJpaEntity::getId).toList();

    var valuesByAttribute =
        ids.isEmpty()
            ? java.util.Map.<String, List<ApplicationAttributeValueJpaEntity>>of()
            : values.findByAttributeIdIn(ids, Sort.by(Sort.Order.asc("value"))).stream()
                .collect(Collectors.groupingBy(ApplicationAttributeValueJpaEntity::getAttributeId));

    return new Page<>(
        page.getContent().stream()
            .map(entity -> toDomain(entity, valuesByAttribute.getOrDefault(entity.getId(), List.of())))
            .toList(),
        page.getTotalElements());
  }

  private static ObservedAttribute toDomain(
      ApplicationAttributeJpaEntity entity, List<ApplicationAttributeValueJpaEntity> values) {
    return ObservedAttribute.restore(
        ObservedAttributeId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        entity.getName(),
        entity.getFirstSeenAt(),
        entity.getLastSeenAt(),
        values.stream()
            .map(value -> new ObservedAttribute.Value(value.getValue(), value.getLastSeenAt()))
            .toList());
  }
}

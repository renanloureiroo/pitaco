package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEventId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryObservedEventRepository implements ObservedEventRepository {

  private record Key(ApplicationId applicationId, EventName name) {}

  private final Map<Key, ObservedEvent> events = new LinkedHashMap<>();

  private boolean failing;

  @Override
  public void record(ApplicationId applicationId, EventName name, Instant now) {
    if (failing) {
      throw new IllegalStateException("catálogo indisponível");
    }

    events.compute(
        new Key(applicationId, name),
        (key, existing) -> {
          if (existing == null) {
            return ObservedEvent.create(applicationId, name, now);
          }
          existing.seenAt(now);
          return existing;
        });
  }

  @Override
  public Optional<Instant> lastSeenAt(ApplicationId applicationId, EventName name) {
    return Optional.ofNullable(events.get(new Key(applicationId, name)))
        .map(ObservedEvent::getLastSeenAt);
  }

  @Override
  public Page<ObservedEvent> findPage(ListObservedEventsQuery query) {
    var matching =
        events.values().stream()
            .filter(event -> event.getApplicationId().equals(query.applicationId()))
            .sorted(
                Comparator.comparing(ObservedEvent::getLastSeenAt)
                    .reversed()
                    .thenComparing(event -> event.getName().value()))
            .toList();

    var items =
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(InMemoryObservedEventRepository::copyOf)
            .toList();

    return new Page<>(items, matching.size());
  }

  public InMemoryObservedEventRepository withSeen(
      ApplicationId applicationId, String name, Instant firstSeenAt, Instant lastSeenAt) {
    var event =
        ObservedEvent.restore(
            ObservedEventId.generate(),
            applicationId,
            EventName.of(name),
            firstSeenAt,
            lastSeenAt);
    events.put(new Key(applicationId, event.getName()), event);
    return this;
  }

  // Simula o catálogo fora do ar: a elegibilidade precisa responder mesmo assim.
  public InMemoryObservedEventRepository failing() {
    this.failing = true;
    return this;
  }

  public List<ObservedEvent> findAll() {
    return List.copyOf(events.values());
  }

  public boolean isEmpty() {
    return events.isEmpty();
  }

  private static ObservedEvent copyOf(ObservedEvent event) {
    return ObservedEvent.restore(
        event.id(),
        event.getApplicationId(),
        event.getName(),
        event.getFirstSeenAt(),
        event.getLastSeenAt());
  }
}

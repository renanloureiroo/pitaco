package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttributeId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class InMemoryObservedAttributeRepository implements ObservedAttributeRepository {

  private record Key(ApplicationId applicationId, String name) {}

  private static final class Entry {
    private final ObservedAttributeId id = ObservedAttributeId.generate();
    private final Instant firstSeenAt;
    private Instant lastSeenAt;
    private final Map<String, Instant> values = new TreeMap<>();

    private Entry(Instant now) {
      this.firstSeenAt = now;
      this.lastSeenAt = now;
    }
  }

  private final Map<Key, Entry> entries = new LinkedHashMap<>();

  private boolean failing;

  @Override
  public void record(ApplicationId applicationId, AttributeSnapshot attributes, Instant now) {
    if (failing) {
      throw new IllegalStateException("catálogo de atributos indisponível");
    }

    attributes
        .values()
        .forEach(
            (name, value) -> {
              var entry =
                  entries.computeIfAbsent(new Key(applicationId, name), key -> new Entry(now));
              if (now.isAfter(entry.lastSeenAt)) {
                entry.lastSeenAt = now;
              }
              if (entry.values.containsKey(value)
                  || entry.values.size() < ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE) {
                entry.values.put(value, now);
              }
            });
  }

  @Override
  public Page<ObservedAttribute> findPage(ListObservedAttributesQuery query) {
    var matching =
        entries.entrySet().stream()
            .filter(entry -> entry.getKey().applicationId().equals(query.applicationId()))
            .map(entry -> toDomain(entry.getKey(), entry.getValue()))
            .sorted(
                Comparator.comparing(ObservedAttribute::getLastSeenAt)
                    .reversed()
                    .thenComparing(ObservedAttribute::getName))
            .toList();

    return new Page<>(
        matching.stream().skip(query.offset()).limit(query.size()).toList(), matching.size());
  }

  public InMemoryObservedAttributeRepository failing() {
    this.failing = true;
    return this;
  }

  public List<ObservedAttribute> findAll() {
    return entries.entrySet().stream()
        .map(entry -> toDomain(entry.getKey(), entry.getValue()))
        .toList();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  private static ObservedAttribute toDomain(Key key, Entry entry) {
    return ObservedAttribute.restore(
        entry.id,
        key.applicationId(),
        key.name(),
        entry.firstSeenAt,
        entry.lastSeenAt,
        entry.values.entrySet().stream()
            .map(value -> new ObservedAttribute.Value(value.getKey(), value.getValue()))
            .toList());
  }
}

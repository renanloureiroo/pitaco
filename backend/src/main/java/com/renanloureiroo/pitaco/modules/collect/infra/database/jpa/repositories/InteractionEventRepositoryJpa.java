package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.InteractionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class InteractionEventRepositoryJpa implements InteractionEventRepository {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String LOCK_PREFIX = "interaction-events|";

  private final SurveyDisplayEventJpaRepository repository;

  public InteractionEventRepositoryJpa(SurveyDisplayEventJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void lockDisplay(DisplayId displayId) {
    repository.lock(LOCK_PREFIX + displayId.value());
  }

  @Override
  public long countByDisplay(DisplayId displayId) {
    return repository.countByDisplayId(displayId.value());
  }

  @Override
  public Set<Integer> storedSeqs(DisplayId displayId, Collection<Integer> seqs) {
    return seqs.isEmpty()
        ? Set.of()
        : new HashSet<>(repository.storedSeqs(displayId.value(), seqs));
  }

  @Override
  public void saveAll(List<InteractionEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    repository.insertAll(JSON.writeValueAsString(events.stream().map(this::rowOf).toList()));
  }

  private Map<String, Object> rowOf(InteractionEvent event) {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", UUID.randomUUID().toString());
    row.put("display_id", event.displayId().value());
    row.put("seq", event.seq());
    row.put("catalog_version", event.catalogVersion());
    row.put("type", event.type().wire());
    row.put("question_key", event.questionKey().map(QuestionKey::value).orElse(null));
    row.put("occurred_at", event.occurredAt().toString());
    row.put("elapsed_ms", event.elapsedMs());
    row.put("received_at", event.receivedAt().toString());
    row.put("data", event.data());
    return row;
  }
}

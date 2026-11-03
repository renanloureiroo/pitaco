package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.modules.collect.application.repositories.InteractionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// O par (exibição, seq) repetido não grava, como o `on conflict do nothing` do adaptador.
public class InMemoryInteractionEventRepository implements InteractionEventRepository {

  private final List<InteractionEvent> events = new ArrayList<>();
  private final List<DisplayId> locks = new ArrayList<>();

  @Override
  public void lockDisplay(DisplayId displayId) {
    locks.add(displayId);
  }

  @Override
  public long countByDisplay(DisplayId displayId) {
    return events.stream().filter(event -> event.displayId().equals(displayId)).count();
  }

  @Override
  public Set<Integer> storedSeqs(DisplayId displayId, Collection<Integer> seqs) {
    return events.stream()
        .filter(event -> event.displayId().equals(displayId))
        .map(InteractionEvent::seq)
        .filter(seqs::contains)
        .collect(Collectors.toSet());
  }

  @Override
  public void saveAll(List<InteractionEvent> incoming) {
    for (var event : incoming) {
      var stored =
          events.stream()
              .anyMatch(
                  existing ->
                      existing.displayId().equals(event.displayId()) && existing.seq() == event.seq());
      if (!stored) {
        events.add(event);
      }
    }
  }

  public List<InteractionEvent> findAll() {
    return List.copyOf(events);
  }

  public List<DisplayId> locks() {
    return List.copyOf(locks);
  }
}

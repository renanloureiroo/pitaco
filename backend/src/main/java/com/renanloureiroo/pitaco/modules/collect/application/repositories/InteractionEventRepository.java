package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionEvent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface InteractionEventRepository {

  // Trava consultiva da transação sobre a exibição: dois lotes simultâneos da mesma exibição
  // passam um de cada vez pela contagem do teto.
  void lockDisplay(DisplayId displayId);

  long countByDisplay(DisplayId displayId);

  Set<Integer> storedSeqs(DisplayId displayId, Collection<Integer> seqs);

  // Um comando para o lote inteiro. O par (exibição, seq) repetido não grava nada: o banco é a
  // última garantia da idempotência.
  void saveAll(List<InteractionEvent> events);
}

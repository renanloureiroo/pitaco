package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
import java.time.Instant;
import java.util.Optional;

public interface ObservedEventRepository {

  // Cria a linha na primeira vez e avança lastSeenAt nas seguintes. A implementação pode
  // amortizar o avanço: o que se promete é "visto recentemente", não o instante exato.
  void record(ApplicationId applicationId, EventName name, Instant now);

  Optional<Instant> lastSeenAt(ApplicationId applicationId, EventName name);

  // items vem ordenado por lastSeenAt desc com desempate por nome asc, recortado em page*size,
  // e total conta o catálogo inteiro da aplicação.
  Page<ObservedEvent> findPage(ListObservedEventsQuery query);

  record ListObservedEventsQuery(ApplicationId applicationId, int page, int size)
      implements PageQuery {}
}

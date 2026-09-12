package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.time.Instant;

public interface ObservedAttributeRepository {

  // Cria nome e valor na primeira vez e avança lastSeenAt nas seguintes, podendo amortizar o
  // avanço. Um valor novo não entra quando o atributo já tem MAX_VALUES_PER_ATTRIBUTE.
  void record(ApplicationId applicationId, AttributeSnapshot attributes, Instant now);

  // items vem ordenado por lastSeenAt desc com desempate por nome asc, com os valores de cada
  // atributo em ordem alfabética; total conta os atributos da aplicação.
  Page<ObservedAttribute> findPage(ListObservedAttributesQuery query);

  record ListObservedAttributesQuery(ApplicationId applicationId, int page, int size)
      implements PageQuery {}
}

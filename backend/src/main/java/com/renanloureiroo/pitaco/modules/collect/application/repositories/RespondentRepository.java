package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.util.Optional;

public interface RespondentRepository {

  Optional<Respondent> findById(RespondentId id, ApplicationId applicationId);

  Optional<Respondent> findByIdentity(ApplicationId applicationId, RespondentIdentity identity);

  Respondent create(Respondent respondent);

  Respondent update(Respondent respondent);

  // items vem ordenado por lastSeenAt desc com desempate por id desc, recortado em page*size, e
  // total conta o conjunto inteiro da aplicação. A página devolve a entidade e não projeção:
  // Respondent não tem coleção associada, então não há N+1 a evitar.
  Page<Respondent> findPage(ListRespondentsQuery query);

  record ListRespondentsQuery(ApplicationId applicationId, int page, int size)
      implements PageQuery {}
}

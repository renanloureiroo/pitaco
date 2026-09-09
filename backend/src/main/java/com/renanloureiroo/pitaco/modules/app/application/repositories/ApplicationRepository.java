package com.renanloureiroo.pitaco.modules.app.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.util.Optional;

public interface ApplicationRepository {
  Application create(Application application);

  Application update(Application application);

  Optional<Application> findBySlug(Slug slug);

  Optional<Application> findById(ApplicationId applicationId);

  // items vem ordenado por createdAt desc com desempate por id desc, recortado em page*size, e
  // total conta o conjunto filtrado inteiro. Página além do fim devolve items vazio, nunca erro.
  Page<Application> findPage(Query query);

  record Query(Optional<Status> status, int page, int size) implements PageQuery {}
}

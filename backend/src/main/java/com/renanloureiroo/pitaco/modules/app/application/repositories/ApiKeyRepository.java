package com.renanloureiroo.pitaco.modules.app.application.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.util.Optional;

public interface ApiKeyRepository {

  ApiKey create(ApiKey apiKey);

  Optional<ApiKey> findByIdAndApplicationId(ApiKeyId id, ApplicationId applicationId);

  // Update condicional: false quando a chave já estava revogada, isto é, alguém revogou antes.
  boolean revoke(ApiKey apiKey);

  // items vem ordenado por createdAt desc com desempate por id desc, recortado em page*size, e
  // total conta o conjunto filtrado inteiro. Página além do fim devolve items vazio, nunca erro.
  Page<ApiKey> findPage(Query query);

  record Query(ApplicationId applicationId, Optional<ApiKeyStatus> status, int page, int size)
      implements PageQuery {}
}

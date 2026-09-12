package com.renanloureiroo.pitaco.modules.privacy.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;

public interface DeletionAuditRepository {

  DeletionAudit create(DeletionAudit audit);

  // Da mais recente para a mais antiga, com o identificador desempatando.
  Page<DeletionAudit> findPage(Query query);

  record Query(ApplicationId applicationId, int page, int size) implements PageQuery {}
}

package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.DeletionAuditRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryDeletionAuditRepository implements DeletionAuditRepository {

  private final List<DeletionAudit> audits = new ArrayList<>();

  public List<DeletionAudit> findAll() {
    return List.copyOf(audits);
  }

  @Override
  public DeletionAudit create(DeletionAudit audit) {
    audits.add(audit);
    return audit;
  }

  @Override
  public Page<DeletionAudit> findPage(Query query) {
    var matching =
        audits.stream()
            .filter(audit -> audit.getApplicationId().equals(query.applicationId()))
            .sorted(
                Comparator.comparing(DeletionAudit::getPerformedAt)
                    .thenComparing(audit -> audit.id().value())
                    .reversed())
            .toList();

    return new Page<>(
        matching.stream().skip(query.offset()).limit(query.size()).toList(), matching.size());
  }
}

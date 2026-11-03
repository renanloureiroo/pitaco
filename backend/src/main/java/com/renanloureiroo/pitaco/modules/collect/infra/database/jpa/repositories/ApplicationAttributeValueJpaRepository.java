package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationAttributeValueJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAttributeValueJpaRepository
    extends JpaRepository<ApplicationAttributeValueJpaEntity, String> {

  List<ApplicationAttributeValueJpaEntity> findByAttributeIdIn(
      Collection<String> attributeIds, Sort sort);
}

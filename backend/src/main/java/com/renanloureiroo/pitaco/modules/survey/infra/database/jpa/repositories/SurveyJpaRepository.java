package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyJpaRepository extends JpaRepository<SurveyJpaEntity, String> {

  Optional<SurveyJpaEntity> findByIdAndApplicationId(String id, String applicationId);

  Page<SurveyJpaEntity> findByApplicationId(String applicationId, Pageable pageable);
}

package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyAnswerJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyAnswerJpaRepository extends JpaRepository<SurveyAnswerJpaEntity, String> {

  List<SurveyAnswerJpaEntity> findByDisplayId(String displayId);
}

package com.renanloureiroo.pitaco.modules.survey.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.util.Optional;

public interface SurveyRepository {

  Survey create(Survey survey);

  Optional<Survey> findByIdAndApplicationId(SurveyId id, ApplicationId applicationId);

  // items vem ordenado por createdAt desc com desempate por id desc, recortado em page*size, e
  // total conta o conjunto inteiro da aplicação. Página além do fim devolve items vazio.
  Page<Survey> findPage(ListSurveysQuery query);

  Survey update(Survey survey);

  void delete(SurveyId id);

  record ListSurveysQuery(ApplicationId applicationId, int page, int size) implements PageQuery {}
}

package com.renanloureiroo.pitaco.modules.survey.application.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Toda leitura devolve a versão inteira — perguntas, opções e regras na mesma consulta. A porta
// nunca devolve versão pela metade, e nada é buscado dentro de laço.
public interface SurveyVersionRepository {

  SurveyVersion create(SurveyVersion version);

  Optional<SurveyVersion> findDraft(SurveyId surveyId);

  Optional<SurveyVersion> findByNumber(SurveyId surveyId, int number);

  Optional<SurveyVersion> findPublished(SurveyId surveyId);

  Page<SurveyVersion> findPage(ListSurveyVersionsQuery query);

  List<SurveyVersion> findAllPublished(SurveyId surveyId);

  // A listagem deriva o estado de cada pesquisa a partir da janela da versão publicada. Uma
  // consulta para a página inteira, não uma por linha (Princípio V).
  Map<SurveyId, TriggerWindow> findPublishedWindows(List<SurveyId> surveyIds);

  SurveyVersion update(SurveyVersion version);

  void delete(SurveyVersionId id);

  void deleteBySurveyId(SurveyId surveyId);

  record ListSurveyVersionsQuery(SurveyId surveyId, int page, int size) implements PageQuery {}
}

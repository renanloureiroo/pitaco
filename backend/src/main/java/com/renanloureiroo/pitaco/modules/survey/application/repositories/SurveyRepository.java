package com.renanloureiroo.pitaco.modules.survey.application.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository {

  Survey create(Survey survey);

  Optional<Survey> findByIdAndApplicationId(SurveyId id, ApplicationId applicationId);

  // items vem ordenado por createdAt desc com desempate por id desc, recortado em page*size, e
  // total conta o conjunto inteiro da aplicação. Página além do fim devolve items vazio.
  Page<Survey> findPage(ListSurveysQuery query);

  Survey update(Survey survey);

  // A linha fica travada até o fim da transação: quem edita a pesquisa não sobrescreve um
  // encerramento por cota gravado entre a leitura e a escrita.
  Optional<Survey> lockByIdAndApplicationId(SurveyId id, ApplicationId applicationId);

  // Encerra só se ainda estiver no ar ou pausada, e diz se foi esta chamada que encerrou.
  boolean endIfLive(SurveyId id);

  // No ar ou agendadas cuja versão publicada escuta o evento e cuja janela ainda não fechou —
  // as que disputariam o mesmo respondente. Pausada fica de fora: não disputa enquanto pausada.
  List<Survey> findLiveListeningTo(ApplicationId applicationId, EventName event, Instant now);

  void delete(SurveyId id);

  record ListSurveysQuery(ApplicationId applicationId, int page, int size) implements PageQuery {}
}

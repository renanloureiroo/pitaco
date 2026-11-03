package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SurveyDisplayRepository {

  Optional<SurveyDisplay> findById(DisplayId id, ApplicationId applicationId);

  SurveyDisplay create(SurveyDisplay display);

  SurveyDisplay update(SurveyDisplay display);

  // Uma consulta para todos os candidatos de uma vez. `outcome` vem como está gravado; ABANDONED
  // é derivado por ResolvedHistory.
  List<DisplayHistoryEntry> historyOf(RespondentId respondentId, List<SurveyId> surveyIds);

  // A exibição mais recente do respondente em qualquer pesquisa: é dela que o descanso conta.
  Optional<Instant> lastOpenedAt(RespondentId respondentId);

  // Concluídas da pesquisa em todas as versões: é o que a cota mede.
  long countCompleted(SurveyId surveyId);

  // Todas as exibições abertas no período, qualquer desfecho; inclusivo nos dois extremos.
  long countOpenedBetween(SurveyId surveyId, Instant from, Instant to);

  // items ordenado por openedAt desc com desempate por id desc, recortado em page*size; total
  // conta o conjunto inteiro que atende ao filtro. Página além do fim devolve items vazio, nunca
  // erro. Filtro ausente não restringe. O período é inclusivo nos dois extremos.
  Page<DisplaySummary> findPage(ListDisplaysQuery query);

  Page<RespondentDisplaySummary> findPageByRespondent(ListRespondentDisplaysQuery query);

  record DisplayHistoryEntry(
      SurveyId surveyId, int comparabilityGroup, DisplayOutcome outcome, Instant openedAt) {}

  // Projeção e não entidade: montar SurveyDisplay exigiria o instantâneo de atributos, uma
  // consulta por linha, e a listagem não pede atributo nenhum (D-02).
  record DisplaySummary(
      DisplayId id,
      SurveyVersionId versionId,
      int versionNumber,
      int comparabilityGroup,
      DisplayOutcome outcome,
      Optional<String> sdkVersion,
      Instant openedAt,
      Optional<Instant> closedAt) {}

  record RespondentDisplaySummary(SurveyId surveyId, DisplaySummary display) {}

  record ListDisplaysQuery(
      ApplicationId applicationId,
      SurveyId surveyId,
      Optional<Integer> versionNumber,
      Optional<DisplayOutcome> outcome,
      Optional<Instant> openedFrom,
      Optional<Instant> openedTo,
      int page,
      int size)
      implements PageQuery {}

  record ListRespondentDisplaysQuery(
      ApplicationId applicationId,
      RespondentId respondentId,
      Optional<DisplayOutcome> outcome,
      Optional<Instant> openedFrom,
      Optional<Instant> openedTo,
      int page,
      int size)
      implements PageQuery {}
}

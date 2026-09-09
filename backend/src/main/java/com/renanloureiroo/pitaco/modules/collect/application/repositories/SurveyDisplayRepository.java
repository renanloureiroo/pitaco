package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
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

  record DisplayHistoryEntry(
      SurveyId surveyId, int comparabilityGroup, DisplayOutcome outcome, Instant openedAt) {}
}

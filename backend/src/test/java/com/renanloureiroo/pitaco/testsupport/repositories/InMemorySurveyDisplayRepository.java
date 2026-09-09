package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySurveyDisplayRepository implements SurveyDisplayRepository {

  private final Map<DisplayId, SurveyDisplay> displays = new LinkedHashMap<>();

  private int historyCalls;

  @Override
  public Optional<SurveyDisplay> findById(DisplayId id, ApplicationId applicationId) {
    return Optional.ofNullable(displays.get(id))
        .filter(display -> display.getApplicationId().equals(applicationId))
        .map(InMemorySurveyDisplayRepository::copyOf);
  }

  @Override
  public SurveyDisplay create(SurveyDisplay display) {
    displays.put(display.id(), copyOf(display));
    return display;
  }

  @Override
  public SurveyDisplay update(SurveyDisplay display) {
    displays.put(display.id(), copyOf(display));
    return display;
  }

  // Uma varredura só, como a consulta do adaptador: é o que o teste do caso de uso verifica.
  @Override
  public List<DisplayHistoryEntry> historyOf(RespondentId respondentId, List<SurveyId> surveyIds) {
    historyCalls++;

    return displays.values().stream()
        .filter(display -> display.getRespondentId().equals(respondentId))
        .filter(display -> surveyIds.contains(display.getSurveyId()))
        .map(
            display ->
                new DisplayHistoryEntry(
                    display.getSurveyId(),
                    display.getComparabilityGroup(),
                    display.getOutcome(),
                    display.getOpenedAt()))
        .toList();
  }

  public int historyCalls() {
    return historyCalls;
  }

  public List<SurveyDisplay> findAll() {
    return List.copyOf(displays.values());
  }

  public boolean isEmpty() {
    return displays.isEmpty();
  }

  private static SurveyDisplay copyOf(SurveyDisplay display) {
    return SurveyDisplay.restore(
        display.id(),
        display.getApplicationId(),
        display.getRespondentId(),
        display.getSurveyId(),
        display.getVersionId(),
        display.getComparabilityGroup(),
        display.getOutcome(),
        display.sdkVersion(),
        display.getAttributes(),
        display.getOpenedAt(),
        display.closedAt());
  }
}

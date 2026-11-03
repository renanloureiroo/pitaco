package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository.DisplayHistoryEntry;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

// O abandono é derivado aqui, na leitura: a coluna guarda só STARTED, COMPLETED e DISMISSED, e
// uma exibição iniciada além do prazo vira abandono sem ninguém ter escrito nada (D-11, D-12).
public final class ResolvedHistory {

  private final List<Resolved> entries;
  private final int maxAttempts;

  private record Resolved(SurveyId surveyId, int comparabilityGroup, DisplayOutcome outcome) {}

  private ResolvedHistory(List<Resolved> entries, int maxAttempts) {
    this.entries = entries;
    this.maxAttempts = maxAttempts;
  }

  public static ResolvedHistory of(
      List<DisplayHistoryEntry> history, Instant now, Duration displayTimeout, int maxAttempts) {
    return new ResolvedHistory(
        history.stream()
            .map(
                entry ->
                    new Resolved(
                        entry.surveyId(),
                        entry.comparabilityGroup(),
                        outcomeAt(entry, now, displayTimeout)))
            .toList(),
        maxAttempts);
  }

  // Resolvida é a que teve desfecho: respondeu ou dispensou. Abandono não resolve nada.
  public boolean isResolved(SurveyId surveyId, int comparabilityGroup) {
    return matching(surveyId, comparabilityGroup).anyMatch(entry -> entry.outcome().isFinal());
  }

  public boolean exhaustedAttempts(SurveyId surveyId, int comparabilityGroup) {
    return matching(surveyId, comparabilityGroup)
            .filter(entry -> entry.outcome() == DisplayOutcome.ABANDONED)
            .count()
        >= maxAttempts;
  }

  private Stream<Resolved> matching(SurveyId surveyId, int comparabilityGroup) {
    return entries.stream()
        .filter(entry -> entry.surveyId().equals(surveyId))
        .filter(entry -> entry.comparabilityGroup() == comparabilityGroup);
  }

  private static DisplayOutcome outcomeAt(
      DisplayHistoryEntry entry, Instant now, Duration displayTimeout) {
    if (entry.outcome() != DisplayOutcome.STARTED) {
      return entry.outcome();
    }
    return now.isBefore(entry.openedAt().plus(displayTimeout))
        ? DisplayOutcome.STARTED
        : DisplayOutcome.ABANDONED;
  }
}

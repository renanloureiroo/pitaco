package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionStore;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.ExpiringAnswer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// As mesmas regras das consultas: ordem de resposta, limite por lote, e texto apagado sem apagar
// a resposta.
public class InMemoryRetentionStore implements RetentionStore {

  public record StoredAnswer(
      ApplicationId applicationId, ExpiringAnswer answer, Instant answeredAt, Optional<String> text) {}

  private final List<StoredAnswer> answers = new ArrayList<>();
  private final List<Integer> deleteBatches = new ArrayList<>();

  public ExpiringAnswer withAnswer(
      ApplicationId applicationId,
      SurveyId surveyId,
      SurveyVersionId versionId,
      String displayId,
      QuestionKey key,
      String status,
      Optional<Integer> number,
      List<String> options,
      Optional<String> text,
      Instant answeredAt) {
    var answer =
        new ExpiringAnswer(
            UUID.randomUUID().toString(), surveyId, versionId, displayId, key, status, number, options);
    answers.add(new StoredAnswer(applicationId, answer, answeredAt, text));
    return answer;
  }

  public List<StoredAnswer> findAll() {
    return List.copyOf(answers);
  }

  public List<Integer> deleteBatches() {
    return List.copyOf(deleteBatches);
  }

  private List<StoredAnswer> before(ApplicationId applicationId, Instant before) {
    return answers.stream()
        .filter(stored -> stored.applicationId().equals(applicationId))
        .filter(stored -> stored.answeredAt().isBefore(before))
        .sorted(
            Comparator.comparing(StoredAnswer::answeredAt)
                .thenComparing(stored -> stored.answer().answerId()))
        .toList();
  }

  @Override
  public List<ExpiringAnswer> expiringAnswers(ApplicationId applicationId, Instant before, int limit) {
    return before(applicationId, before).stream().limit(limit).map(StoredAnswer::answer).toList();
  }

  @Override
  public int deleteAnswers(List<String> answerIds) {
    Set<String> ids = new HashSet<>(answerIds);
    var removed = (int) answers.stream().filter(stored -> ids.contains(stored.answer().answerId())).count();
    answers.removeIf(stored -> ids.contains(stored.answer().answerId()));
    deleteBatches.add(removed);
    return removed;
  }

  @Override
  public int clearTexts(ApplicationId applicationId, Instant before, int limit) {
    var targets =
        before(applicationId, before).stream()
            .filter(stored -> stored.text().isPresent())
            .limit(limit)
            .toList();
    targets.forEach(
        stored -> {
          answers.remove(stored);
          answers.add(new StoredAnswer(stored.applicationId(), stored.answer(), stored.answeredAt(), Optional.empty()));
        });
    return targets.size();
  }

  @Override
  public long countAnswersBefore(ApplicationId applicationId, Instant before) {
    return before(applicationId, before).size();
  }

  @Override
  public long countTextsBefore(ApplicationId applicationId, Instant before) {
    return before(applicationId, before).stream().filter(stored -> stored.text().isPresent()).count();
  }
}

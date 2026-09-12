package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionStore;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.ExpiringAnswer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RetentionStoreJpa implements RetentionStore {

  private static final String OPTION_SEPARATOR = String.valueOf((char) 31);

  private final RetentionJpaRepository repository;

  public RetentionStoreJpa(RetentionJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<ExpiringAnswer> expiringAnswers(
      ApplicationId applicationId, Instant before, int limit) {
    return repository.expiringAnswers(applicationId.value(), before, limit).stream()
        .map(
            row ->
                new ExpiringAnswer(
                    (String) row[0],
                    SurveyId.of((String) row[1]),
                    SurveyVersionId.of((String) row[2]),
                    (String) row[3],
                    QuestionKey.of((String) row[4]),
                    (String) row[5],
                    Optional.ofNullable(row[6]).map(value -> ((Number) value).intValue()),
                    row[7] == null ? List.of() : List.of(((String) row[7]).split(OPTION_SEPARATOR))))
        .toList();
  }

  @Override
  public int deleteAnswers(List<String> answerIds) {
    return answerIds.isEmpty() ? 0 : repository.deleteAnswers(answerIds);
  }

  @Override
  public int clearTexts(ApplicationId applicationId, Instant before, int limit) {
    return repository.clearTexts(applicationId.value(), before, limit);
  }

  @Override
  public long countAnswersBefore(ApplicationId applicationId, Instant before) {
    return repository.countAnswersBefore(applicationId.value(), before);
  }

  @Override
  public long countTextsBefore(ApplicationId applicationId, Instant before) {
    return repository.countTextsBefore(applicationId.value(), before);
  }
}

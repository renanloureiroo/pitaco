package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionRunRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryRetentionRunRepository implements RetentionRunRepository {

  private final List<RetentionRun> runs = new ArrayList<>();

  public List<RetentionRun> findAll() {
    return List.copyOf(runs);
  }

  @Override
  public RetentionRun create(RetentionRun run) {
    runs.add(run);
    return run;
  }

  @Override
  public Optional<Instant> lastRunAt(ApplicationId applicationId) {
    return runs.stream()
        .filter(run -> run.getApplicationId().equals(applicationId))
        .map(RetentionRun::getRanAt)
        .max(Comparator.naturalOrder());
  }
}

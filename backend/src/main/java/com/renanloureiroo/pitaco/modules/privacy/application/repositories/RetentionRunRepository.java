package com.renanloureiroo.pitaco.modules.privacy.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import java.time.Instant;
import java.util.Optional;

public interface RetentionRunRepository {

  RetentionRun create(RetentionRun run);

  Optional<Instant> lastRunAt(ApplicationId applicationId);
}

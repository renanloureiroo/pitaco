package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.util.Optional;

public interface RespondentRepository {

  Optional<Respondent> findByIdentity(ApplicationId applicationId, RespondentIdentity identity);

  Respondent create(Respondent respondent);

  Respondent update(Respondent respondent);
}

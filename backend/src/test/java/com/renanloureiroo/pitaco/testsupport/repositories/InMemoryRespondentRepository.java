package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryRespondentRepository implements RespondentRepository {

  private final Map<RespondentId, Respondent> respondents = new LinkedHashMap<>();

  @Override
  public Optional<Respondent> findByIdentity(
      ApplicationId applicationId, RespondentIdentity identity) {
    return respondents.values().stream()
        .filter(respondent -> respondent.getApplicationId().equals(applicationId))
        .filter(respondent -> respondent.getIdentity().equals(identity))
        .findFirst()
        .map(InMemoryRespondentRepository::copyOf);
  }

  @Override
  public Respondent create(Respondent respondent) {
    respondents.put(respondent.id(), copyOf(respondent));
    return respondent;
  }

  @Override
  public Respondent update(Respondent respondent) {
    respondents.put(respondent.id(), copyOf(respondent));
    return respondent;
  }

  public List<Respondent> findAll() {
    return List.copyOf(respondents.values());
  }

  public boolean isEmpty() {
    return respondents.isEmpty();
  }

  private static Respondent copyOf(Respondent respondent) {
    return Respondent.restore(
        respondent.id(),
        respondent.getApplicationId(),
        respondent.getIdentity(),
        respondent.getFirstSeenAt(),
        respondent.getLastSeenAt());
  }
}

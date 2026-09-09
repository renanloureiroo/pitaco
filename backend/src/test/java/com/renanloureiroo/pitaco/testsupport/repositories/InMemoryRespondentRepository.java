package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryRespondentRepository implements RespondentRepository {

  private final Map<RespondentId, Respondent> respondents = new LinkedHashMap<>();

  @Override
  public Optional<Respondent> findById(RespondentId id, ApplicationId applicationId) {
    return Optional.ofNullable(respondents.get(id))
        .filter(respondent -> respondent.getApplicationId().equals(applicationId))
        .map(InMemoryRespondentRepository::copyOf);
  }

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

  @Override
  public Page<Respondent> findPage(ListRespondentsQuery query) {
    var matching =
        respondents.values().stream()
            .filter(respondent -> respondent.getApplicationId().equals(query.applicationId()))
            .sorted(
                Comparator.comparing(Respondent::getLastSeenAt)
                    .thenComparing(respondent -> respondent.id().value())
                    .reversed())
            .toList();

    var items =
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(InMemoryRespondentRepository::copyOf)
            .toList();

    return new Page<>(items, matching.size());
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

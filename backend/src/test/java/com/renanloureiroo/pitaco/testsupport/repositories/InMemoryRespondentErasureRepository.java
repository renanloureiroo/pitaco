package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RespondentErasureRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects.ErasureTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemoryRespondentErasureRepository implements RespondentErasureRepository {

  private record Stored(
      String respondentId, ApplicationId applicationId, ErasureTarget target, int displays, int answers) {}

  private final List<Stored> respondents = new ArrayList<>();
  private final List<String> erased = new ArrayList<>();

  public String withRespondent(
      ApplicationId applicationId, ErasureTarget target, int displays, int answers) {
    var id = UUID.randomUUID().toString();
    respondents.add(new Stored(id, applicationId, target, displays, answers));
    return id;
  }

  public List<String> erased() {
    return List.copyOf(erased);
  }

  @Override
  public Optional<ErasableRespondent> find(ApplicationId applicationId, ErasureTarget target) {
    return respondents.stream()
        .filter(stored -> stored.applicationId().equals(applicationId))
        .filter(stored -> stored.target().equals(target))
        .findFirst()
        .map(stored -> new ErasableRespondent(stored.respondentId(), stored.displays(), stored.answers()));
  }

  @Override
  public void erase(String respondentId) {
    respondents.removeIf(stored -> stored.respondentId().equals(respondentId));
    erased.add(respondentId);
  }
}

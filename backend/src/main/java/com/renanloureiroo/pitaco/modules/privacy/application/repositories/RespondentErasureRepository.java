package com.renanloureiroo.pitaco.modules.privacy.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects.ErasureTarget;
import java.util.Optional;

public interface RespondentErasureRepository {

  // O respondente e quanto dele existe, numa leitura só, antes de apagar.
  Optional<ErasableRespondent> find(ApplicationId applicationId, ErasureTarget target);

  // A cascata do schema leva exibições, respostas, atributos da exibição e supressões.
  void erase(String respondentId);

  record ErasableRespondent(String respondentId, int displays, int answers) {}
}

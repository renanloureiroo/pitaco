package com.renanloureiroo.pitaco.modules.app.application.repositories;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {
  Application create(Application application);

  Application update(Application application);

  Optional<Application> findBySlug(Slug slug);

  Optional<Application> findById(ApplicationId applicationId);

  List<Application> findAll();
}

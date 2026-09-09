package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryApplicationRepository implements ApplicationRepository {

  private final Map<ApplicationId, Application> applications = new LinkedHashMap<>();

  @Override
  public Application create(Application application) {
    applications.put(application.id(), application);
    return application;
  }

  @Override
  public Application update(Application application) {
    applications.put(application.id(), application);
    return application;
  }

  @Override
  public Optional<Application> findBySlug(Slug slug) {
    return applications.values().stream()
        .filter(application -> application.getSlug().equals(slug))
        .findFirst();
  }

  @Override
  public Optional<Application> findById(ApplicationId applicationId) {
    return Optional.ofNullable(applications.get(applicationId));
  }

  @Override
  public Page<Application> findPage(Query query) {
    var matching =
        applications.values().stream()
            .filter(application -> query.status().map(application.getStatus()::equals).orElse(true))
            .sorted(
                Comparator.comparing(Application::getCreatedAt)
                    .thenComparing(application -> application.id().value())
                    .reversed())
            .toList();

    var items = matching.stream().skip(query.offset()).limit(query.size()).toList();

    return new Page<>(items, matching.size());
  }

  // Espião de teste, não contrato: a porta não expõe coleção ilimitada.
  public List<Application> findAll() {
    return List.copyOf(applications.values());
  }
}

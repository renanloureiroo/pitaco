package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public final class ApplicationFactory {

  public static final String DEFAULT_NAME = "Acme App";
  public static final Instant BATCH_FIRST_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private String name = DEFAULT_NAME;
  private String slug;
  private Integer quietPeriodDays;
  private Integer retentionDays;
  private Integer openTextRetentionDays;
  private boolean active = true;
  private Instant createdAt;
  private ApplicationId id;

  private ApplicationFactory() {}

  public static ApplicationFactory anApplication() {
    return new ApplicationFactory();
  }

  public static ApplicationFactory anApplicationWithPolicies() {
    return anApplication().withQuietPeriod(15).withRetention(180).withOpenTextRetention(30);
  }

  public ApplicationFactory withName(String name) {
    this.name = name;
    return this;
  }

  public ApplicationFactory withSlug(String slug) {
    this.slug = slug;
    return this;
  }

  public ApplicationFactory withQuietPeriod(Integer days) {
    this.quietPeriodDays = days;
    return this;
  }

  public ApplicationFactory withRetention(Integer days) {
    this.retentionDays = days;
    return this;
  }

  public ApplicationFactory withOpenTextRetention(Integer days) {
    this.openTextRetentionDays = days;
    return this;
  }

  public ApplicationFactory createdAt(Instant createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public ApplicationFactory withId(ApplicationId id) {
    this.id = id;
    return this;
  }

  public ApplicationFactory inactive() {
    this.active = false;
    return this;
  }

  public Slug slug() {
    return slug != null ? Slug.of(slug) : Slug.from(name);
  }

  // Com createdAt ou id controlados o caminho é restore: create carimba Instant.now() e gera o
  // identificador, e a ordenação com desempate deixaria de ser verificável.
  public Application build() {
    if (createdAt == null && id == null) {
      var application =
          Application.create(
              slug(), Name.of(name), quietPeriodDays, retentionDays, openTextRetentionDays);

      if (!active) {
        application.deactivate();
      }

      return application;
    }

    var created = createdAt == null ? BATCH_FIRST_CREATED_AT : createdAt;

    return Application.restore(
        id == null ? ApplicationId.generate() : id,
        slug(),
        Name.of(name),
        active ? Status.ACTIVE : Status.INACTIVE,
        quietPeriodDays,
        retentionDays,
        openTextRetentionDays,
        created,
        created);
  }

  // Lote em ordem crescente de criação: a listagem deve devolvê-lo exatamente ao contrário.
  public List<Application> buildBatchSavedIn(ApplicationRepository repository, int count) {
    return IntStream.range(0, count)
        .mapToObj(
            position ->
                anApplication()
                    .withName("App " + position)
                    .createdAt(BATCH_FIRST_CREATED_AT.plusSeconds(position))
                    .buildSavedIn(repository))
        .toList();
  }

  public Application buildSavedIn(ApplicationRepository repository) {
    return repository.create(build());
  }

  public CreateApplicationUseCase.Input asCreateInput() {
    return new CreateApplicationUseCase.Input(
        name, slug, quietPeriodDays, retentionDays, openTextRetentionDays);
  }
}

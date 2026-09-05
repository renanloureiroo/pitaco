package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;

public final class ApplicationFactory {

  public static final String DEFAULT_NAME = "Acme App";

  private String name = DEFAULT_NAME;
  private String slug;
  private Integer quietPeriodDays;
  private Integer retentionDays;
  private Integer openTextRetentionDays;
  private boolean active = true;

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

  public ApplicationFactory inactive() {
    this.active = false;
    return this;
  }

  public Slug slug() {
    return slug != null ? Slug.of(slug) : Slug.from(name);
  }

  public Application build() {
    var application =
        Application.create(
            slug(), Name.of(name), quietPeriodDays, retentionDays, openTextRetentionDays);

    if (!active) {
      application.deactivate();
    }

    return application;
  }

  public Application buildSavedIn(ApplicationRepository repository) {
    return repository.create(build());
  }

  public CreateApplicationUseCase.Input asCreateInput() {
    return new CreateApplicationUseCase.Input(
        name, slug, quietPeriodDays, retentionDays, openTextRetentionDays);
  }
}

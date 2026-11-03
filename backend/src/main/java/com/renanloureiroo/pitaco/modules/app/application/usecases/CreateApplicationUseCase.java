package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationAlreadyExistsWithSameSlug;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateApplicationUseCase
    implements UseCase<CreateApplicationUseCase.Input, CreateApplicationUseCase.Output> {

  private final ApplicationRepository applicationRepository;

  public CreateApplicationUseCase(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @Override
  public Output execute(Input input) {
    var slug = input.slug() != null ? Slug.of(input.slug()) : Slug.from(input.name());

    if (applicationRepository.findBySlug(slug).isPresent()) {
      throw new ApplicationAlreadyExistsWithSameSlug(slug);
    }

    var newApplication =
        Application.create(
            slug,
            Name.of(input.name()),
            input.quietPeriodDays(),
            input.retentionDays(),
            input.openTextRetentionDays());

    applicationRepository.create(newApplication);

    log.info(
        "Aplicação criada [{}] slug={}", newApplication.id().value(), newApplication.getSlug());

    return new Output(newApplication.id().value(), newApplication.getSlug().value());
  }

  public record Input(
      String name,
      String slug,
      Integer quietPeriodDays,
      Integer retentionDays,
      Integer openTextRetentionDays) {}

  public record Output(String id, String slug) {}
}

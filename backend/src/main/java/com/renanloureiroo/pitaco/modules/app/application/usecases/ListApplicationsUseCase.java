package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListApplicationsUseCase
    implements UseCase<ListApplicationsUseCase.Input, ListApplicationsUseCase.Output> {

  private final ApplicationRepository applications;

  public ListApplicationsUseCase(ApplicationRepository applications) {
    this.applications = applications;
  }

  @Override
  public Output execute(Input input) {
    var page =
        applications.findPage(
            new ApplicationRepository.Query(input.status(), input.page(), input.size()));

    log.info(
        "Aplicações listadas status={} total={}",
        input.status().map(Enum::name).orElse("ALL"),
        page.total());

    return new Output(
        page.map(ListApplicationsUseCase::itemOf).items(),
        input.page(),
        input.size(),
        page.total(),
        page.totalPages(input.size()));
  }

  private static Item itemOf(Application application) {
    return new Item(
        application.id().value(),
        application.getSlug().value(),
        application.getName().value(),
        application.getStatus(),
        application.getCreatedAt());
  }

  public record Input(Optional<Status> status, int page, int size) {}

  public record Output(List<Item> items, int page, int size, long total, int totalPages) {}

  public record Item(String id, String slug, String name, Status status, Instant createdAt) {}
}

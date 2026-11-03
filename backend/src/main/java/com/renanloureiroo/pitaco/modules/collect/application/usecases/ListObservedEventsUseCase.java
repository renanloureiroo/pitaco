package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedEventOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListObservedEventsUseCase
    implements UseCase<ListObservedEventsUseCase.Input, ListObservedEventsUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final ObservedEventRepository events;

  public ListObservedEventsUseCase(
      ApplicationScopeGateway applications, ObservedEventRepository events) {
    this.applications = applications;
    this.events = events;
  }

  @Override
  public Output execute(Input input) {
    // Só a existência: aplicação inativa não recebe eventos novos, mas o catálogo continua
    // servindo à autoria.
    var applicationId =
        CollectScope.existingApplicationIdOf(applications, input.applicationId());

    var page =
        events.findPage(
            new ObservedEventRepository.ListObservedEventsQuery(
                applicationId, input.page(), input.size()));

    var items = page.items().stream().map(ObservedEventOutput::of).toList();

    log.info(
        "Eventos observados listados application={} total={}",
        applicationId.value(),
        page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  public record Input(String applicationId, int page, int size) {}

  public record Output(
      List<ObservedEventOutput> items, int page, int size, long total, int totalPages) {}
}

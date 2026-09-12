package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedAttributeOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListObservedAttributesUseCase
    implements UseCase<ListObservedAttributesUseCase.Input, ListObservedAttributesUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final ObservedAttributeRepository attributes;

  public ListObservedAttributesUseCase(
      ApplicationScopeGateway applications, ObservedAttributeRepository attributes) {
    this.applications = applications;
    this.attributes = attributes;
  }

  @Override
  public Output execute(Input input) {
    // Só a existência, como no catálogo de eventos: aplicação inativa não recebe atributos
    // novos, mas o que já foi visto continua servindo à autoria.
    var applicationId =
        CollectScope.existingApplicationIdOf(applications, input.applicationId());

    var page =
        attributes.findPage(
            new ObservedAttributeRepository.ListObservedAttributesQuery(
                applicationId, input.page(), input.size()));

    var items = page.items().stream().map(ObservedAttributeOutput::of).toList();

    log.info(
        "Atributos observados listados application={} total={}",
        applicationId.value(),
        page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  public record Input(String applicationId, int page, int size) {}

  public record Output(
      List<ObservedAttributeOutput> items, int page, int size, long total, int totalPages) {}
}

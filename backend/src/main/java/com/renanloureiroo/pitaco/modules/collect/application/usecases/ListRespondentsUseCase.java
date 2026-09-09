package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListRespondentsUseCase
    implements UseCase<ListRespondentsUseCase.Input, ListRespondentsUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final RespondentRepository respondents;

  public ListRespondentsUseCase(
      ApplicationScopeGateway applications, RespondentRepository respondents) {
    this.applications = applications;
    this.respondents = respondents;
  }

  @Override
  public Output execute(Input input) {
    // Só a existência: aplicação inativa não coleta mais, mas o que já coletou continua legível.
    var applicationId =
        CollectScope.existingApplicationIdOf(applications, input.applicationId());

    var page =
        respondents.findPage(
            new RespondentRepository.ListRespondentsQuery(
                applicationId, input.page(), input.size()));

    var items = page.items().stream().map(RespondentOutput::of).toList();

    // Nunca o identityValue: ele sai no corpo da resposta e em lugar nenhum do log (FR-030).
    log.info(
        "Respondentes listados application={} total={}", applicationId.value(), page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  public record Input(String applicationId, int page, int size) {}

  public record Output(
      List<RespondentOutput> items, int page, int size, long total, int totalPages) {}
}

package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SdkErrorReportOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListSdkErrorsUseCase
    implements UseCase<ListSdkErrorsUseCase.Input, ListSdkErrorsUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final SdkErrorReportRepository reports;

  public ListSdkErrorsUseCase(
      ApplicationScopeGateway applications, SdkErrorReportRepository reports) {
    this.applications = applications;
    this.reports = reports;
  }

  public record Input(
      String applicationId,
      Optional<SdkErrorKind> kind,
      Optional<String> sdkVersion,
      int page,
      int size) {}

  public record Output(
      List<SdkErrorReportOutput> items, int page, int size, long total, int totalPages) {}

  @Override
  public Output execute(Input input) {
    var applicationId = CollectScope.existingApplicationIdOf(applications, input.applicationId());

    var page =
        reports.findPage(
            new SdkErrorReportRepository.ListSdkErrorsQuery(
                applicationId, input.kind(), input.sdkVersion(), input.page(), input.size()));

    log.info(
        "Erros do SDK listados application={} total={}", applicationId.value(), page.total());

    return new Output(
        page.items().stream().map(SdkErrorReportOutput::of).toList(),
        input.page(),
        input.size(),
        page.total(),
        page.totalPages(input.size()));
  }
}

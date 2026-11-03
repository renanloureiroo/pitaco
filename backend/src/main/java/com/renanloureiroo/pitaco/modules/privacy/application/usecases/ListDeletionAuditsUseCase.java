package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.DeletionAuditOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.DeletionAuditRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.services.PrivacyScope;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListDeletionAuditsUseCase
    implements UseCase<ListDeletionAuditsUseCase.Input, ListDeletionAuditsUseCase.Output> {

  private final PrivacyApplicationGateway applications;
  private final DeletionAuditRepository audits;

  public ListDeletionAuditsUseCase(
      PrivacyApplicationGateway applications, DeletionAuditRepository audits) {
    this.applications = applications;
    this.audits = audits;
  }

  public record Input(String applicationId, int page, int size) {}

  public record Output(
      List<DeletionAuditOutput> items, int page, int size, long total, int totalPages) {}

  @Override
  public Output execute(Input input) {
    var applicationId =
        PrivacyScope.existingApplicationOf(applications, input.applicationId()).applicationId();

    var page =
        audits.findPage(
            new DeletionAuditRepository.Query(applicationId, input.page(), input.size()));

    log.info(
        "Registros de exclusão listados application={} total={}",
        applicationId.value(),
        page.total());

    return new Output(
        page.items().stream().map(DeletionAuditOutput::of).toList(),
        input.page(),
        input.size(),
        page.total(),
        page.totalPages(input.size()));
  }
}

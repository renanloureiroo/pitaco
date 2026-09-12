package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorContext;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Aplicação inativa não entrega pesquisa, então o SDK dela não tem o que reportar: o relatório é
// descartado com a mesma resposta de quando é gravado.
@Slf4j
public class ReportSdkErrorUseCase implements UseCaseWithoutOutput<ReportSdkErrorUseCase.Input> {

  private final ApplicationScopeGateway applications;
  private final SdkErrorReportRepository reports;

  public ReportSdkErrorUseCase(
      ApplicationScopeGateway applications, SdkErrorReportRepository reports) {
    this.applications = applications;
    this.reports = reports;
  }

  public record Input(
      String applicationId,
      Optional<String> sdkVersion,
      Optional<String> kind,
      Optional<String> message,
      Map<String, Object> context,
      Optional<Instant> occurredAt) {}

  // Uma escrita só, mas nativa: o contexto vai para jsonb com cast explícito, e comando nativo de
  // escrita exige transação aberta.
  @Override
  @Transactional
  public void execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());

    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      log.info(
          "Relatório de erro do SDK descartado application={} motivo=aplicacao_inativa",
          applicationId.value());
      return;
    }

    var context = SdkErrorContext.sanitize(input.context());
    var report =
        reports.create(
            SdkErrorReport.create(
                applicationId,
                input.sdkVersion().flatMap(SdkVersion::parse),
                SdkErrorKind.fromWire(input.kind().orElse(null)),
                input.message().orElse(null),
                context,
                input.occurredAt(),
                Instant.now()));

    log.info(
        "Relatório de erro do SDK recebido application={} kind={} sdk={} descartados={}",
        applicationId.value(),
        report.getKind().wire(),
        report.sdkVersion().map(SdkVersion::value).orElse("-"),
        context.discarded());
  }
}

package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SdkErrorReportOutput;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorContext;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkErrorReportRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListSdkErrorsUseCase")
class ListSdkErrorsUseCaseTest {

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemorySdkErrorReportRepository reports = new InMemorySdkErrorReportRepository();

  private ListSdkErrorsUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    useCase = new ListSdkErrorsUseCase(applications, reports);
    applicationId = applications.anActiveApplication();
  }

  private void reported(
      ApplicationId owner, SdkErrorKind kind, String version, String message, Instant at) {
    reports.with(
        SdkErrorReport.create(
            owner,
            Optional.of(SdkVersion.of(version)),
            kind,
            message,
            SdkErrorContext.empty(),
            Optional.empty(),
            at));
  }

  @Test
  @DisplayName("Mais recente primeiro, filtrando por tipo e versão, só da aplicação")
  void filtra_e_ordena() {
    reported(applicationId, SdkErrorKind.RENDER_ERROR, "1.0.0", "a", Instant.parse("2026-09-10T10:00:00Z"));
    reported(applicationId, SdkErrorKind.RENDER_ERROR, "1.1.0", "b", Instant.parse("2026-09-11T10:00:00Z"));
    reported(applicationId, SdkErrorKind.NETWORK_ERROR, "1.1.0", "c", Instant.parse("2026-09-12T10:00:00Z"));
    reported(applications.anActiveApplication(), SdkErrorKind.RENDER_ERROR, "1.1.0", "x", Instant.now());

    var all =
        useCase.execute(
            new ListSdkErrorsUseCase.Input(
                applicationId.value(), Optional.empty(), Optional.empty(), 0, 20));
    var renders =
        useCase.execute(
            new ListSdkErrorsUseCase.Input(
                applicationId.value(),
                Optional.of(SdkErrorKind.RENDER_ERROR),
                Optional.of("1.1.0"),
                0,
                20));

    assertThat(all.items()).extracting(SdkErrorReportOutput::message).containsExactly("c", "b", "a");
    assertThat(all.total()).isEqualTo(3);
    assertThat(renders.items()).extracting(SdkErrorReportOutput::message).containsExactly("b");
    assertThat(renders.items().getFirst().kind()).isEqualTo("render_error");
  }
}

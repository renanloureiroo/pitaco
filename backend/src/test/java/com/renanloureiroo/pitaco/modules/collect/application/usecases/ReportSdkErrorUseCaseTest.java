package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkErrorReportRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportSdkErrorUseCase")
class ReportSdkErrorUseCaseTest {

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemorySdkErrorReportRepository reports = new InMemorySdkErrorReportRepository();

  private ReportSdkErrorUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    useCase = new ReportSdkErrorUseCase(applications, reports);
    applicationId = applications.anActiveApplication();
  }

  private ReportSdkErrorUseCase.Input input(ApplicationId owner, String kind) {
    return new ReportSdkErrorUseCase.Input(
        owner.value(),
        Optional.of("1.4.2"),
        Optional.ofNullable(kind),
        Optional.of("falhou em joao@exemplo.com"),
        Map.of("questionType", "matrix", "email", "joao@exemplo.com"),
        Optional.empty());
  }

  @Test
  @DisplayName("Grava o relatório sanitizado, sem dado pessoal no contexto nem na mensagem")
  void grava_sanitizado() {
    useCase.execute(input(applicationId, "render_error"));

    assertThat(reports.findAll())
        .singleElement()
        .satisfies(
            report -> {
              assertThat(report.getKind()).isEqualTo(SdkErrorKind.RENDER_ERROR);
              assertThat(report.sdkVersion()).hasValueSatisfying(
                  version -> assertThat(version.value()).isEqualTo("1.4.2"));
              assertThat(report.getMessage()).isEqualTo("falhou em [email]");
              assertThat(report.getContext().values()).containsOnlyKeys("questionType");
            });
  }

  @Test
  @DisplayName("Tipo desconhecido ou ausente vira unknown")
  void tipo_desconhecido() {
    useCase.execute(input(applicationId, "crash"));
    useCase.execute(input(applicationId, null));

    assertThat(reports.findAll()).allSatisfy(
        report -> assertThat(report.getKind()).isEqualTo(SdkErrorKind.UNKNOWN));
  }

  @Test
  @DisplayName("Aplicação inativa descarta o relatório")
  void aplicacao_inativa() {
    useCase.execute(input(applications.anInactiveApplication(), "render_error"));

    assertThat(reports.findAll()).isEmpty();
  }
}

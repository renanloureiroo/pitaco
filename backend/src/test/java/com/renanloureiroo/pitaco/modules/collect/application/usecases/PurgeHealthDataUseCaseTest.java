package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorContext;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkErrorReportRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkVersionUsageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PurgeHealthDataUseCase")
class PurgeHealthDataUseCaseTest {

  private final InMemorySdkErrorReportRepository reports = new InMemorySdkErrorReportRepository();
  private final InMemorySdkVersionUsageRepository usage = new InMemorySdkVersionUsageRepository();

  private final PurgeHealthDataUseCase useCase =
      new PurgeHealthDataUseCase(reports, usage, Duration.ofDays(90), Duration.ofDays(90));

  private static SdkErrorReport receivedAt(Instant at) {
    return SdkErrorReport.create(
        ApplicationId.generate(),
        Optional.empty(),
        SdkErrorKind.UNKNOWN,
        "x",
        SdkErrorContext.empty(),
        Optional.empty(),
        at);
  }

  @Test
  @DisplayName("Apaga relatório e dia de uso além da retenção, e mantém o resto")
  void apaga_o_vencido() {
    var now = Instant.now();
    var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    var application = ApplicationId.generate();

    reports.with(receivedAt(now.minus(Duration.ofDays(91)))).with(receivedAt(now.minus(Duration.ofDays(1))));
    usage
        .withUsage(application, "1.0.0", today.minusDays(100), 5, now, now)
        .withUsage(application, "1.0.0", today, 5, now, now);

    useCase.execute();

    assertThat(reports.findAll()).hasSize(1);
    assertThat(usage.dailyRows()).isEqualTo(1);
    assertThat(usage.findByApplication(application, today.minusDays(200)).getFirst().requestCount())
        .isEqualTo(10);
  }
}

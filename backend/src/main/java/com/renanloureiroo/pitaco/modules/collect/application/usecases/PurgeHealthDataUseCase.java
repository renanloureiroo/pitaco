package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutInputAndOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;

// Relatório de erro velho não ajuda a investigar nada, e o total diário só serve à janela
// recente. O acumulado por versão fica: é o que diz quando uma versão foi vista pela última vez.
@Slf4j
public class PurgeHealthDataUseCase implements UseCaseWithoutInputAndOutput {

  private final SdkErrorReportRepository reports;
  private final SdkVersionUsageRepository usage;
  private final Duration errorRetention;
  private final Duration dailyUsageRetention;

  public PurgeHealthDataUseCase(
      SdkErrorReportRepository reports,
      SdkVersionUsageRepository usage,
      Duration errorRetention,
      Duration dailyUsageRetention) {
    this.reports = reports;
    this.usage = usage;
    this.errorRetention = errorRetention;
    this.dailyUsageRetention = dailyUsageRetention;
  }

  @Override
  @Transactional
  public void execute() {
    var now = Instant.now();

    var reportsDeleted = reports.deleteReceivedBefore(now.minus(errorRetention));
    var daysDeleted =
        usage.purgeDailyBefore(LocalDate.ofInstant(now.minus(dailyUsageRetention), ZoneOffset.UTC));

    log.info(
        "Dados de saúde expurgados relatorios={} diasDeUso={}", reportsDeleted, daysDeleted);
  }
}

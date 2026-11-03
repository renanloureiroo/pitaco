package com.renanloureiroo.pitaco.modules.collect.infra.health;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.PurgeHealthDataUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A borda deste caso de uso é o agendador, não o HTTP: é aqui que a falha é logada, uma vez.
@Slf4j
@Component
public class HealthDataPurgeJob {

  private final PurgeHealthDataUseCase purge;

  public HealthDataPurgeJob(PurgeHealthDataUseCase purge) {
    this.purge = purge;
  }

  @Scheduled(cron = "${pitaco.health.sdk-errors.purge-cron}", zone = "UTC")
  public void run() {
    try {
      purge.execute();
    } catch (RuntimeException failure) {
      log.warn("Expurgo de dados de saúde falhou motivo={}", failure.getClass().getSimpleName());
    }
  }
}

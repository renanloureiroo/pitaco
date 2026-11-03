package com.renanloureiroo.pitaco.modules.privacy.infra.retention;

import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ApplyRetentionUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A borda deste caso de uso é o agendador: é aqui que a falha é logada, uma vez. Desligar o
// descarte é tirar este bean, não esvaziar a política das aplicações.
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "pitaco.privacy.retention",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RetentionJob {

  private final ApplyRetentionUseCase retention;

  public RetentionJob(ApplyRetentionUseCase retention) {
    this.retention = retention;
  }

  @Scheduled(cron = "${pitaco.privacy.retention.cron}", zone = "UTC")
  public void run() {
    try {
      retention.execute();
    } catch (RuntimeException failure) {
      log.warn("Retenção falhou motivo={}", failure.getClass().getSimpleName());
    }
  }
}

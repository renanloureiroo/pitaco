package com.renanloureiroo.pitaco.modules.privacy.infra.gateways;

import com.renanloureiroo.pitaco.modules.privacy.application.gateways.RetentionSchedule;
import com.renanloureiroo.pitaco.modules.privacy.infra.config.PrivacyProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

// A mesma expressão que agenda o job responde quando ele roda de novo: duas fontes para o mesmo
// horário divergiriam na primeira mudança.
@Component
public class CronRetentionSchedule implements RetentionSchedule {

  private final PrivacyProperties properties;
  private final CronExpression cron;

  public CronRetentionSchedule(PrivacyProperties properties) {
    this.properties = properties;
    this.cron = CronExpression.parse(properties.retention().cron());
  }

  @Override
  public Optional<Instant> nextRunAfter(Instant instant) {
    if (!properties.retention().enabled()) {
      return Optional.empty();
    }
    return Optional.ofNullable(cron.next(instant.atZone(ZoneOffset.UTC)))
        .map(next -> next.toInstant());
  }
}

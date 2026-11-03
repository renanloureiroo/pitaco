package com.renanloureiroo.pitaco.modules.privacy.application.gateways;

import java.time.Instant;
import java.util.Optional;

// Quando a retenção roda de novo. Ausente quando o descarte automático está desligado.
public interface RetentionSchedule {

  Optional<Instant> nextRunAfter(Instant instant);
}

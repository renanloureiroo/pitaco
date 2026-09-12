package com.renanloureiroo.pitaco.modules.privacy.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pitaco.privacy")
public record PrivacyProperties(Retention retention) {

  public record Retention(boolean enabled, String cron, int batchSize) {}
}

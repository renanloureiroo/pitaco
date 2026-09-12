package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pitaco.collect.rate-limit")
public record RateLimitProperties(Limit perKey, Limit perOrigin, Limit sdkErrors, Origin origin) {

  public record Limit(int capacity, Duration window) {}

  public record Origin(String clientIpHeader, List<String> trustedProxies) {}
}

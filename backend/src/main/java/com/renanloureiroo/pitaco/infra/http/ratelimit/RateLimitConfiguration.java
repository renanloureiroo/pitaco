package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

  @Bean
  FixedWindowRateLimiter originRateLimiter(RateLimitProperties properties) {
    var limit = properties.perOrigin();
    return new FixedWindowRateLimiter(limit.capacity(), limit.window());
  }

  @Bean
  OriginResolver originResolver(RateLimitProperties properties) {
    var origin = properties.origin();
    return origin == null
        ? OriginResolver.of(null, List.of())
        : OriginResolver.of(origin.clientIpHeader(), origin.trustedProxies());
  }

  @Bean
  OriginRateLimitInterceptor originRateLimitInterceptor(
      FixedWindowRateLimiter originRateLimiter, OriginResolver originResolver) {
    return new OriginRateLimitInterceptor(originRateLimiter, originResolver);
  }

  @Bean
  ApiKeyRateLimitInterceptor apiKeyRateLimitInterceptor(RateLimitProperties properties) {
    var limit = properties.perKey();
    return new ApiKeyRateLimitInterceptor(
        new FixedWindowRateLimiter(limit.capacity(), limit.window()));
  }

  @Bean
  SdkErrorRateLimitInterceptor sdkErrorRateLimitInterceptor(RateLimitProperties properties) {
    var limit = properties.sdkErrors();
    return new SdkErrorRateLimitInterceptor(
        new FixedWindowRateLimiter(limit.capacity(), limit.window()));
  }
}

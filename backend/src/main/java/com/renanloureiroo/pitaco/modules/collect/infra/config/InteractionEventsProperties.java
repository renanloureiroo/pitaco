package com.renanloureiroo.pitaco.modules.collect.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pitaco.collect.interaction-events")
public record InteractionEventsProperties(int maxPerDisplay, Duration acceptanceWindow) {}

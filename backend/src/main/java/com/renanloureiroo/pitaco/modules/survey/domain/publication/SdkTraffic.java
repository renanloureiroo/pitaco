package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;

public record SdkTraffic(SdkVersion version, long requests) {}

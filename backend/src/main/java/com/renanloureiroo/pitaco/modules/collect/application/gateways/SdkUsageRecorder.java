package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;

// Porta e não repositório: quem implementa pode acumular em memória e descarregar depois. O que
// se promete é a contagem aproximada, nunca uma escrita por consulta no caminho quente.
public interface SdkUsageRecorder {

  void record(ApplicationId applicationId, SdkVersion version, Instant now);
}

package com.renanloureiroo.pitaco.modules.survey.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.SdkTraffic;
import java.time.LocalDate;
import java.util.List;

// O uso de versões do SDK é contado pela coleta; a autoria só lê o tráfego recente para avisar
// antes de publicar algo que a maioria dos apps em uso não sabe desenhar.
public interface SdkTrafficGateway {

  List<SdkTraffic> recentTraffic(ApplicationId applicationId, LocalDate since);
}

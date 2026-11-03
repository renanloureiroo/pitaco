package com.renanloureiroo.pitaco.modules.survey.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.KnownAttribute;
import java.util.Collection;
import java.util.List;

// O catálogo de atributos é alimentado pela coleta; a autoria só o lê, e só pelos nomes que as
// regras da pesquisa usam. Nome ausente da lista é atributo que o app nunca enviou.
public interface AttributeCatalogGateway {

  List<KnownAttribute> knownIn(ApplicationId applicationId, Collection<String> names);
}

package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.AttributeCatalogGateway;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.KnownAttribute;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InMemoryAttributeCatalogGateway implements AttributeCatalogGateway {

  private final Map<ApplicationId, Map<String, KnownAttribute>> catalog = new HashMap<>();

  @Override
  public List<KnownAttribute> knownIn(ApplicationId applicationId, Collection<String> names) {
    var known = catalog.getOrDefault(applicationId, Map.of());
    return names.stream().filter(known::containsKey).map(known::get).toList();
  }

  public InMemoryAttributeCatalogGateway withAttribute(
      ApplicationId applicationId, String name, String... values) {
    return with(applicationId, new KnownAttribute(name, Set.of(values), false));
  }

  public InMemoryAttributeCatalogGateway withSaturatedAttribute(
      ApplicationId applicationId, String name, String... values) {
    return with(applicationId, new KnownAttribute(name, Set.of(values), true));
  }

  private InMemoryAttributeCatalogGateway with(ApplicationId applicationId, KnownAttribute known) {
    catalog.computeIfAbsent(applicationId, key -> new HashMap<>()).put(known.name(), known);
    return this;
  }
}

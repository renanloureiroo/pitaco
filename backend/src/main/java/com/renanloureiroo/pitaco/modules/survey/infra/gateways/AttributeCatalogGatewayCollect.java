package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.AttributeCatalogGateway;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.KnownAttribute;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AttributeCatalogGatewayCollect implements AttributeCatalogGateway {

  private final SurveyAttributeCatalogJpaRepository repository;

  public AttributeCatalogGatewayCollect(SurveyAttributeCatalogJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<KnownAttribute> knownIn(ApplicationId applicationId, Collection<String> names) {
    if (names.isEmpty()) {
      return List.of();
    }

    var values = new LinkedHashMap<String, Set<String>>();
    for (var row : repository.findKnown(applicationId.value(), names)) {
      var attributeValues = values.computeIfAbsent((String) row[0], name -> new HashSet<>());
      if (row[1] != null) {
        attributeValues.add((String) row[1]);
      }
    }

    return values.entrySet().stream()
        .map(
            entry ->
                new KnownAttribute(
                    entry.getKey(),
                    entry.getValue(),
                    entry.getValue().size() >= ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE))
        .toList();
  }
}

package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Regras que não alcançam ninguém, avaliadas contra o que o app já enviou. Ausência de atributo
// falha fechado na elegibilidade, então exigir um atributo nunca visto é exigir o que ninguém
// tem; ABSENT é a única operação que casa com quem não envia nada.
public final class SegmentationReach {

  private SegmentationReach() {}

  public static List<PublicationWarning> warningsFor(
      List<SegmentationRule> rules, Map<String, KnownAttribute> known) {
    var warnings = new ArrayList<PublicationWarning>();

    for (var rule : rules) {
      if (unreachable(rule, known.get(rule.attribute()))) {
        warnings.add(PublicationWarning.noKnownMatch(rule.id(), rule.attribute()));
      }
    }

    rulesByAttribute(rules).forEach(
        (attribute, sameAttribute) -> {
          if (contradictory(sameAttribute)) {
            warnings.add(PublicationWarning.contradictory(attribute));
          }
        });

    return List.copyOf(warnings);
  }

  private static boolean unreachable(SegmentationRule rule, KnownAttribute attribute) {
    if (rule.operation() == RuleOperation.ABSENT) {
      return false;
    }
    if (attribute == null) {
      return true;
    }
    if (rule.operation() != RuleOperation.EQUALS || attribute.saturated()) {
      return false;
    }
    return rule.value().filter(value -> !attribute.values().contains(value)).isPresent();
  }

  // Uma regra por vez não enxerga a outra: exigir dois valores diferentes do mesmo atributo, ou
  // exigir e proibir a presença dele, é uma combinação vazia independentemente do catálogo.
  private static boolean contradictory(List<SegmentationRule> rules) {
    var requiresAbsence = rules.stream().anyMatch(rule -> is(rule, RuleOperation.ABSENT));
    var requiresPresence = rules.stream().anyMatch(rule -> !is(rule, RuleOperation.ABSENT));

    if (requiresAbsence && requiresPresence) {
      return true;
    }

    var required =
        rules.stream()
            .filter(rule -> is(rule, RuleOperation.EQUALS))
            .map(rule -> rule.value().orElseThrow())
            .collect(Collectors.toSet());
    var forbidden =
        rules.stream()
            .filter(rule -> is(rule, RuleOperation.NOT_EQUALS))
            .map(rule -> rule.value().orElseThrow())
            .collect(Collectors.toSet());

    return required.size() > 1 || required.stream().anyMatch(forbidden::contains);
  }

  private static boolean is(SegmentationRule rule, RuleOperation operation) {
    return rule.operation() == operation;
  }

  private static Map<String, List<SegmentationRule>> rulesByAttribute(
      List<SegmentationRule> rules) {
    return rules.stream()
        .collect(
            Collectors.groupingBy(
                SegmentationRule::attribute, LinkedHashMap::new, Collectors.toList()));
  }
}

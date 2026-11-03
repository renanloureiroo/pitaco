package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import java.util.List;
import java.util.function.Predicate;

public final class SegmentationEvaluation {

  private SegmentationEvaluation() {}

  public static boolean satisfies(
      List<SegmentationCriterion> criteria, AttributeSnapshot attributes) {
    return criteria.stream().allMatch(criterion -> satisfies(criterion, attributes));
  }

  // Atributo vazio conta como ausente, e NOT_EQUALS sobre ausente não casa: na dúvida sobre quem
  // é o respondente, a pesquisa não é entregue (falha fechado, FR-016).
  private static boolean satisfies(SegmentationCriterion criterion, AttributeSnapshot attributes) {
    var informed = attributes.valueOf(criterion.attribute()).filter(value -> !value.isBlank());

    return switch (criterion.operation()) {
      case EQUALS -> informed.filter(expected(criterion)).isPresent();
      case NOT_EQUALS -> informed.isPresent() && informed.filter(expected(criterion)).isEmpty();
      case PRESENT -> informed.isPresent();
      case ABSENT -> informed.isEmpty();
    };
  }

  private static Predicate<String> expected(SegmentationCriterion criterion) {
    return value -> criterion.value().map(value::equals).orElse(false);
  }
}

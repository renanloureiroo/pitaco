package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionKey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// As quatro diferenças que tornam duas respostas não somáveis. Enunciado reescrito,
// obrigatoriedade trocada e reordenação não entram: o sistema não julga se um texto mudou de
// sentido, verifica o que consegue enxergar.
public final class ChangeClassification {

  public enum DifferenceKind {
    QUESTION_ADDED,
    QUESTION_REMOVED,
    TYPE_CHANGED,
    OPTIONS_CHANGED
  }

  public record Difference(QuestionKey questionKey, DifferenceKind kind) {}

  private ChangeClassification() {}

  public static List<Difference> between(List<Question> previous, List<Question> current) {

    var before = byKey(previous);
    var after = byKey(current);
    var differences = new ArrayList<Difference>();

    after.forEach(
        (key, question) -> {
          var earlier = before.get(key);
          if (earlier == null) {
            differences.add(new Difference(key, DifferenceKind.QUESTION_ADDED));
            return;
          }
          if (earlier.getType() != question.getType()) {
            differences.add(new Difference(key, DifferenceKind.TYPE_CHANGED));
          }
          if (!valuesOf(earlier).equals(valuesOf(question))) {
            differences.add(new Difference(key, DifferenceKind.OPTIONS_CHANGED));
          }
        });

    before.keySet().stream()
        .filter(key -> !after.containsKey(key))
        .forEach(key -> differences.add(new Difference(key, DifferenceKind.QUESTION_REMOVED)));

    return List.copyOf(differences);
  }

  private static Map<QuestionKey, Question> byKey(List<Question> questions) {
    return questions.stream()
        .collect(
            Collectors.toMap(
                Question::getKey,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
  }

  // Conjunto, não lista: reordenar as alternativas não muda o que se pode responder.
  private static Set<String> valuesOf(Question question) {
    return question.getOptions().stream().map(QuestionOption::value).collect(Collectors.toSet());
  }
}

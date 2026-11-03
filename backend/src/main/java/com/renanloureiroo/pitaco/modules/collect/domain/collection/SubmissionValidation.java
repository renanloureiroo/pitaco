package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Sempre a lista inteira, nunca a primeira falha: quem envia descobre tudo que está errado em uma
// requisição só, no mesmo molde de PublicationImpediment na autoria (D-14, FR-035).
public final class SubmissionValidation {

  private SubmissionValidation() {}

  public static List<SubmissionProblem> check(
      List<DeliverableQuestion> questions, DisplayOutcome outcome, List<AnswerDraft> answers) {

    var byKey =
        questions.stream()
            .collect(
                Collectors.toMap(
                    DeliverableQuestion::key,
                    question -> question,
                    (first, second) -> first,
                    LinkedHashMap::new));

    var problems = new ArrayList<SubmissionProblem>();

    problems.addAll(unknownQuestions(byKey, answers));
    problems.addAll(duplicatedQuestions(answers));
    problems.addAll(requiredMissing(questions, outcome, answers));

    var known = answers.stream().filter(answer -> byKey.containsKey(answer.questionKey())).toList();

    problems.addAll(unconditionalNotApplicable(byKey, known));
    problems.addAll(missingValues(known));
    problems.addAll(typeMismatches(byKey, known));
    problems.addAll(unknownOptions(byKey, known));
    problems.addAll(emptyOptions(known));
    problems.addAll(duplicatedOptions(known));
    problems.addAll(outOfRange(byKey, known));
    problems.addAll(longTexts(byKey, known));

    return List.copyOf(problems);
  }

  private static List<SubmissionProblem> unknownQuestions(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    return answers.stream()
        .map(AnswerDraft::questionKey)
        .filter(key -> !byKey.containsKey(key))
        .distinct()
        .map(key -> SubmissionProblem.of(SubmissionProblem.QUESTION_UNKNOWN, key))
        .toList();
  }

  private static List<SubmissionProblem> duplicatedQuestions(List<AnswerDraft> answers) {
    var seen = new LinkedHashMap<QuestionKey, Integer>();
    answers.forEach(answer -> seen.merge(answer.questionKey(), 1, Integer::sum));

    return seen.entrySet().stream()
        .filter(entry -> entry.getValue() > 1)
        .map(entry -> SubmissionProblem.of(SubmissionProblem.QUESTION_DUPLICATED, entry.getKey()))
        .toList();
  }

  // Só a conclusão exige o obrigatório: a dispensa aceita o parcial como está (FR-037).
  private static List<SubmissionProblem> requiredMissing(
      List<DeliverableQuestion> questions, DisplayOutcome outcome, List<AnswerDraft> answers) {
    if (outcome != DisplayOutcome.COMPLETED) {
      return List.of();
    }

    // Obrigatória pulada pela condição nunca foi feita à pessoa: não aplicável a satisfaz.
    var answered =
        answers.stream()
            .filter(
                answer ->
                    answer.status() == AnswerStatus.ANSWERED
                        || answer.status() == AnswerStatus.NOT_APPLICABLE)
            .map(AnswerDraft::questionKey)
            .collect(Collectors.toSet());

    return questions.stream()
        .filter(DeliverableQuestion::required)
        .filter(question -> !answered.contains(question.key()))
        .map(question -> SubmissionProblem.of(SubmissionProblem.REQUIRED_MISSING, question.key()))
        .toList();
  }

  // Não aplicável é o que a condição pulou. Pergunta sem condição não tem como ter sido pulada por
  // ela, e aceitar o status ali deixaria uma não resposta passar por pergunta que nunca foi feita.
  // Se o SDK avaliou a condição certo não é verificado: só ele percorre a árvore.
  private static List<SubmissionProblem> unconditionalNotApplicable(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    return answers.stream()
        .filter(answer -> answer.status() == AnswerStatus.NOT_APPLICABLE)
        .filter(answer -> !byKey.get(answer.questionKey()).isConditional())
        .map(
            answer ->
                SubmissionProblem.of(
                    SubmissionProblem.NOT_APPLICABLE_UNCONDITIONAL, answer.questionKey()))
        .toList();
  }

  private static List<SubmissionProblem> missingValues(List<AnswerDraft> answers) {
    return answers.stream()
        .filter(answer -> answer.status() == AnswerStatus.ANSWERED && answer.value().isEmpty())
        .map(answer -> SubmissionProblem.of(SubmissionProblem.VALUE_MISSING, answer.questionKey()))
        .toList();
  }

  // Pulada com valor entra aqui pelo mesmo motivo das outras: a forma enviada não é a que a
  // pergunta aceita naquela situação.
  private static List<SubmissionProblem> typeMismatches(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    return answers.stream()
        .filter(answer -> answer.value().isPresent())
        .filter(
            answer ->
                answer.status() != AnswerStatus.ANSWERED
                    || !fits(byKey.get(answer.questionKey()).type(), answer.value().orElseThrow()))
        .map(
            answer ->
                SubmissionProblem.of(SubmissionProblem.VALUE_TYPE_MISMATCH, answer.questionKey()))
        .toList();
  }

  private static boolean fits(QuestionType type, RawAnswerValue value) {
    return switch (type) {
      case FREE_TEXT, SINGLE_CHOICE -> value instanceof RawAnswerValue.RawText;
      case MULTIPLE_CHOICE -> value instanceof RawAnswerValue.RawChoices;
      case RATING, SCALE, NPS -> value instanceof RawAnswerValue.RawNumber;
    };
  }

  private static List<SubmissionProblem> unknownOptions(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    var problems = new ArrayList<SubmissionProblem>();

    for (var answer : answers) {
      var question = byKey.get(answer.questionKey());
      if (!question.type().acceptsOptions() || answer.value().isEmpty()) {
        continue;
      }

      var declared = declaredOptionsOf(question);
      var chosen = chosenOptionsOf(answer.value().orElseThrow());

      if (!chosen.isEmpty() && !declared.containsAll(chosen)) {
        problems.add(SubmissionProblem.of(SubmissionProblem.OPTION_UNKNOWN, answer.questionKey()));
      }
    }

    return List.copyOf(problems);
  }

  private static List<SubmissionProblem> emptyOptions(List<AnswerDraft> answers) {
    return answers.stream()
        .filter(answer -> answer.value().orElse(null) instanceof RawAnswerValue.RawChoices choices
            && choices.values().isEmpty())
        .map(answer -> SubmissionProblem.of(SubmissionProblem.OPTIONS_EMPTY, answer.questionKey()))
        .toList();
  }

  private static List<SubmissionProblem> duplicatedOptions(List<AnswerDraft> answers) {
    return answers.stream()
        .filter(
            answer ->
                answer.value().orElse(null) instanceof RawAnswerValue.RawChoices choices
                    && choices.values().stream().distinct().count() != choices.values().size())
        .map(
            answer ->
                SubmissionProblem.of(SubmissionProblem.OPTIONS_DUPLICATED, answer.questionKey()))
        .toList();
  }

  private static List<SubmissionProblem> outOfRange(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    return answers.stream()
        .filter(
            answer -> {
              var question = byKey.get(answer.questionKey());
              return answer.value().orElse(null) instanceof RawAnswerValue.RawNumber number
                  && question
                      .range()
                      .map(range -> number.value() < range.min() || number.value() > range.max())
                      .orElse(false);
            })
        .map(
            answer ->
                SubmissionProblem.of(SubmissionProblem.VALUE_OUT_OF_RANGE, answer.questionKey()))
        .toList();
  }

  private static List<SubmissionProblem> longTexts(
      Map<QuestionKey, DeliverableQuestion> byKey, List<AnswerDraft> answers) {
    return answers.stream()
        .filter(
            answer ->
                byKey.get(answer.questionKey()).type() == QuestionType.FREE_TEXT
                    && answer.value().orElse(null) instanceof RawAnswerValue.RawText text
                    && text.value() != null
                    && text.value().strip().length() > AnswerText.MAX_LENGTH)
        .map(answer -> SubmissionProblem.of(SubmissionProblem.TEXT_TOO_LONG, answer.questionKey()))
        .toList();
  }

  private static Set<String> declaredOptionsOf(DeliverableQuestion question) {
    return question.options().stream()
        .map(QuestionOption::value)
        .collect(Collectors.toSet());
  }

  private static List<String> chosenOptionsOf(RawAnswerValue value) {
    return switch (value) {
      case RawAnswerValue.RawText text -> List.of(text.value());
      case RawAnswerValue.RawChoices choices -> choices.values();
      case RawAnswerValue.RawNumber ignored -> List.of();
    };
  }
}

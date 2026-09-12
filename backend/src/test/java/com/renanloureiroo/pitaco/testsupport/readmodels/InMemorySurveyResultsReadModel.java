package com.renanloureiroo.pitaco.testsupport.readmodels;

import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.RetainedSummary;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.RetainedCount;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.domain.comparability.QuestionShape;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

// O mesmo contrato do adaptador SQL, sobre listas: cada método reproduz o recorte e a
// agregação que a consulta faz, para que o caso de uso seja testado sem banco.
public class InMemorySurveyResultsReadModel implements SurveyResultsReadModel {

  public record StoredQuestion(SurveyId surveyId, int versionNumber, QuestionDefinition definition) {}

  public record StoredDisplay(
      String id,
      ApplicationId applicationId,
      SurveyId surveyId,
      int versionNumber,
      String storedOutcome,
      Instant openedAt,
      Optional<Instant> closedAt,
      Optional<String> sdkVersion,
      String respondentReference,
      Map<String, String> attributes) {}

  public record StoredAnswer(
      String displayId,
      QuestionKey key,
      String status,
      Optional<String> text,
      Optional<Integer> number,
      List<String> options,
      Instant answeredAt) {}

  private final List<StoredQuestion> questions = new ArrayList<>();
  private final List<StoredDisplay> displays = new ArrayList<>();
  private final List<StoredAnswer> answers = new ArrayList<>();
  private final List<List<String>> answerBatches = new ArrayList<>();

  public InMemorySurveyResultsReadModel withQuestion(
      SurveyId surveyId, int versionNumber, QuestionDefinition definition) {
    questions.add(new StoredQuestion(surveyId, versionNumber, definition));
    return this;
  }

  public QuestionDefinition aQuestion(
      SurveyId surveyId, QuestionType type, int position, List<QuestionOption> options, Optional<ScaleRange> range) {
    var definition =
        new QuestionDefinition(
            QuestionKey.generate(), "Pergunta " + position, type, position, options, range);
    withQuestion(surveyId, 1, definition);
    return definition;
  }

  public StoredDisplay aDisplay(
      ApplicationId applicationId,
      SurveyId surveyId,
      String storedOutcome,
      Instant openedAt,
      Map<String, String> attributes) {
    var display =
        new StoredDisplay(
            UUID.randomUUID().toString(),
            applicationId,
            surveyId,
            1,
            storedOutcome,
            openedAt,
            "STARTED".equals(storedOutcome) ? Optional.empty() : Optional.of(openedAt.plusSeconds(30)),
            Optional.of("1.4.2"),
            "u-" + displays.size(),
            attributes);
    displays.add(display);
    return display;
  }

  public InMemorySurveyResultsReadModel withDisplay(StoredDisplay display) {
    displays.add(display);
    return this;
  }

  public InMemorySurveyResultsReadModel withText(
      StoredDisplay display, QuestionKey key, String text, Instant answeredAt) {
    answers.add(
        new StoredAnswer(
            display.id(), key, "ANSWERED", Optional.of(text), Optional.empty(), List.of(), answeredAt));
    return this;
  }

  public InMemorySurveyResultsReadModel withNumber(StoredDisplay display, QuestionKey key, int number) {
    answers.add(
        new StoredAnswer(
            display.id(),
            key,
            "ANSWERED",
            Optional.empty(),
            Optional.of(number),
            List.of(),
            display.openedAt().plusSeconds(10)));
    return this;
  }

  public InMemorySurveyResultsReadModel withOptions(
      StoredDisplay display, QuestionKey key, String... options) {
    answers.add(
        new StoredAnswer(
            display.id(),
            key,
            "ANSWERED",
            Optional.empty(),
            Optional.empty(),
            List.of(options),
            display.openedAt().plusSeconds(10)));
    return this;
  }

  public InMemorySurveyResultsReadModel withSkipped(StoredDisplay display, QuestionKey key) {
    answers.add(
        new StoredAnswer(
            display.id(),
            key,
            "SKIPPED",
            Optional.empty(),
            Optional.empty(),
            List.of(),
            display.openedAt().plusSeconds(10)));
    return this;
  }

  public List<List<String>> answerBatches() {
    return List.copyOf(answerBatches);
  }

  @Override
  public List<QuestionDefinition> questionsOf(SurveyId surveyId, Optional<Integer> versionNumber) {
    var chosen = new LinkedHashMap<QuestionKey, QuestionDefinition>();
    questions.stream()
        .filter(question -> question.surveyId().equals(surveyId))
        .filter(question -> versionNumber.map(number -> number == question.versionNumber()).orElse(true))
        .sorted(Comparator.comparingInt(StoredQuestion::versionNumber).reversed())
        .forEach(question -> chosen.putIfAbsent(question.definition().key(), question.definition()));
    return List.copyOf(chosen.values());
  }

  @Override
  public DisplayCounts displayCountsOf(ResultsFilter filter, Instant abandonedBefore) {
    var matching = matching(filter).toList();
    return new DisplayCounts(
        matching.size(),
        matching.stream().filter(display -> display.storedOutcome().equals("COMPLETED")).count(),
        matching.stream().filter(display -> display.storedOutcome().equals("DISMISSED")).count(),
        matching.stream()
            .filter(display -> display.storedOutcome().equals("STARTED"))
            .filter(display -> display.openedAt().isBefore(abandonedBefore))
            .count(),
        matching.stream()
            .filter(display -> display.storedOutcome().equals("STARTED"))
            .filter(display -> !display.openedAt().isBefore(abandonedBefore))
            .count(),
        matching.stream()
            .filter(
                display ->
                    answers.stream()
                        .anyMatch(
                            answer ->
                                answer.displayId().equals(display.id())
                                    && answer.status().equals("ANSWERED")))
            .count());
  }

  @Override
  public List<DailyCounts> timelineOf(ResultsFilter filter) {
    var byDay = new TreeMap<LocalDate, long[]>();
    matching(filter)
        .forEach(
            display -> {
              var day = display.openedAt().atZone(ZoneOffset.UTC).toLocalDate();
              var counts = byDay.computeIfAbsent(day, ignored -> new long[2]);
              counts[0]++;
              if (display.storedOutcome().equals("COMPLETED")) {
                counts[1]++;
              }
            });
    return byDay.entrySet().stream()
        .map(entry -> new DailyCounts(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
        .toList();
  }

  @Override
  public List<AnswerCounts> answerCountsOf(ResultsFilter filter) {
    var byKey = new LinkedHashMap<QuestionKey, long[]>();
    matchingAnswers(filter)
        .forEach(
            answer -> {
              var counts = byKey.computeIfAbsent(answer.key(), ignored -> new long[3]);
              switch (answer.status()) {
                case "ANSWERED" -> counts[0]++;
                case "SKIPPED" -> counts[1]++;
                case "NOT_APPLICABLE" -> counts[2]++;
                default -> {}
              }
            });
    return byKey.entrySet().stream()
        .map(
            entry ->
                new AnswerCounts(
                    entry.getKey(),
                    entry.getValue()[0],
                    entry.getValue()[1],
                    entry.getValue()[2]))
        .toList();
  }

  // A forma sai das definições guardadas. Condição não entra no fake: a comparação de condições
  // é coberta pelo teste do domínio e pelo E2E sobre o banco.
  @Override
  public List<QuestionShape> questionShapesOf(SurveyId surveyId) {
    return questions.stream()
        .filter(question -> question.surveyId().equals(surveyId))
        .map(
            question ->
                new QuestionShape(
                    question.definition().key(),
                    question.versionNumber(),
                    question.definition().type(),
                    question.definition().options().stream()
                        .map(QuestionOption::value)
                        .collect(Collectors.toSet()),
                    question.definition().range(),
                    Optional.empty()))
        .toList();
  }

  @Override
  public Set<Integer> displayedVersionsOf(ResultsFilter filter) {
    return matching(filter).map(StoredDisplay::versionNumber).collect(Collectors.toSet());
  }

  public InMemorySurveyResultsReadModel withNotApplicable(StoredDisplay display, QuestionKey key) {
    answers.add(
        new StoredAnswer(
            display.id(),
            key,
            "NOT_APPLICABLE",
            Optional.empty(),
            Optional.empty(),
            List.of(),
            display.openedAt().plusSeconds(10)));
    return this;
  }

  public StoredDisplay aDisplayInVersion(
      ApplicationId applicationId,
      SurveyId surveyId,
      int versionNumber,
      String storedOutcome,
      Instant openedAt) {
    var display =
        new StoredDisplay(
            UUID.randomUUID().toString(),
            applicationId,
            surveyId,
            versionNumber,
            storedOutcome,
            openedAt,
            "STARTED".equals(storedOutcome) ? Optional.empty() : Optional.of(openedAt.plusSeconds(30)),
            Optional.of("1.4.2"),
            "u-" + displays.size(),
            Map.of());
    displays.add(display);
    return display;
  }

  @Override
  public List<OptionCount> optionCountsOf(ResultsFilter filter) {
    var counts = new LinkedHashMap<String, Long>();
    var keys = new LinkedHashMap<String, QuestionKey>();
    matchingAnswers(filter)
        .filter(answer -> answer.status().equals("ANSWERED"))
        .forEach(
            answer ->
                answer
                    .options()
                    .forEach(
                        option -> {
                          var id = answer.key().value() + "|" + option;
                          keys.put(id, answer.key());
                          counts.merge(id, 1L, Long::sum);
                        }));
    return counts.entrySet().stream()
        .map(
            entry ->
                new OptionCount(
                    keys.get(entry.getKey()),
                    entry.getKey().substring(entry.getKey().indexOf('|') + 1),
                    entry.getValue()))
        .toList();
  }

  @Override
  public List<NumericCount> numericCountsOf(ResultsFilter filter) {
    var counts = new LinkedHashMap<QuestionKey, Map<Integer, Long>>();
    matchingAnswers(filter)
        .filter(answer -> answer.status().equals("ANSWERED"))
        .filter(answer -> answer.number().isPresent())
        .forEach(
            answer ->
                counts
                    .computeIfAbsent(answer.key(), ignored -> new TreeMap<>())
                    .merge(answer.number().get(), 1L, Long::sum));
    return counts.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().entrySet().stream()
                    .map(value -> new NumericCount(entry.getKey(), value.getKey(), value.getValue())))
        .toList();
  }

  @Override
  public List<AttributeValueCount> attributeCatalogOf(ApplicationId applicationId, SurveyId surveyId) {
    var counts = new TreeMap<String, Long>();
    displays.stream()
        .filter(display -> display.applicationId().equals(applicationId))
        .filter(display -> display.surveyId().equals(surveyId))
        .forEach(
            display ->
                display.attributes().forEach((name, value) -> counts.merge(name + "|" + value, 1L, Long::sum)));
    return counts.entrySet().stream()
        .map(
            entry ->
                new AttributeValueCount(
                    entry.getKey().substring(0, entry.getKey().indexOf('|')),
                    entry.getKey().substring(entry.getKey().indexOf('|') + 1),
                    entry.getValue()))
        .toList();
  }

  @Override
  public Page<OpenAnswerRow> openAnswersOf(OpenAnswersQuery query) {
    var rows =
        matchingAnswers(query.filter())
            .filter(answer -> answer.status().equals("ANSWERED"))
            .filter(answer -> answer.text().map(text -> !text.isBlank()).orElse(false))
            .filter(
                answer ->
                    query.notBefore().map(cutoff -> !answer.answeredAt().isBefore(cutoff)).orElse(true))
            .filter(
                answer ->
                    query
                        .term()
                        .map(
                            term ->
                                answer
                                    .text()
                                    .get()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(term.toLowerCase(Locale.ROOT)))
                        .orElse(true))
            .sorted(Comparator.comparing(StoredAnswer::answeredAt).reversed())
            .map(answer -> new OpenAnswerRow(answer.displayId(), answer.key(), answer.text().get(), answer.answeredAt()))
            .toList();

    var from = (int) Math.min(query.offset(), rows.size());
    var to = Math.min(from + query.size(), rows.size());
    return new Page<>(rows.subList(from, to), rows.size());
  }

  @Override
  public List<AnswerRow> answersOf(List<String> displayIds) {
    answerBatches.add(List.copyOf(displayIds));
    return answers.stream()
        .filter(answer -> displayIds.contains(answer.displayId()))
        .map(
            answer ->
                new AnswerRow(
                    answer.displayId(),
                    answer.key(),
                    answer.status(),
                    answer.text(),
                    answer.number(),
                    answer.options(),
                    answer.answeredAt()))
        .toList();
  }

  @Override
  public List<String> attributeNamesOf(ResultsFilter filter) {
    return matching(filter)
        .flatMap(display -> display.attributes().keySet().stream())
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  public List<DisplayRow> displaysAfter(ResultsFilter filter, Optional<DisplayCursor> after, int limit) {
    return matching(filter)
        .sorted(Comparator.comparing(StoredDisplay::openedAt).thenComparing(StoredDisplay::id))
        .filter(
            display ->
                after
                    .map(
                        cursor ->
                            display.openedAt().isAfter(cursor.openedAt())
                                || (display.openedAt().equals(cursor.openedAt())
                                    && display.id().compareTo(cursor.id()) > 0))
                    .orElse(true))
        .limit(limit)
        .map(
            display ->
                new DisplayRow(
                    display.id(),
                    display.respondentReference(),
                    display.versionNumber(),
                    display.storedOutcome(),
                    display.sdkVersion(),
                    display.openedAt(),
                    display.closedAt()))
        .toList();
  }

  @Override
  public List<AttributeRow> attributesOf(List<String> displayIds) {
    return displays.stream()
        .filter(display -> displayIds.contains(display.id()))
        .flatMap(
            display ->
                display.attributes().entrySet().stream()
                    .map(entry -> new AttributeRow(display.id(), entry.getKey(), entry.getValue())))
        .toList();
  }

  private Stream<StoredDisplay> matching(ResultsFilter filter) {
    return displays.stream()
        .filter(display -> display.applicationId().equals(filter.applicationId()))
        .filter(display -> display.surveyId().equals(filter.surveyId()))
        .filter(display -> filter.versionNumber().map(number -> number == display.versionNumber()).orElse(true))
        .filter(display -> filter.from().map(from -> !display.openedAt().isBefore(from)).orElse(true))
        .filter(display -> filter.to().map(to -> !display.openedAt().isAfter(to)).orElse(true))
        .filter(
            display ->
                filter
                    .attribute()
                    .map(
                        attribute ->
                            attribute
                                .value()
                                .map(value -> value.equals(display.attributes().get(attribute.name())))
                                .orElseGet(() -> !display.attributes().containsKey(attribute.name())))
                    .orElse(true));
  }

  private Stream<StoredAnswer> matchingAnswers(ResultsFilter filter) {
    var ids = matching(filter).map(StoredDisplay::id).toList();
    return answers.stream().filter(answer -> ids.contains(answer.displayId()));
  }

  private final List<StoredRetained> retained = new ArrayList<>();

  public record StoredRetained(
      SurveyId surveyId, int versionNumber, Instant discardedBefore, long respondingDisplays, List<RetainedCount> counts) {}

  public InMemorySurveyResultsReadModel withRetained(
      SurveyId surveyId,
      int versionNumber,
      Instant discardedBefore,
      long respondingDisplays,
      List<RetainedCount> counts) {
    retained.add(
        new StoredRetained(surveyId, versionNumber, discardedBefore, respondingDisplays, List.copyOf(counts)));
    return this;
  }

  private java.util.stream.Stream<StoredRetained> retainedOf(
      SurveyId surveyId, Optional<Integer> versionNumber) {
    return retained.stream()
        .filter(entry -> entry.surveyId().equals(surveyId))
        .filter(entry -> versionNumber.map(number -> number == entry.versionNumber()).orElse(true));
  }

  @Override
  public List<RetainedCount> retainedCountsOf(SurveyId surveyId, Optional<Integer> versionNumber) {
    var summed = new LinkedHashMap<String, RetainedCount>();
    retainedOf(surveyId, versionNumber)
        .flatMap(entry -> entry.counts().stream())
        .forEach(
            count ->
                summed.merge(
                    count.key().value() + "|" + count.dimension() + "|" + count.value(),
                    count,
                    (a, b) -> new RetainedCount(a.key(), a.dimension(), a.value(), a.count() + b.count())));
    return List.copyOf(summed.values());
  }

  @Override
  public Optional<RetainedSummary> retainedSummaryOf(
      SurveyId surveyId, Optional<Integer> versionNumber) {
    var entries = retainedOf(surveyId, versionNumber).toList();
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new RetainedSummary(
            entries.stream().mapToLong(StoredRetained::respondingDisplays).sum(),
            entries.stream().map(StoredRetained::discardedBefore).max(Comparator.naturalOrder()).orElseThrow()));
  }
}

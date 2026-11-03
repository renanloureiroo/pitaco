package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyResultsReadModelJpa implements SurveyResultsReadModel {

  private static final String OPTION_SEPARATOR = String.valueOf((char) 31);

  private final SurveyResultsJpaRepository repository;

  public SurveyResultsReadModelJpa(SurveyResultsJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<QuestionDefinition> questionsOf(SurveyId surveyId, Optional<Integer> versionNumber) {
    var rows = repository.questions(surveyId.value(), versionNumber.orElse(null));

    // A consulta vem da versão mais recente para a mais antiga: a primeira ocorrência de cada
    // chave é a definição que vale no consolidado.
    var chosen = new LinkedHashMap<QuestionKey, Object[]>();
    for (var row : rows) {
      chosen.putIfAbsent(QuestionKey.of(string(row[1])), row);
    }
    if (chosen.isEmpty()) {
      return List.of();
    }

    var options = optionsOf(chosen.values().stream().map(row -> string(row[0])).toList());

    return chosen.values().stream()
        .map(
            row ->
                new QuestionDefinition(
                    QuestionKey.of(string(row[1])),
                    string(row[2]),
                    QuestionType.valueOf(string(row[3])),
                    integer(row[4]),
                    options.getOrDefault(string(row[0]), List.of()),
                    row[5] == null || row[6] == null
                        ? Optional.empty()
                        : Optional.of(new ScaleRange(integer(row[5]), integer(row[6])))))
        .toList();
  }

  private Map<String, List<QuestionOption>> optionsOf(List<String> questionIds) {
    var byQuestion = new LinkedHashMap<String, List<QuestionOption>>();
    for (var row : repository.questionOptions(questionIds)) {
      byQuestion
          .computeIfAbsent(string(row[0]), id -> new ArrayList<>())
          .add(new QuestionOption(string(row[1]), string(row[2]), integer(row[3])));
    }
    return byQuestion;
  }

  @Override
  public DisplayCounts displayCountsOf(ResultsFilter filter, Instant abandonedBefore) {
    var row =
        repository
            .displayCounts(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter),
            abandonedBefore)
            .get(0);

    return new DisplayCounts(
        count(row[0]), count(row[1]), count(row[2]), count(row[3]), count(row[4]), count(row[5]));
  }

  @Override
  public List<DailyCounts> timelineOf(ResultsFilter filter) {
    return repository
        .timeline(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(row -> new DailyCounts(LocalDate.parse(string(row[0])), count(row[1]), count(row[2])))
        .toList();
  }

  @Override
  public List<AnswerCounts> answerCountsOf(ResultsFilter filter) {
    return repository
        .answerCounts(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(
            row ->
                new AnswerCounts(
                    QuestionKey.of(string(row[0])), count(row[1]), count(row[2]), count(row[3])))
        .toList();
  }

  @Override
  public List<QuestionShape> questionShapesOf(SurveyId surveyId) {
    return repository.questionShapes(surveyId.value()).stream()
        .map(
            row ->
                new QuestionShape(
                    QuestionKey.of(string(row[0])),
                    integer(row[1]),
                    QuestionType.valueOf(string(row[2])),
                    Set.copyOf(split(row[10])),
                    row[3] == null || row[4] == null
                        ? Optional.empty()
                        : Optional.of(new ScaleRange(integer(row[3]), integer(row[4]))),
                    conditionOf(row)))
        .toList();
  }

  // A condição vira uma impressão digital em texto: comparar é só igualdade, e os valores já vêm
  // ordenados da consulta, então a ordem em que foram escritos não conta.
  private static Optional<String> conditionOf(Object[] row) {
    if (row[5] == null || row[6] == null) {
      return Optional.empty();
    }
    return Optional.of(
        String.join(
            "|",
            string(row[5]),
            string(row[6]),
            String.join(",", split(row[9])),
            row[7] == null ? "-" : String.valueOf(integer(row[7])),
            row[8] == null ? "-" : String.valueOf(integer(row[8]))));
  }

  private static List<String> split(Object aggregated) {
    return aggregated == null ? List.of() : List.of(string(aggregated).split(OPTION_SEPARATOR));
  }

  @Override
  public Set<Integer> displayedVersionsOf(ResultsFilter filter) {
    return repository
        .displayedVersions(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(Number::intValue)
        .collect(Collectors.toSet());
  }

  @Override
  public List<OptionCount> optionCountsOf(ResultsFilter filter) {
    return repository
        .optionCounts(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(row -> new OptionCount(QuestionKey.of(string(row[0])), string(row[1]), count(row[2])))
        .toList();
  }

  @Override
  public List<NumericCount> numericCountsOf(ResultsFilter filter) {
    return repository
        .numericCounts(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(row -> new NumericCount(QuestionKey.of(string(row[0])), integer(row[1]), count(row[2])))
        .toList();
  }

  @Override
  public List<AttributeValueCount> attributeCatalogOf(ApplicationId applicationId, SurveyId surveyId) {
    return repository.attributeCatalog(applicationId.value(), surveyId.value()).stream()
        .map(row -> new AttributeValueCount(string(row[0]), string(row[1]), count(row[2])))
        .toList();
  }

  @Override
  public Page<OpenAnswerRow> openAnswersOf(OpenAnswersQuery query) {
    var filter = query.filter();
    var term = query.term().map(SurveyResultsReadModelJpa::likePattern).orElse(null);

    var items =
        repository
            .openAnswers(
                filter.applicationId().value(),
                filter.surveyId().value(),
                filter.versionNumber().orElse(null),
                filter.from().orElse(null),
                filter.to().orElse(null),
                attributeOf(filter),
                attributeValueOf(filter),
                query.notBefore().orElse(null),
                term,
                query.size(),
                query.offset())
            .stream()
            .map(
                row ->
                    new OpenAnswerRow(
                        string(row[0]), QuestionKey.of(string(row[1])), string(row[2]), instant(row[3])))
            .toList();

    var total =
        repository.countOpenAnswers(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter),
            query.notBefore().orElse(null),
            term);

    return new Page<>(items, total);
  }

  // O termo vira padrão do ILIKE aqui, e os curingas que ele traga são escapados: quem busca
  // "100%" quer o texto "100%", não qualquer coisa que comece com 100.
  static String likePattern(String term) {
    var escaped =
        term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  @Override
  public List<AnswerRow> answersOf(List<String> displayIds) {
    if (displayIds.isEmpty()) {
      return List.of();
    }

    return repository.answersOf(displayIds).stream()
        .map(
            row ->
                new AnswerRow(
                    string(row[0]),
                    QuestionKey.of(string(row[1])),
                    string(row[2]),
                    Optional.ofNullable(row[3]).map(Object::toString),
                    Optional.ofNullable(row[4]).map(SurveyResultsReadModelJpa::integer),
                    row[6] == null ? List.of() : List.of(string(row[6]).split(OPTION_SEPARATOR)),
                    instant(row[5])))
        .toList();
  }

  @Override
  public List<String> attributeNamesOf(ResultsFilter filter) {
    return repository.attributeNames(
        filter.applicationId().value(),
        filter.surveyId().value(),
        filter.versionNumber().orElse(null),
        filter.from().orElse(null),
        filter.to().orElse(null),
        attributeOf(filter),
        attributeValueOf(filter));
  }

  @Override
  public List<DisplayRow> displaysAfter(
      ResultsFilter filter, Optional<DisplayCursor> after, int limit) {
    return repository
        .displaysAfter(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter),
            after.map(DisplayCursor::openedAt).orElse(null),
            after.map(DisplayCursor::id).orElse(null),
            limit)
        .stream()
        .map(
            row ->
                new DisplayRow(
                    string(row[0]),
                    string(row[1]),
                    integer(row[2]),
                    string(row[3]),
                    Optional.ofNullable(row[4]).map(Object::toString),
                    instant(row[5]),
                    Optional.ofNullable(row[6]).map(SurveyResultsReadModelJpa::instant)))
        .toList();
  }

  @Override
  public List<AttributeRow> attributesOf(List<String> displayIds) {
    if (displayIds.isEmpty()) {
      return List.of();
    }

    return repository.attributesOf(displayIds).stream()
        .map(row -> new AttributeRow(string(row[0]), string(row[1]), string(row[2])))
        .toList();
  }

  @Override
  public List<RetainedCount> retainedCountsOf(SurveyId surveyId, Optional<Integer> versionNumber) {
    return repository.retainedCounts(surveyId.value(), versionNumber.orElse(null)).stream()
        .map(
            row ->
                new RetainedCount(
                    QuestionKey.of(string(row[0])), string(row[1]), string(row[2]), count(row[3])))
        .toList();
  }

  @Override
  public Optional<RetainedSummary> retainedSummaryOf(
      SurveyId surveyId, Optional<Integer> versionNumber) {
    return repository.retainedSummary(surveyId.value(), versionNumber.orElse(null)).stream()
        .findFirst()
        .filter(row -> row[1] != null)
        .map(row -> new RetainedSummary(count(row[0]), instant(row[1])));
  }

  private static String attributeOf(ResultsFilter filter) {
    return filter.attribute().map(AttributeFilter::name).orElse(null);
  }

  private static String attributeValueOf(ResultsFilter filter) {
    return filter.attribute().flatMap(AttributeFilter::value).orElse(null);
  }

  private static String string(Object value) {
    return (String) value;
  }

  private static long count(Object value) {
    return ((Number) value).longValue();
  }

  private static int integer(Object value) {
    return ((Number) value).intValue();
  }

  // O driver devolve timestamptz como Instant ou como OffsetDateTime/Timestamp conforme o
  // caminho; todos viram Instant aqui.
  private static Instant instant(Object value) {
    return switch (value) {
      case Instant instant -> instant;
      case OffsetDateTime offset -> offset.toInstant();
      case Timestamp timestamp -> timestamp.toInstant();
      default -> throw new IllegalStateException("Instante inesperado: " + value.getClass());
    };
  }
}

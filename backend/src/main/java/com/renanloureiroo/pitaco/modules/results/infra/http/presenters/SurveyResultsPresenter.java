package com.renanloureiroo.pitaco.modules.results.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.results.application.outputs.AttributeCatalogOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.QuestionResultOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ResponseRateOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ResultsFilterOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyResultsOutput;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.AttributeCatalogDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.AttributeValueCountDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.ComparabilityDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.NpsSummaryDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.OptionShareDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.QuestionAggregateDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.QuestionResultDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.ResponseRateDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.ResultsFilterDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.TimelinePointDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.ValueShareDTO;
import java.util.List;
import java.util.Locale;

public final class SurveyResultsPresenter {

  private SurveyResultsPresenter() {}

  public static SurveyResultsResponseDTO present(SurveyResultsOutput output) {
    return new SurveyResultsResponseDTO(
        output.everPublished(),
        present(output.responseRate()),
        output.questions().stream().map(SurveyResultsPresenter::present).toList(),
        output.sampleSize(),
        output.smallSample(),
        present(output.filter()),
        output.attributes().stream().map(SurveyResultsPresenter::present).toList(),
        output
            .nps()
            .map(
                nps ->
                    new NpsSummaryDTO(
                        nps.questionKey().value(),
                        nps.respondents(),
                        nps.promoters(),
                        nps.passives(),
                        nps.detractors(),
                        nps.score().orElse(null)))
            .orElse(null),
        output
            .retention()
            .map(
                retention ->
                    new SurveyResultsResponseDTO.RetentionDTO(
                        retention.snapshotApplied(), retention.discardedBefore(), retention.note()))
            .orElse(null));
  }

  private static ResponseRateDTO present(ResponseRateOutput rate) {
    return new ResponseRateDTO(
        rate.displayed(),
        rate.completed(),
        rate.dismissed(),
        rate.abandoned(),
        rate.inProgress(),
        rate.rate().orElse(null),
        rate.definition(),
        rate.timeline().stream()
            .map(point -> new TimelinePointDTO(point.day(), point.displayed(), point.completed()))
            .toList());
  }

  private static QuestionResultDTO present(QuestionResultOutput question) {
    return new QuestionResultDTO(
        question.key().value(),
        question.statement(),
        question.type().name().toLowerCase(Locale.ROOT),
        question.position(),
        question.answered(),
        question.skipped(),
        question.notApplicable(),
        question.aggregate().map(SurveyResultsPresenter::present).orElse(null),
        question
            .comparability()
            .map(value -> new ComparabilityDTO(value.comparable(), value.versions()))
            .orElse(null));
  }

  private static QuestionAggregateDTO present(QuestionAggregate aggregate) {
    return switch (aggregate) {
      case QuestionAggregate.Choice choice ->
          new QuestionAggregateDTO(
              "choice",
              choice.options().stream()
                  .map(
                      option ->
                          new OptionShareDTO(
                              option.value(), option.label(), option.count(), option.share()))
                  .toList(),
              null,
              null,
              null,
              null,
              null,
              null);
      case QuestionAggregate.Numeric numeric ->
          new QuestionAggregateDTO(
              "numeric",
              null,
              numeric.average(),
              distribution(numeric.distribution()),
              null,
              null,
              null,
              null);
      case QuestionAggregate.Nps nps ->
          new QuestionAggregateDTO(
              "nps",
              null,
              null,
              distribution(nps.distribution()),
              nps.promoters(),
              nps.passives(),
              nps.detractors(),
              nps.score());
      case QuestionAggregate.Text text ->
          new QuestionAggregateDTO("text", null, null, null, null, null, null, null);
    };
  }

  private static List<ValueShareDTO> distribution(List<QuestionAggregate.ValueShare> shares) {
    return shares.stream()
        .map(share -> new ValueShareDTO(share.value(), share.count(), share.share()))
        .toList();
  }

  private static ResultsFilterDTO present(ResultsFilterOutput filter) {
    return new ResultsFilterDTO(
        filter.from().orElse(null),
        filter.to().orElse(null),
        filter.attribute().orElse(null),
        filter.attributeValue().orElse(null),
        filter.attributeAbsent(),
        filter.versionNumber().orElse(null));
  }

  private static AttributeCatalogDTO present(AttributeCatalogOutput catalog) {
    return new AttributeCatalogDTO(
        catalog.name(),
        catalog.values().stream()
            .map(value -> new AttributeValueCountDTO(value.value(), value.count()))
            .toList());
  }
}

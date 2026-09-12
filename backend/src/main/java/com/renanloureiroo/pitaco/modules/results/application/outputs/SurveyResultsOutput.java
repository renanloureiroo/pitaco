package com.renanloureiroo.pitaco.modules.results.application.outputs;

import java.util.List;
import java.util.Optional;

public record SurveyResultsOutput(
    boolean everPublished,
    ResponseRateOutput responseRate,
    List<QuestionResultOutput> questions,
    long sampleSize,
    boolean smallSample,
    ResultsFilterOutput filter,
    List<AttributeCatalogOutput> attributes,
    Optional<NpsSummaryOutput> nps,
    Optional<RetentionOutput> retention) {

  public SurveyResultsOutput {
    nps = nps == null ? Optional.empty() : nps;
    retention = retention == null ? Optional.empty() : retention;
  }

  public SurveyResultsOutput(
      boolean everPublished,
      ResponseRateOutput responseRate,
      List<QuestionResultOutput> questions,
      long sampleSize,
      boolean smallSample,
      ResultsFilterOutput filter,
      List<AttributeCatalogOutput> attributes,
      Optional<NpsSummaryOutput> nps) {
    this(
        everPublished,
        responseRate,
        questions,
        sampleSize,
        smallSample,
        filter,
        attributes,
        nps,
        Optional.empty());
  }

  public SurveyResultsOutput(
      boolean everPublished,
      ResponseRateOutput responseRate,
      List<QuestionResultOutput> questions,
      long sampleSize,
      boolean smallSample,
      ResultsFilterOutput filter,
      List<AttributeCatalogOutput> attributes) {
    this(
        everPublished,
        responseRate,
        questions,
        sampleSize,
        smallSample,
        filter,
        attributes,
        Optional.empty());
  }
}

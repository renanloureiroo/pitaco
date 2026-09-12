package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.RetainedCount;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryResultsSurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyResultsReadModel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyResultsUseCase — agregado congelado pela retenção")
class GetSurveyResultsRetentionTest {

  private static final Instant OPENED = Instant.parse("2026-09-01T10:00:00Z");
  private static final Instant DISCARDED_BEFORE = Instant.parse("2026-08-01T00:00:00Z");

  private InMemoryResultsSurveyScopeGateway surveys;
  private InMemorySurveyResultsReadModel results;
  private GetSurveyResultsUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;
  private InMemorySurveyResultsReadModel.StoredDisplay live;
  private com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition nps;

  @BeforeEach
  void setUp() {
    surveys = new InMemoryResultsSurveyScopeGateway();
    results = new InMemorySurveyResultsReadModel();
    useCase = new GetSurveyResultsUseCase(surveys, results, Duration.ofMinutes(30), 3);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aPublishedSurveyIn(applicationId);
    nps = results.aQuestion(surveyId, QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS));
    live = results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of());
    results.withNumber(live, nps.key(), 3);
  }

  private void retainTwoPromoters(int versionNumber) {
    results.withRetained(
        surveyId,
        versionNumber,
        DISCARDED_BEFORE,
        2,
        List.of(
            new RetainedCount(nps.key(), "STATUS", "ANSWERED", 2),
            new RetainedCount(nps.key(), "STATUS", "SKIPPED", 1),
            new RetainedCount(nps.key(), "NUMBER", "10", 2)));
  }

  private GetSurveyResultsUseCase.Input input(ResultsSelection selection) {
    return new GetSurveyResultsUseCase.Input(applicationId.value(), surveyId.value(), selection);
  }

  @Test
  @DisplayName("Sem nada congelado, o resultado não fala de retenção")
  void sem_retencao() {
    var output = useCase.execute(input(ResultsSelection.none()));

    assertThat(output.retention()).isEmpty();
    assertThat(output.sampleSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("Sem recorte, soma o congelado ao vivo: contagens, distribuição e amostra")
  void soma_o_congelado() {
    retainTwoPromoters(1);

    var output = useCase.execute(input(ResultsSelection.none()));

    var question = output.questions().getFirst();
    assertThat(question.answered()).isEqualTo(3);
    assertThat(question.skipped()).isEqualTo(1);
    var aggregate = (QuestionAggregate.Nps) question.aggregate().orElseThrow();
    assertThat(aggregate.promoters()).isEqualTo(2);
    assertThat(aggregate.detractors()).isEqualTo(1);
    assertThat(output.sampleSize()).isEqualTo(3);
    assertThat(output.smallSample()).isFalse();
    assertThat(output.retention())
        .hasValueSatisfying(
            retention -> {
              assertThat(retention.snapshotApplied()).isTrue();
              assertThat(retention.discardedBefore()).isEqualTo(DISCARDED_BEFORE);
              assertThat(retention.note()).isNotBlank();
            });
  }

  @Test
  @DisplayName("Com recorte de período, não soma e diz por quê")
  void recorte_de_periodo_nao_soma() {
    retainTwoPromoters(1);

    var output =
        useCase.execute(
            input(
                new ResultsSelection(
                    Optional.of(OPENED.minusSeconds(3600)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));

    assertThat(output.questions().getFirst().answered()).isEqualTo(1);
    assertThat(output.sampleSize()).isEqualTo(1);
    assertThat(output.retention())
        .hasValueSatisfying(retention -> assertThat(retention.snapshotApplied()).isFalse());
  }

  @Test
  @DisplayName("Com recorte de versão, soma só o congelado daquela versão")
  void recorte_de_versao() {
    retainTwoPromoters(2);

    var output =
        useCase.execute(
            input(
                new ResultsSelection(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1))));

    assertThat(output.questions().getFirst().answered()).isEqualTo(1);
    assertThat(output.retention()).isEmpty();
  }
}

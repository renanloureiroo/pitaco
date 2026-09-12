package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportRow;
import com.renanloureiroo.pitaco.modules.results.domain.DisplayResolution;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryResultsSurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyResultsReadModel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExportSurveyResultsUseCase")
class ExportSurveyResultsUseCaseTest {

  private static final Instant OPENED = Instant.parse("2026-09-01T10:00:00Z");

  private final InMemoryResultsSurveyScopeGateway surveys = new InMemoryResultsSurveyScopeGateway();
  private final InMemorySurveyResultsReadModel results = new InMemorySurveyResultsReadModel();
  private final ExportSurveyResultsUseCase useCase =
      new ExportSurveyResultsUseCase(surveys, results, Duration.ofMinutes(30));
  private final ApplicationId applicationId = ApplicationId.generate();

  @Test
  @DisplayName("Uma linha por exibição, abandonada e dispensada inclusive, com atributos e respostas")
  void uma_linha_por_exibicao() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId, 5);
    var nps = results.aQuestion(surveyId, QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS));
    var text = results.aQuestion(surveyId, QuestionType.FREE_TEXT, 2, List.of(), Optional.empty());

    var completed = results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of("plano", "pro"));
    results.withNumber(completed, nps.key(), 8).withText(completed, text.key(), "vencido", OPENED);
    results.aDisplay(applicationId, surveyId, "DISMISSED", OPENED.plusSeconds(1), Map.of());
    results.aDisplay(applicationId, surveyId, "STARTED", OPENED.plusSeconds(2), Map.of("cidade", "SP"));

    var output =
        useCase.execute(
            new ExportSurveyResultsUseCase.Input(
                applicationId.value(), surveyId.value(), ResultsSelection.none()));

    assertThat(output.attributeNames()).containsExactly("cidade", "plano");
    assertThat(output.questions()).extracting(question -> question.key()).containsExactly(nps.key(), text.key());

    var rows = new ArrayList<ExportRow>();
    output.rows().forEach(rows::add);

    assertThat(rows).extracting(ExportRow::outcome)
        .containsExactly(DisplayResolution.COMPLETED, DisplayResolution.DISMISSED, DisplayResolution.ABANDONED);
    var first = rows.get(0);
    assertThat(first.attributes()).containsEntry("plano", "pro");
    assertThat(first.answers().get(nps.key()).number()).hasValue(8);
    assertThat(first.answers().get(text.key()).text()).as("texto vencido pela retenção sai vazio").isEmpty();
    assertThat(rows.get(1).answers()).isEmpty();
  }

  @Test
  @DisplayName("Lê em lotes pelo cursor e nunca uma consulta por linha")
  void em_lotes() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    for (var index = 0; index < ExportSurveyResultsUseCase.BATCH_SIZE + 1; index++) {
      results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED.plusSeconds(index), Map.of());
    }

    var output =
        useCase.execute(
            new ExportSurveyResultsUseCase.Input(
                applicationId.value(), surveyId.value(), ResultsSelection.none()));
    var count = new int[1];
    output.rows().forEach(row -> count[0]++);

    assertThat(count[0]).isEqualTo(ExportSurveyResultsUseCase.BATCH_SIZE + 1);
    assertThat(results.answerBatches()).hasSize(2);
  }
}

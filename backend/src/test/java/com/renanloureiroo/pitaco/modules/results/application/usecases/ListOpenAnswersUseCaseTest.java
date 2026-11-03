package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.outputs.OpenAnswerOutput;
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

@DisplayName("ListOpenAnswersUseCase")
class ListOpenAnswersUseCaseTest {

  private static final Instant OPENED = Instant.parse("2026-09-01T10:00:00Z");

  private InMemoryResultsSurveyScopeGateway surveys;
  private InMemorySurveyResultsReadModel results;
  private ListOpenAnswersUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemoryResultsSurveyScopeGateway();
    results = new InMemorySurveyResultsReadModel();
    useCase = new ListOpenAnswersUseCase(surveys, results);
    applicationId = ApplicationId.generate();
  }

  private ListOpenAnswersUseCase.Input input(SurveyId surveyId, Optional<String> term) {
    return new ListOpenAnswersUseCase.Input(
        applicationId.value(), surveyId.value(), ResultsSelection.none(), term, 0, 20);
  }

  @Test
  @DisplayName("Lista da mais recente para a mais antiga, com o contexto legível da mesma exibição")
  void lista_com_contexto() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var nps = results.aQuestion(surveyId, QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS));
    var choice =
        results.aQuestion(
            surveyId,
            QuestionType.SINGLE_CHOICE,
            2,
            List.of(new QuestionOption("Pelo app", "app", 1)),
            Optional.empty());
    var text = results.aQuestion(surveyId, QuestionType.FREE_TEXT, 3, List.of(), Optional.empty());

    var first = results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of());
    var second = results.aDisplay(applicationId, surveyId, "DISMISSED", OPENED, Map.of());
    results
        .withNumber(first, nps.key(), 9)
        .withOptions(first, choice.key(), "app")
        .withText(first, text.key(), "Achei confuso", OPENED.plusSeconds(20))
        .withText(second, text.key(), "Muito bom", OPENED.plusSeconds(40));

    var output = useCase.execute(input(surveyId, Optional.empty()));

    assertThat(output.total()).isEqualTo(2);
    assertThat(output.items()).extracting(OpenAnswerOutput::text).containsExactly("Muito bom", "Achei confuso");

    var withContext = output.items().get(1);
    assertThat(withContext.statement()).isEqualTo(text.statement());
    assertThat(withContext.context())
        .extracting(OpenAnswerOutput.AnswerContext::value)
        .containsExactlyInAnyOrder("9", "Pelo app");
    assertThat(output.items().get(0).context()).isEmpty();
  }

  @Test
  @DisplayName("Busca pelo termo, descarta texto só com espaço e o texto vencido pela retenção")
  void busca_e_descartes() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId, 30);
    var text = results.aQuestion(surveyId, QuestionType.FREE_TEXT, 1, List.of(), Optional.empty());
    var display = results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of());
    var now = Instant.now();
    results
        .withText(display, text.key(), "   ", now.minus(Duration.ofDays(1)))
        .withText(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), text.key(), "Achei CONFUSO demais", now.minus(Duration.ofDays(2)))
        .withText(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), text.key(), "Ficou ótimo", now.minus(Duration.ofDays(3)))
        .withText(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), text.key(), "confuso e vencido", now.minus(Duration.ofDays(40)));

    var all = useCase.execute(input(surveyId, Optional.empty()));
    var searched = useCase.execute(input(surveyId, Optional.of("confuso")));

    assertThat(all.items()).extracting(OpenAnswerOutput::text).containsExactly("Achei CONFUSO demais", "Ficou ótimo");
    assertThat(searched.items()).extracting(OpenAnswerOutput::text).containsExactly("Achei CONFUSO demais");
  }

  @Test
  @DisplayName("O contexto da página inteira vem de uma consulta só")
  void contexto_em_uma_consulta() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var text = results.aQuestion(surveyId, QuestionType.FREE_TEXT, 1, List.of(), Optional.empty());
    for (var index = 0; index < 5; index++) {
      results.withText(
          results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()),
          text.key(),
          "resposta " + index,
          OPENED.plusSeconds(index));
    }

    useCase.execute(input(surveyId, Optional.empty()));

    assertThat(results.answerBatches()).hasSize(1);
    assertThat(results.answerBatches().get(0)).hasSize(5);
  }
}

package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.results.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.results.application.outputs.QuestionResultOutput;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryResultsSurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyResultsReadModel;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyResultsUseCase")
class GetSurveyResultsUseCaseTest {

  private static final Duration TIMEOUT = Duration.ofMinutes(30);
  private static final int THRESHOLD = 3;
  private static final Instant RECENT = Instant.now().minusSeconds(60);
  private static final Instant OLD = Instant.parse("2026-09-01T10:00:00Z");

  private InMemoryResultsSurveyScopeGateway surveys;
  private InMemorySurveyResultsReadModel results;
  private GetSurveyResultsUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;

  @BeforeEach
  void setUp() {
    surveys = new InMemoryResultsSurveyScopeGateway();
    results = new InMemorySurveyResultsReadModel();
    useCase = new GetSurveyResultsUseCase(surveys, results, TIMEOUT, THRESHOLD);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aPublishedSurveyIn(applicationId);
  }

  private GetSurveyResultsUseCase.Input input(ResultsSelection selection) {
    return new GetSurveyResultsUseCase.Input(applicationId.value(), surveyId.value(), selection);
  }

  @Test
  @DisplayName("Conta exibidas, concluídas, dispensadas, abandonadas e em andamento, e a taxa sobre todas")
  void taxa_de_resposta() {
    results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of());
    results.aDisplay(applicationId, surveyId, "COMPLETED", OLD.plusSeconds(1), Map.of());
    results.aDisplay(applicationId, surveyId, "DISMISSED", OLD, Map.of());
    results.aDisplay(applicationId, surveyId, "STARTED", OLD, Map.of());
    results.aDisplay(applicationId, surveyId, "STARTED", RECENT, Map.of());

    var output = useCase.execute(input(ResultsSelection.none()));

    var rate = output.responseRate();
    assertThat(rate.displayed()).isEqualTo(5);
    assertThat(rate.completed()).isEqualTo(2);
    assertThat(rate.dismissed()).isEqualTo(1);
    assertThat(rate.abandoned()).isEqualTo(1);
    assertThat(rate.inProgress()).isEqualTo(1);
    assertThat(rate.rate()).hasValue(0.4);
    assertThat(rate.definition()).isNotBlank();
    assertThat(rate.timeline())
        .anySatisfy(
            point -> {
              assertThat(point.day()).isEqualTo(LocalDate.of(2026, 9, 1));
              assertThat(point.displayed()).isEqualTo(4);
              assertThat(point.completed()).isEqualTo(2);
            });
    assertThat(output.everPublished()).isTrue();
  }

  @Test
  @DisplayName("Pergunta sem resposta vem sem agregado; a com resposta vem agregada na forma do tipo")
  void agregado_por_pergunta() {
    var nps = results.aQuestion(surveyId, QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS));
    var choice =
        results.aQuestion(
            surveyId,
            QuestionType.SINGLE_CHOICE,
            2,
            List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
            Optional.empty());
    var text = results.aQuestion(surveyId, QuestionType.FREE_TEXT, 3, List.of(), Optional.empty());

    var first = results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of());
    var second = results.aDisplay(applicationId, surveyId, "DISMISSED", OLD, Map.of());
    results.withNumber(first, nps.key(), 10).withOptions(first, choice.key(), "yes");
    results.withNumber(second, nps.key(), 2).withSkipped(second, choice.key());

    var output = useCase.execute(input(ResultsSelection.none()));

    assertThat(output.questions()).extracting(QuestionResultOutput::position).containsExactly(1, 2, 3);

    var npsResult = output.questions().get(0);
    assertThat(npsResult.answered()).isEqualTo(2);
    var aggregate = (QuestionAggregate.Nps) npsResult.aggregate().orElseThrow();
    assertThat(aggregate.promoters()).isEqualTo(1);
    assertThat(aggregate.detractors()).isEqualTo(1);
    assertThat(aggregate.score()).isZero();

    var choiceResult = output.questions().get(1);
    assertThat(choiceResult.answered()).isEqualTo(1);
    assertThat(choiceResult.skipped()).isEqualTo(1);
    assertThat(((QuestionAggregate.Choice) choiceResult.aggregate().orElseThrow()).options())
        .extracting(QuestionAggregate.OptionShare::count)
        .containsExactly(1L, 0L);

    var textResult = output.questions().get(2);
    assertThat(textResult.answered()).isZero();
    assertThat(textResult.aggregate()).isEmpty();
    assertThat(textResult.key()).isEqualTo(text.key());
  }

  @Test
  @DisplayName("Amostra pequena é sinalizada pelo número de exibições com alguma resposta")
  void amostra_pequena() {
    var question = results.aQuestion(surveyId, QuestionType.RATING, 1, List.of(), Optional.of(new ScaleRange(1, 5)));
    for (var index = 0; index < THRESHOLD; index++) {
      results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of()), question.key(), 4);
    }
    results.aDisplay(applicationId, surveyId, "DISMISSED", OLD, Map.of());

    var output = useCase.execute(input(ResultsSelection.none()));

    assertThat(output.sampleSize()).isEqualTo(THRESHOLD);
    assertThat(output.smallSample()).isFalse();

    results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of()), question.key(), 1);
    var bigger = useCase.execute(input(new ResultsSelection(Optional.of(OLD.plusSeconds(1)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())));

    assertThat(bigger.sampleSize()).isZero();
    assertThat(bigger.smallSample()).isTrue();
  }

  @Test
  @DisplayName("Recorte por atributo restringe os números, e ausência é um recorte próprio")
  void recorte_por_atributo() {
    results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of("plano", "pro"));
    results.aDisplay(applicationId, surveyId, "COMPLETED", OLD, Map.of("plano", "free"));
    results.aDisplay(applicationId, surveyId, "DISMISSED", OLD, Map.of());

    var pro =
        useCase.execute(
            input(
                new ResultsSelection(
                    Optional.empty(), Optional.empty(), Optional.of("plano"), Optional.of("pro"), Optional.empty())));
    var absent =
        useCase.execute(
            input(
                new ResultsSelection(
                    Optional.empty(), Optional.empty(), Optional.of("plano"), Optional.empty(), Optional.empty())));

    assertThat(pro.responseRate().displayed()).isEqualTo(1);
    assertThat(pro.filter().attributeAbsent()).isFalse();
    assertThat(absent.responseRate().displayed()).isEqualTo(1);
    assertThat(absent.responseRate().dismissed()).isEqualTo(1);
    assertThat(absent.filter().attributeAbsent()).isTrue();
    assertThat(pro.attributes()).singleElement().satisfies(catalog -> {
      assertThat(catalog.name()).isEqualTo("plano");
      assertThat(catalog.values()).hasSize(2);
    });
  }

  @Test
  @DisplayName("Pesquisa nunca publicada devolve estado vazio sem consultar o banco")
  void nunca_publicada() {
    var draft = surveys.aDraftSurveyIn(applicationId);

    var output =
        useCase.execute(
            new GetSurveyResultsUseCase.Input(applicationId.value(), draft.value(), ResultsSelection.none()));

    assertThat(output.everPublished()).isFalse();
    assertThat(output.responseRate().displayed()).isZero();
    assertThat(output.questions()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa inexistente, malformada ou de outra aplicação é o mesmo 404")
  void pesquisa_fora_do_escopo() {
    var other = surveys.aPublishedSurveyIn(ApplicationId.generate());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyResultsUseCase.Input(
                        applicationId.value(), UUID.randomUUID().toString(), ResultsSelection.none())))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyResultsUseCase.Input(applicationId.value(), other.value(), ResultsSelection.none())))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyResultsUseCase.Input(applicationId.value(), "nao-e-id", ResultsSelection.none())))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyResultsUseCase.Input("nao-e-id", surveyId.value(), ResultsSelection.none())))
        .isInstanceOf(ApplicationNotFound.class);
  }
}

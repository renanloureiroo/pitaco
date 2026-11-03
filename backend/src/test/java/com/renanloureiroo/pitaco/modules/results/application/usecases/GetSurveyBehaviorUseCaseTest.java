package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.results.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.MetricDefinitionOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.QuestionBehaviorOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.ViaCountOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionAbandonment;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionActiveTime;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionFunnel;
import com.renanloureiroo.pitaco.modules.results.domain.behavior.BehaviorMetric;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryResultsSurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyBehaviorReadModel;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyResultsReadModel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyBehaviorUseCase")
class GetSurveyBehaviorUseCaseTest {

  private static final Duration TIMEOUT = Duration.ofMinutes(30);

  private InMemoryResultsSurveyScopeGateway surveys;
  private InMemorySurveyResultsReadModel results;
  private InMemorySurveyBehaviorReadModel behavior;
  private GetSurveyBehaviorUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;

  @BeforeEach
  void setUp() {
    surveys = new InMemoryResultsSurveyScopeGateway();
    results = new InMemorySurveyResultsReadModel();
    behavior = new InMemorySurveyBehaviorReadModel();
    useCase = new GetSurveyBehaviorUseCase(surveys, results, behavior, TIMEOUT);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aPublishedSurveyIn(applicationId);
  }

  private GetSurveyBehaviorUseCase.Input input(ResultsSelection selection) {
    return new GetSurveyBehaviorUseCase.Input(applicationId.value(), surveyId.value(), selection);
  }

  @Test
  @DisplayName("Monta o funil, o tempo e as taxas por pergunta, na ordem da pesquisa, com zeros onde não há evento")
  void por_pergunta() {
    var second = results.aQuestion(surveyId, QuestionType.SINGLE_CHOICE, 2, List.of(), Optional.empty());
    var first = results.aQuestion(surveyId, QuestionType.NPS, 1, List.of(), Optional.empty());
    behavior
        .withTotals(5, 4)
        .withFunnel(new QuestionFunnel(first.key(), 4, 1, 2, 0, 2, 1, 0, 0))
        .withAbandonment(new QuestionAbandonment(first.key(), 2))
        .withActiveTime(new QuestionActiveTime(first.key(), 3, 1000.4, 4999.6));

    var output = useCase.execute(input(ResultsSelection.none()));

    assertThat(output.everPublished()).isTrue();
    assertThat(output.displayed()).isEqualTo(5);
    assertThat(output.instrumented()).isEqualTo(4);
    assertThat(output.questions()).extracting(QuestionBehaviorOutput::position).containsExactly(1, 2);

    var nps = output.questions().get(0);
    assertThat(nps.viewed()).isEqualTo(4);
    assertThat(nps.answered()).isEqualTo(2);
    assertThat(nps.abandoned()).isEqualTo(2);
    assertThat(nps.revisitRate()).contains(0.25);
    assertThat(nps.answerChangeRate()).contains(0.5);
    assertThat(nps.activeTime().samples()).isEqualTo(3);
    assertThat(nps.activeTime().medianMs()).contains(1000L);
    assertThat(nps.activeTime().p90Ms()).contains(5000L);

    var untouched = output.questions().get(1);
    assertThat(untouched.key()).isEqualTo(second.key());
    assertThat(untouched.viewed()).isZero();
    assertThat(untouched.revisitRate()).isEmpty();
    assertThat(untouched.answerChangeRate()).isEmpty();
    assertThat(untouched.activeTime().medianMs()).isEmpty();
  }

  @Test
  @DisplayName("As seis vias aparecem sempre; via ausente ou fora do catálogo conta como não informada")
  void dispensas() {
    behavior
        .withDismissals(Optional.of("swipe"), 3)
        .withDismissals(Optional.of("close_button"), 1)
        .withDismissals(Optional.of("teleport"), 1)
        .withDismissals(Optional.empty(), 1);

    var dismissals = useCase.execute(input(ResultsSelection.none())).dismissals();

    assertThat(dismissals.total()).isEqualTo(6);
    assertThat(dismissals.unspecified()).isEqualTo(2);
    assertThat(dismissals.byVia())
        .extracting(ViaCountOutput::via, ViaCountOutput::count)
        .containsExactly(
            Tuple.tuple("close_button", 1L),
            Tuple.tuple("swipe", 3L),
            Tuple.tuple("backdrop", 0L),
            Tuple.tuple("hardware_back", 0L),
            Tuple.tuple("navigation", 0L),
            Tuple.tuple("programmatic", 0L));
    assertThat(dismissals.byVia().get(1).share()).contains(0.5);
  }

  @Test
  @DisplayName("Toda métrica vem com a definição, e o recorte chega ao modelo de leitura")
  void definicoes_e_recorte() {
    var selection =
        new ResultsSelection(
            Optional.of(Instant.parse("2026-09-01T00:00:00Z")), Optional.empty(),
            Optional.of("plano"), Optional.of("pro"), Optional.of(2));
    var before = Instant.now().minus(TIMEOUT);

    var output = useCase.execute(input(selection));

    assertThat(output.definitions())
        .extracting(MetricDefinitionOutput::metric)
        .containsExactly(java.util.Arrays.stream(BehaviorMetric.values()).map(BehaviorMetric::key).toArray(String[]::new));
    assertThat(output.filter().versionNumber()).contains(2);
    assertThat(behavior.filters()).singleElement().satisfies(filter -> {
      assertThat(filter.versionNumber()).contains(2);
      assertThat(filter.attribute()).isPresent();
    });
    assertThat(behavior.lastAbandonedBefore()).hasValueSatisfying(at -> assertThat(at).isAfterOrEqualTo(before));
  }

  @Test
  @DisplayName("Pesquisa que nunca publicou devolve vazio sem consultar os eventos")
  void rascunho() {
    var draft = surveys.aDraftSurveyIn(applicationId);

    var output =
        useCase.execute(new GetSurveyBehaviorUseCase.Input(applicationId.value(), draft.value(), ResultsSelection.none()));

    assertThat(output.everPublished()).isFalse();
    assertThat(output.questions()).isEmpty();
    assertThat(output.dismissals().byVia()).hasSize(6);
    assertThat(output.definitions()).isNotEmpty();
    assertThat(behavior.filters()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação e identificador malformado são 404")
  void escopo() {
    var other = ApplicationId.generate();

    assertThatThrownBy(
            () -> useCase.execute(new GetSurveyBehaviorUseCase.Input(other.value(), surveyId.value(), ResultsSelection.none())))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(
            () -> useCase.execute(new GetSurveyBehaviorUseCase.Input("nao-e-id", surveyId.value(), ResultsSelection.none())))
        .isInstanceOf(ApplicationNotFound.class);
    assertThat(behavior.filters()).isEmpty();
  }
}
